package de.feuerwehr.manager.web;

import de.feuerwehr.manager.dsgvo.AuditEvent;
import de.feuerwehr.manager.dsgvo.AuditEventRepository;
import de.feuerwehr.manager.dsgvo.AuditEventType;
import de.feuerwehr.manager.logging.ContainerLogBuffer;
import de.feuerwehr.manager.settings.ApplicationSettings;
import de.feuerwehr.manager.settings.GlobalSettingsService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitService;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserRepository;
import de.feuerwehr.manager.dsgvo.AuditEventTypeIcons;
import de.feuerwehr.manager.dsgvo.AuditEventTypeLabels;
import de.feuerwehr.manager.user.UserRole;
import de.feuerwehr.manager.web.dto.AuditLogRow;
import de.feuerwehr.manager.web.dto.UnitTableRow;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;
import org.springframework.web.util.HtmlUtils;

@Service
@RequiredArgsConstructor
public class AdminGlobalViewService {

    private final GlobalSettingsService globalSettingsService;
    private final UnitService unitService;
    private final UserRepository userRepository;
    private final AuditEventRepository auditEventRepository;
    private final ContainerLogBuffer containerLogBuffer;

    public void populateKonfiguration(Model model) {
        ApplicationSettings s = globalSettingsService.get();
        model.addAttribute("globalSettings", s);
        model.addAttribute("hasCustomLogo", globalSettingsService.hasCustomLogo());
        model.addAttribute(
                "privacyIncomplete",
                isBlank(s.getPrivacyContactName()) || isBlank(s.getPrivacyContactEmail()));
    }

    public void populateEinheiten(Model model) {
        List<Unit> units = unitService.findAllOrdered();
        Map<Long, String> adminsByUnit = userRepository.findAdminLevelAccountsWithUnit().stream()
                .filter(u -> u.getRole() == UserRole.UNIT_ADMIN && u.getUnit() != null)
                .collect(Collectors.groupingBy(
                        u -> u.getUnit().getId(),
                        Collectors.mapping(
                                u -> u.getDisplayName() + " (@" + u.getUsername() + ")",
                                Collectors.joining(", "))));

        List<UnitTableRow> rows = new ArrayList<>();
        for (Unit unit : units) {
            rows.add(new UnitTableRow(
                    unit.getId(),
                    unit.getName(),
                    unit.isActive(),
                    adminsByUnit.getOrDefault(unit.getId(), "—"),
                    unit.isTestData()));
        }
        model.addAttribute("unitRows", rows);
    }

    public void populateAuditLog(Model model) {
        List<AuditEvent> events = auditEventRepository.findAllByOrderByOccurredAtDesc(PageRequest.of(0, 500));
        Set<Long> userIds = new HashSet<>();
        for (AuditEvent event : events) {
            if (event.getActorUserId() != null && event.getActorUserId() > 0) {
                userIds.add(event.getActorUserId());
            }
            if (event.getSubjectUserId() != null && event.getSubjectUserId() > 0) {
                userIds.add(event.getSubjectUserId());
            }
        }
        Map<Long, User> usersById = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<AuditLogRow> rows = events.stream()
                .map(e -> new AuditLogRow(
                        e.getOccurredAt(),
                        formatActor(e, usersById),
                        buildActionHtml(e),
                        buildDetailColumn(e, usersById)))
                .toList();
        model.addAttribute("auditRows", rows);
    }

    public void populateContainerLog(Model model) {
        model.addAttribute("containerLogLines", containerLogBuffer.snapshot());
    }

    public void populateSmtp(Model model) {
        ApplicationSettings s = globalSettingsService.get();
        model.addAttribute("globalSettings", s);
        model.addAttribute("smtpPasswordConfigured", globalSettingsService.isSmtpPasswordConfigured());
    }

    private static String buildActionHtml(AuditEvent event) {
        String label = AuditEventTypeLabels.label(event.getEventType());
        return AuditEventTypeIcons.svg(event.getEventType())
                + "<span class=\"audit-action__label\">"
                + HtmlUtils.htmlEscape(label)
                + "</span>";
    }

    private static String buildDetailColumn(AuditEvent event, Map<Long, User> usersById) {
        if (event.getEventType() == AuditEventType.USER_ANONYMIZED) {
            String deleted = resolveDeletedUserDetail(event, usersById);
            return deleted != null ? deleted : "";
        }
        return event.getDetail() != null ? event.getDetail() : "";
    }

    private static String resolveDeletedUserDetail(AuditEvent event, Map<Long, User> usersById) {
        if (event.getDetail() != null && !event.getDetail().isBlank()) {
            return event.getDetail().trim();
        }
        if (event.getSubjectUserId() == null) {
            return null;
        }
        User subject = usersById.get(event.getSubjectUserId());
        if (subject == null) {
            return "Nutzer-ID " + event.getSubjectUserId();
        }
        if (subject.getAnonymizedAt() != null) {
            return "Nutzer-ID " + subject.getId();
        }
        return subject.getUsername() + " · " + subject.getDisplayName();
    }

    private static String formatActor(AuditEvent event, Map<Long, User> usersById) {
        if (event.getActorUserId() == null) {
            return "—";
        }
        User user = usersById.get(event.getActorUserId());
        if (user == null) {
            return "#" + event.getActorUserId();
        }
        return user.getUsername();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
