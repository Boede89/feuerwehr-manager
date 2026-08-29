package de.feuerwehr.manager.pdf;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

public final class PdfDownloadResponse {

    private PdfDownloadResponse() {}

    public static ResponseEntity<byte[]> attachment(String filename, byte[] pdfBytes) {
        return pdf(filename, pdfBytes, "attachment");
    }

    public static ResponseEntity<byte[]> inline(String filename, byte[] pdfBytes) {
        return pdf(filename, pdfBytes, "inline");
    }

    private static ResponseEntity<byte[]> pdf(String filename, byte[] pdfBytes, String disposition) {
        String safeName = filename != null && !filename.isBlank() ? filename : "dokument.pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition + "; filename=\"" + safeName + "\"")
                .body(pdfBytes);
    }
}
