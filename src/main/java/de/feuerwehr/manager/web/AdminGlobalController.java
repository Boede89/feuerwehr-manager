package de.feuerwehr.manager.web;

import de.feuerwehr.manager.dsgvo.AuditService;
import de.feuerwehr.manager.settings.GlobalSettingsService;
import de.feuerwehr.manager.unit.UnitService;
import de.feuerwehr.manager.user.UserRole;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/global")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class AdminGlobalController {

    private static final int MAX_LOGO_BYTES = 200_000;

    private final GlobalSettingsService globalSettingsService;
    private final UnitService unitService;
    private final AuditService auditService;

    @PostMapping("/config")
    public String saveConfig(
            @RequestParam(required = false) String ffName,
            @RequestParam(required = false) String ffStrasse,
            @RequestParam(required = false) String ffOrt,
            @RequestParam(required = false) String appUrl,
            @RequestParam(required = false) String feedbackEmail,
            RedirectAttributes redirectAttributes) {
        try {
            globalSettingsService.saveStammdaten(ffName, ffStrasse, ffOrt, appUrl, feedbackEmail);
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", "Stammdaten gespeichert.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin?scope=global&tab=konfiguration";
    }

    @PostMapping("/privacy")
    public String savePrivacy(
            @RequestParam(required = false) String privacyContactName,
            @RequestParam(required = false) String privacyContactEmail,
            @RequestParam(required = false) String privacyContactPhone,
            @RequestParam(required = false) String privacyHoster,
            RedirectAttributes redirectAttributes) {
        globalSettingsService.savePrivacyContact(
                privacyContactName, privacyContactEmail, privacyContactPhone, privacyHoster);
        redirectAttributes.addFlashAttribute("saved", true);
        redirectAttributes.addFlashAttribute("message", "Kontaktdaten für die Datenschutzerklärung gespeichert.");
        return "redirect:/admin?scope=global&tab=konfiguration";
    }

    @PostMapping("/logo")
    public String uploadLogo(@RequestParam("logoFile") MultipartFile logoFile, RedirectAttributes redirectAttributes) {
        try {
            if (logoFile.isEmpty()) {
                throw new IllegalArgumentException("Bitte eine Bilddatei auswählen.");
            }
            String contentType = logoFile.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                throw new IllegalArgumentException("Nur Bilddateien (PNG, JPG, WebP) sind erlaubt.");
            }
            if (logoFile.getSize() > MAX_LOGO_BYTES) {
                throw new IllegalArgumentException("Das Bild darf maximal 200 KB groß sein.");
            }
            String base64 = Base64.getEncoder().encodeToString(logoFile.getBytes());
            String dataUri = "data:" + contentType + ";base64," + base64;
            globalSettingsService.saveLogoBase64(dataUri);
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", "Wappen wurde gespeichert.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Wappen konnte nicht gespeichert werden: " + e.getMessage());
        }
        return "redirect:/admin?scope=global&tab=konfiguration";
    }

    @PostMapping("/logo/delete")
    public String deleteLogo(RedirectAttributes redirectAttributes) {
        globalSettingsService.clearLogo();
        redirectAttributes.addFlashAttribute("saved", true);
        redirectAttributes.addFlashAttribute("message", "Wappen entfernt — Standard-Emblem wird wieder angezeigt.");
        return "redirect:/admin?scope=global&tab=konfiguration";
    }

    @PostMapping("/smtp")
    public String saveSmtp(
            @RequestParam(required = false) String smtpHost,
            @RequestParam(required = false) Integer smtpPort,
            @RequestParam(required = false) String smtpUsername,
            @RequestParam(required = false) String smtpPassword,
            @RequestParam(required = false) String smtpFromEmail,
            @RequestParam(required = false) String smtpFromName,
            @RequestParam(required = false) String smtpEncryption,
            RedirectAttributes redirectAttributes) {
        try {
            globalSettingsService.saveSmtp(
                    smtpHost, smtpPort, smtpUsername, smtpPassword, smtpFromEmail, smtpFromName, smtpEncryption);
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", "SMTP-Einstellungen gespeichert.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin?scope=global&tab=schnittstellen";
    }

    @PostMapping("/units")
    public String createUnit(@RequestParam String name, RedirectAttributes redirectAttributes) {
        try {
            var created = unitService.create(name);
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", "Einheit „" + created.getName() + "“ wurde angelegt.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin?scope=global&tab=einheiten";
    }

    @PostMapping("/units/update")
    public String updateUnit(
            @RequestParam long unitId,
            @RequestParam String name,
            @RequestParam(required = false) String active,
            RedirectAttributes redirectAttributes) {
        boolean isActive = "true".equalsIgnoreCase(active);
        try {
            unitService.update(unitId, name, isActive);
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", "Einheit wurde gespeichert.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin?scope=global&tab=einheiten";
    }

    @PostMapping("/units/delete")
    public String deleteUnit(@RequestParam long unitId, RedirectAttributes redirectAttributes) {
        try {
            unitService.delete(unitId);
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", "Einheit wurde gelöscht.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin?scope=global&tab=einheiten";
    }

    @PostMapping("/audit/clear")
    public String clearAuditLog(RedirectAttributes redirectAttributes) {
        auditService.clearAll();
        redirectAttributes.addFlashAttribute("saved", true);
        redirectAttributes.addFlashAttribute("message", "Audit-Log wurde geleert.");
        return "redirect:/admin?scope=global&tab=audit";
    }
}
