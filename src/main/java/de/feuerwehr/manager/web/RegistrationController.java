package de.feuerwehr.manager.web;

import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserRegistrationService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequiredArgsConstructor
public class RegistrationController {

    private final UserRegistrationService userRegistrationService;

    @PostMapping("/login/register")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> register(
            @RequestParam String firstName,
            @RequestParam String lastName,
            @RequestParam String email,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate birthdate,
            @RequestParam long unitId,
            HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        try {
            userRegistrationService.register(firstName, lastName, email, birthdate, unitId, request);
            body.put("ok", true);
            body.put(
                    "message",
                    "Ihre Registrierung wurde übermittelt. Ein Administrator muss das Konto noch freischalten. "
                            + "Die Zugangsdaten erhalten Sie anschließend per E-Mail.");
            return ResponseEntity.ok(body);
        } catch (IllegalArgumentException e) {
            body.put("ok", false);
            body.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(body);
        }
    }
}
