package de.feuerwehr.manager.routing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlarmRouteService {

    private static final String NOMINATIM_URL = "https://nominatim.openstreetmap.org/search";
    private static final String OSRM_URL = "https://router.project-osrm.org/route/v1/driving";
    private static final String USER_AGENT = "Feuerwehr-Manager/1.0 (alarm routing)";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(12)).build();

    public record LatLon(double lat, double lon) {}

    public record RouteStep(String direction, String instruction, String distance) {}

    public record RoutePlan(
            String startAddress,
            String destinationAddress,
            String routeTitle,
            int distanceMeters,
            int durationSeconds,
            double avgSpeedKmh,
            List<RouteStep> steps,
            String plainText) {}

    public Optional<RoutePlan> planRoute(String startAddress, String destinationAddress, String routeTitle) {
        if (startAddress == null
                || startAddress.isBlank()
                || destinationAddress == null
                || destinationAddress.isBlank()) {
            return Optional.empty();
        }
        try {
            Optional<LatLon> from = geocode(startAddress.trim());
            Optional<LatLon> to = geocode(destinationAddress.trim());
            if (from.isEmpty() || to.isEmpty()) {
                log.warn("[Routing] Geocoding fehlgeschlagen: {} -> {}", startAddress, destinationAddress);
                return Optional.empty();
            }
            return routeOsrm(from.get(), to.get(), startAddress.trim(), destinationAddress.trim(), routeTitle);
        } catch (Exception e) {
            log.warn("[Routing] Fehler: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<RoutePlan> routeOsrm(
            LatLon from, LatLon to, String startLabel, String destLabel, String routeTitle) throws Exception {
        String coords = from.lon() + "," + from.lat() + ";" + to.lon() + "," + to.lat();
        URI uri = URI.create(OSRM_URL + "/" + coords + "?steps=true&overview=false&geometries=geojson");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200 || response.body() == null) {
            return Optional.empty();
        }
        JsonNode root = objectMapper.readTree(response.body());
        if (!"Ok".equalsIgnoreCase(root.path("code").asText(""))) {
            return Optional.empty();
        }
        JsonNode route = root.path("routes").path(0);
        if (route.isMissingNode()) {
            return Optional.empty();
        }
        double distanceM = route.path("distance").asDouble(0);
        double durationS = route.path("duration").asDouble(0);
        int distanceMi = (int) Math.round(distanceM);
        int durationSi = (int) Math.round(durationS);
        double avgSpeed = durationS > 0 ? (distanceM / 1000.0) / (durationS / 3600.0) : 0;

        List<RouteStep> steps = new ArrayList<>();
        JsonNode legSteps = route.path("legs").path(0).path("steps");
        if (legSteps.isArray()) {
            for (JsonNode step : legSteps) {
                JsonNode maneuver = step.path("maneuver");
                double bearing = maneuver.path("bearing_after").asDouble(0);
                String road = step.path("name").asText("");
                String instruction = buildInstruction(maneuver.path("type").asText(""), maneuver.path("modifier").asText(""), road);
                int stepDist = (int) Math.round(step.path("distance").asDouble(0));
                steps.add(new RouteStep(formatBearing(bearing), instruction, stepDist + " m"));
            }
        }
        if (!steps.isEmpty()) {
            steps.add(new RouteStep("", "ZIEL ERREICHT", ""));
        }

        String title = routeTitle != null && !routeTitle.isBlank() ? routeTitle.trim() : "Einsatzstelle";
        String plain = buildPlainText(title, distanceMi, durationSi, avgSpeed, steps);
        return Optional.of(new RoutePlan(
                startLabel, destLabel, title, distanceMi, durationSi, avgSpeed, steps, plain));
    }

    private Optional<LatLon> geocode(String address) throws Exception {
        String query = URLEncoder.encode(address + ", Deutschland", StandardCharsets.UTF_8);
        URI uri = URI.create(NOMINATIM_URL + "?q=" + query + "&format=json&limit=1&countrycodes=de");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", "de")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200 || response.body() == null || response.body().isBlank()) {
            return Optional.empty();
        }
        JsonNode root = objectMapper.readTree(response.body());
        if (!root.isArray() || root.isEmpty()) {
            return Optional.empty();
        }
        JsonNode first = root.get(0);
        double lat = first.path("lat").asDouble(0);
        double lon = first.path("lon").asDouble(0);
        if (lat == 0 && lon == 0) {
            return Optional.empty();
        }
        return Optional.of(new LatLon(lat, lon));
    }

    private static String buildInstruction(String type, String modifier, String road) {
        String street = road != null && !road.isBlank() ? road.trim() : "";
        return switch (type) {
            case "depart" -> street.isBlank() ? "Start" : "dem Straßenverlauf von " + street + " folgen";
            case "arrive" -> "Ziel erreicht";
            case "roundabout" -> "Kreisverkehr nehmen";
            case "turn", "new name", "continue" -> {
                String dir = modifierToGerman(modifier);
                if (!street.isBlank()) {
                    yield (dir.isBlank() ? "" : dir + " ") + "dem Straßenverlauf von " + street + " folgen";
                }
                yield dir.isBlank() ? "weiterfahren" : dir + " weiterfahren";
            }
            default -> street.isBlank() ? "weiterfahren" : "über " + street;
        };
    }

    private static String modifierToGerman(String modifier) {
        if (modifier == null || modifier.isBlank()) {
            return "";
        }
        return switch (modifier) {
            case "left" -> "links abbiegen";
            case "right" -> "rechts abbiegen";
            case "slight left" -> "leicht links halten";
            case "slight right" -> "leicht rechts halten";
            case "sharp left" -> "scharf links";
            case "sharp right" -> "scharf rechts";
            case "straight" -> "geradeaus";
            case "uturn" -> "wenden";
            default -> modifier;
        };
    }

    private static String formatBearing(double bearing) {
        String[] dirs = {"N", "NO", "O", "SO", "S", "SW", "W", "NW"};
        int idx = (int) Math.round(bearing / 45.0) % 8;
        if (idx < 0) {
            idx += 8;
        }
        return dirs[idx] + " " + Math.round(bearing) + "°";
    }

    private static String buildPlainText(String routeTitle, int distanceM, int durationSec, double avgSpeed, List<RouteStep> steps) {
        StringBuilder sb = new StringBuilder();
        sb.append("Route für ").append(routeTitle).append('\n');
        sb.append("Routingkriterium: Zeit\n");
        sb.append("Fahrtlänge: ")
                .append(distanceM)
                .append(" m (")
                .append(formatMinutes(durationSec))
                .append(") bei durchschnittlich ")
                .append(String.format(Locale.GERMANY, "%.1f", avgSpeed))
                .append(" km/h\n");
        for (RouteStep step : steps) {
            if (step.direction() != null && !step.direction().isBlank()) {
                sb.append(step.direction()).append(' ');
            }
            sb.append(step.instruction());
            if (step.distance() != null && !step.distance().isBlank()) {
                sb.append(' ').append(step.distance());
            }
            sb.append('\n');
        }
        return sb.toString().trim();
    }

    private static String formatMinutes(int durationSec) {
        int minutes = Math.max(1, (int) Math.round(durationSec / 60.0));
        return minutes + (minutes == 1 ? " Minute" : " Minuten");
    }
}
