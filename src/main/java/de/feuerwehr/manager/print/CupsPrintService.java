package de.feuerwehr.manager.print;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class CupsPrintService {

    private static final Pattern PRINTER_LINE =
            Pattern.compile("^printer\\s+(.+?)\\s+is\\s+", Pattern.CASE_INSENSITIVE);
    private static final Pattern DEVICE_LINE = Pattern.compile("^device for (.+?):\\s+", Pattern.CASE_INSENSITIVE);
    private static final Pattern ACCEPTING_LINE =
            Pattern.compile("^(.+)\\s+accepting requests\\s+", Pattern.CASE_INSENSITIVE);

    public boolean isCupsClientAvailable() {
        return findExecutable("lpstat").isPresent();
    }

    public CupsListResult listPrintersDetailed(String cupsServer) {
        Optional<String> lpstat = findExecutable("lpstat");
        if (lpstat.isEmpty()) {
            return CupsListResult.failure("lpstat nicht gefunden — App-Image neu bauen (cups-client).");
        }
        if (cupsServer == null || cupsServer.isBlank()) {
            return CupsListResult.failure(
                    "Kein CUPS-Server konfiguriert. In .env: CUPS_SERVER=print:<Passwort>@ffm_cups:631 — danach App-Container neu starten.");
        }
        String executable = lpstat.get();
        StringBuilder attempts = new StringBuilder();
        for (String candidate : serverCandidates(cupsServer)) {
            CupsListResult attempt = listPrintersOnce(executable, candidate);
            if (!attempt.printers().isEmpty()) {
                return attempt;
            }
            if (attempts.length() > 0) {
                attempts.append(" | ");
            }
            attempts.append(maskCupsServer(candidate));
            if (attempt.error() != null && !attempt.error().isBlank()) {
                attempts.append(": ").append(attempt.error());
            }
        }
        return CupsListResult.failure(
                "CUPS nicht erreichbar oder keine Warteschlangen. Versucht: " + attempts);
    }

    private CupsListResult listPrintersOnce(String executable, String cupsServer) {
        Map<String, CupsPrinterOption> seen = new LinkedHashMap<>();
        StringBuilder outputLog = new StringBuilder();
        boolean connectionProblem = false;
        for (String arg : List.of("-t", "-p")) {
            CommandResult result = runCommandDetailed(executable, cupsServer, arg);
            for (String line : result.output()) {
                if (outputLog.length() > 0) {
                    outputLog.append(' ');
                }
                outputLog.append(line.trim());
                if (isConnectionProblem(line)) {
                    connectionProblem = true;
                }
                String name = parsePrinterName(line.trim());
                if (name != null && !name.isBlank() && !seen.containsKey(name)) {
                    seen.put(name, new CupsPrinterOption(name, name));
                }
            }
            if (result.exitCode() != 0) {
                connectionProblem = true;
            }
        }
        List<CupsPrinterOption> printers = new ArrayList<>(seen.values());
        if (!printers.isEmpty()) {
            return new CupsListResult(printers, null);
        }
        if (connectionProblem || !outputLog.isEmpty()) {
            String detail = outputLog.isEmpty() ? "keine Antwort" : outputLog.toString();
            return CupsListResult.failure(detail);
        }
        return CupsListResult.failure("keine Drucker");
    }

    private static boolean isConnectionProblem(String line) {
        String lower = line.toLowerCase();
        return lower.contains("scheduler is not running")
                || lower.contains("no such file or directory")
                || lower.contains("unable to connect")
                || lower.contains("unauthorized")
                || lower.contains("forbidden")
                || lower.contains("connection refused");
    }

    private static List<String> serverCandidates(String cupsServer) {
        List<String> candidates = new ArrayList<>();
        candidates.add(cupsServer.trim());
        parseCupsServer(cupsServer).ifPresent(target -> {
            for (String host : List.of("ffm_cups", "cups", "host.docker.internal")) {
                if (!host.equalsIgnoreCase(target.host())) {
                    candidates.add(buildCupsServer(target.username(), target.password(), host, target.port()));
                }
            }
        });
        return candidates.stream().distinct().toList();
    }

    private static String buildCupsServer(String username, String password, String host, int port) {
        String auth = "";
        if (username != null && !username.isBlank()) {
            auth = username;
            if (password != null && !password.isBlank()) {
                auth = auth + ":" + password;
            }
            auth = auth + "@";
        }
        return auth + host + ":" + port;
    }

    public String resolveWorkingCupsServer(String cupsServer) {
        if (cupsServer == null || cupsServer.isBlank()) {
            return cupsServer;
        }
        Optional<String> lpstat = findExecutable("lpstat");
        if (lpstat.isEmpty()) {
            return cupsServer;
        }
        for (String candidate : serverCandidates(cupsServer)) {
            CommandResult result = runCommandDetailed(lpstat.get(), candidate, "-r");
            if (result.exitCode() == 0
                    && result.output().stream().anyMatch(l -> l.toLowerCase().contains("scheduler is running"))) {
                return candidate;
            }
        }
        return cupsServer;
    }

    public List<CupsPrinterOption> listPrinters(String cupsServer) {
        return listPrintersDetailed(cupsServer).printers();
    }

    private static String maskCupsServer(String cupsServer) {
        int at = cupsServer.indexOf('@');
        if (at > 0) {
            return "print:***" + cupsServer.substring(at);
        }
        return cupsServer;
    }

    public CupsPrintResult printPdf(byte[] pdfContent, String printerName, String cupsServer, boolean usePostscript) {
        if (printerName == null || printerName.isBlank()) {
            return CupsPrintResult.failure("Kein CUPS-Drucker ausgewählt.");
        }
        if (pdfContent == null || pdfContent.length < 100 || !startsWithPdfHeader(pdfContent)) {
            return CupsPrintResult.failure("PDF-Inhalt ungültig.");
        }
        Optional<String> lp = findExecutable("lp");
        if (lp.isEmpty()) {
            return CupsPrintResult.failure("lp-Befehl nicht gefunden. CUPS-Client muss installiert sein.");
        }

        Path fileToPrint = null;
        Path tempPdf = null;
        Path tempPs = null;
        try {
            tempPdf = Files.createTempFile("ffm-print-", ".pdf");
            Files.write(tempPdf, pdfContent);
            fileToPrint = tempPdf;

            if (usePostscript) {
                tempPs = Files.createTempFile("ffm-print-", ".ps");
                if (convertPdfToPostScript(tempPdf, tempPs)) {
                    Files.deleteIfExists(tempPdf);
                    tempPdf = null;
                    fileToPrint = tempPs;
                } else {
                    Files.deleteIfExists(tempPs);
                    tempPs = null;
                }
            }

            runCommandDetailed(lp.get(), resolveWorkingCupsServer(cupsServer), "-d", printerName.trim(), fileToPrint.toString());
            return CupsPrintResult.success("Druckauftrag an CUPS-Drucker gesendet.");
        } catch (IOException e) {
            log.warn("CUPS-Druck fehlgeschlagen: {}", e.getMessage());
            return CupsPrintResult.failure("CUPS-Druck fehlgeschlagen: " + e.getMessage());
        } finally {
            deleteQuietly(tempPdf);
            deleteQuietly(tempPs);
            if (fileToPrint != null && fileToPrint != tempPdf && fileToPrint != tempPs) {
                deleteQuietly(fileToPrint);
            }
        }
    }

    private static boolean convertPdfToPostScript(Path pdf, Path ps) {
        Optional<String> pdftops = findExecutable("pdftops");
        if (pdftops.isPresent()) {
            List<String> out = runCommand(pdftops.get(), null, pdf.toString(), ps.toString());
            if (Files.exists(ps) && fileSize(ps) > 50) {
                return true;
            }
            if (!out.isEmpty()) {
                log.debug("pdftops: {}", String.join(" ", out));
            }
        }
        Optional<String> gs = findExecutable("gs");
        if (gs.isPresent()) {
            List<String> out = runCommand(
                    gs.get(),
                    null,
                    "-sDEVICE=ps2write",
                    "-dNOPAUSE",
                    "-dBATCH",
                    "-sOutputFile=" + ps.toString(),
                    pdf.toString());
            if (Files.exists(ps) && fileSize(ps) > 50) {
                return true;
            }
            if (!out.isEmpty()) {
                log.debug("ghostscript: {}", String.join(" ", out));
            }
        }
        return false;
    }

    private static String parsePrinterName(String line) {
        Matcher m = PRINTER_LINE.matcher(line);
        if (m.find()) {
            return m.group(1).trim();
        }
        m = DEVICE_LINE.matcher(line);
        if (m.find()) {
            return m.group(1).trim();
        }
        m = ACCEPTING_LINE.matcher(line);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }

    private static List<String> runCommand(String executable, String cupsServer, String... args) {
        return runCommandDetailed(executable, cupsServer, args).output();
    }

    private static CommandResult runCommandDetailed(String executable, String cupsServer, String... args) {
        try {
            List<String> command = buildCupsCommand(executable, cupsServer, args);
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            if (cupsServer != null && !cupsServer.isBlank()) {
                pb.environment().put("CUPS_SERVER", cupsServer.trim());
            }
            Process process = pb.start();
            List<String> lines = process.inputReader().lines().toList();
            int exit = process.waitFor();
            if (exit != 0 && !lines.isEmpty()) {
                String err = String.join(" ", lines);
                String baseName = Path.of(executable).getFileName().toString();
                if ("lp".equals(baseName)) {
                    throw new IOException(err.isBlank() ? "lp exit " + exit : err);
                }
                log.debug("Befehl {} beendet mit {}: {}", command, exit, err);
            }
            return new CommandResult(exit, lines);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Druckbefehl unterbrochen.", e);
        } catch (IOException e) {
            throw new IllegalStateException("Druckbefehl fehlgeschlagen: " + e.getMessage(), e);
        }
    }

    private static List<String> buildCupsCommand(String executable, String cupsServer, String... args) {
        List<String> command = new ArrayList<>();
        command.add(executable);
        parseCupsServer(cupsServer).ifPresent(target -> {
            command.add("-h");
            command.add(target.hostPort());
            String userAuth = target.userAuth();
            if (userAuth != null && !userAuth.isBlank()) {
                command.add("-U");
                command.add(userAuth);
            }
        });
        command.addAll(List.of(args));
        return command;
    }

    private static Optional<CupsTarget> parseCupsServer(String cupsServer) {
        if (cupsServer == null || cupsServer.isBlank()) {
            return Optional.empty();
        }
        String remainder = cupsServer.trim();
        String username = null;
        String password = null;
        int at = remainder.lastIndexOf('@');
        if (at > 0) {
            String auth = remainder.substring(0, at);
            remainder = remainder.substring(at + 1);
            int colon = auth.indexOf(':');
            if (colon >= 0) {
                username = auth.substring(0, colon);
                password = auth.substring(colon + 1);
            } else {
                username = auth;
            }
        }
        int port = 631;
        String host = remainder;
        int colon = remainder.lastIndexOf(':');
        if (colon > 0 && !remainder.startsWith("[")) {
            host = remainder.substring(0, colon);
            try {
                port = Integer.parseInt(remainder.substring(colon + 1));
            } catch (NumberFormatException ignored) {
                host = remainder;
            }
        }
        if (host.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new CupsTarget(host, port, username, password));
    }

    private static Optional<String> findExecutable(String name) {
        for (String path : List.of("/usr/bin/" + name, "/usr/local/bin/" + name)) {
            if (Files.isExecutable(Path.of(path))) {
                return Optional.of(path);
            }
        }
        try {
            ProcessBuilder pb = new ProcessBuilder("which", name);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String found = process.inputReader().lines().findFirst().orElse("").trim();
            process.waitFor();
            if (!found.isBlank() && Files.isExecutable(Path.of(found))) {
                return Optional.of(found);
            }
        } catch (Exception e) {
            log.debug("which {} fehlgeschlagen: {}", name, e.getMessage());
        }
        return Optional.empty();
    }

    private static boolean startsWithPdfHeader(byte[] content) {
        return content.length >= 5
                && content[0] == '%'
                && content[1] == 'P'
                && content[2] == 'D'
                && content[3] == 'F'
                && content[4] == '-';
    }

    private static long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return 0;
        }
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // ignore
        }
    }

    private record CupsTarget(String host, int port, String username, String password) {
        String hostPort() {
            return host + ":" + port;
        }

        String userAuth() {
            if (username == null || username.isBlank()) {
                return null;
            }
            if (password != null && !password.isBlank()) {
                return username + ":" + password;
            }
            return username;
        }
    }

    public record CupsPrinterOption(String name, String display) {}

    public record CupsListResult(List<CupsPrinterOption> printers, String error) {
        public static CupsListResult failure(String error) {
            return new CupsListResult(List.of(), error);
        }
    }

    private record CommandResult(int exitCode, List<String> output) {}

    public record CupsPrintResult(boolean success, String message) {
        public static CupsPrintResult success(String message) {
            return new CupsPrintResult(true, message);
        }

        public static CupsPrintResult failure(String message) {
            return new CupsPrintResult(false, message);
        }
    }
}
