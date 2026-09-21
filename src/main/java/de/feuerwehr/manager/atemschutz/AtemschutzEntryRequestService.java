package de.feuerwehr.manager.atemschutz;

import de.feuerwehr.manager.mail.UnitMailService;
import de.feuerwehr.manager.notification.UserNotificationPreferenceService;
import de.feuerwehr.manager.notification.UserNotificationTopic;
import de.feuerwehr.manager.settings.GlobalSettingsService;
import de.feuerwehr.manager.settings.TestModeService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitRepository;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AtemschutzEntryRequestService {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY);
    private static final String SOURCE_REF_TYPE = "ENTRY_REQUEST";

    private final AtemschutzEntryRequestRepository requestRepository;
    private final AtemschutzCarrierRepository carrierRepository;
    private final AtemschutzService atemschutzService;
    private final AtemschutzSettingsService settingsService;
    private final UnitRepository unitRepository;
    private final UserRepository userRepository;
    private final UnitMailService unitMailService;
    private final UserNotificationPreferenceService userNotificationPreferenceService;
    private final TestModeService testModeService;
    private final GlobalSettingsService globalSettingsService;

    @Transactional(readOnly = true)
    public List<CarrierOption> listActiveCarrierOptions(long unitId) {
        boolean testData = testModeService.isEnabled();
        Set<Long> csaEligible = atemschutzService.listCsaEligiblePersonIds(unitId);
        return carrierRepository.findByUnitId(unitId, testData).stream()
                .filter(c -> c.getStatus() == AtemschutzCarrierStatus.ACTIVE)
                .sorted(Comparator.comparing(
                                (AtemschutzCarrier c) -> c.getPerson().getLastName(),
                                String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(c -> c.getPerson().getFirstName(), String.CASE_INSENSITIVE_ORDER))
                .map(c -> new CarrierOption(
                        c.getId(),
                        c.getPerson().displayName(),
                        csaEligible.contains(c.getPerson().getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public long countPending(long unitId) {
        return requestRepository.countPendingByUnitId(unitId, testModeService.isEnabled());
    }

    @Transactional(readOnly = true)
    public List<AtemschutzEntryRequest> listPending(long unitId) {
        List<AtemschutzEntryRequest> list = requestRepository.findByUnitIdAndStatus(
                unitId, AtemschutzEntryRequestStatus.PENDING, testModeService.isEnabled());
        list.sort(Comparator.comparing(
                        AtemschutzEntryRequest::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .reversed());
        return list;
    }

    @Transactional(readOnly = true)
    public AtemschutzEntryRequest requireRequest(long requestId) {
        return requestRepository
                .findByIdWithDetails(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Antrag nicht gefunden."));
    }

    @Transactional
    public AtemschutzEntryRequest submit(
            long unitId,
            AtemschutzEntryRequestType entryType,
            LocalDate eventDate,
            List<Long> carrierIds,
            long requestedByUserId) {
        if (entryType == null) {
            throw new IllegalArgumentException("Eintragstyp fehlt.");
        }
        if (eventDate == null) {
            throw new IllegalArgumentException("Datum ist erforderlich.");
        }
        Unit unit = unitRepository
                .findVisibleById(unitId, testModeService.isEnabled())
                .orElseThrow(() -> new IllegalArgumentException("Einheit nicht gefunden."));
        User requester = userRepository
                .findById(requestedByUserId)
                .orElseThrow(() -> new IllegalArgumentException("Benutzer nicht gefunden."));

        Set<AtemschutzCarrier> carriers = resolveCarriers(unitId, entryType, carrierIds);

        AtemschutzEntryRequest request = new AtemschutzEntryRequest();
        request.setUnit(unit);
        request.setEntryType(entryType);
        request.setEventDate(eventDate);
        request.setStatus(AtemschutzEntryRequestStatus.PENDING);
        request.setRequestedBy(requester);
        request.setTestData(testModeService.isEnabled());
        request.setCarriers(carriers);
        AtemschutzEntryRequest saved = requestRepository.save(request);

        notifyInstructors(saved);
        return saved;
    }

    @Transactional
    public AtemschutzEntryRequest updatePending(
            long requestId,
            AtemschutzEntryRequestType entryType,
            LocalDate eventDate,
            List<Long> carrierIds) {
        AtemschutzEntryRequest request = requirePending(requestId);
        if (entryType == null) {
            throw new IllegalArgumentException("Eintragstyp fehlt.");
        }
        if (eventDate == null) {
            throw new IllegalArgumentException("Datum ist erforderlich.");
        }
        long unitId = request.getUnit().getId();
        request.setEntryType(entryType);
        request.setEventDate(eventDate);
        request.setCarriers(resolveCarriers(unitId, entryType, carrierIds));
        return requestRepository.save(request);
    }

    @Transactional
    public AtemschutzEntryRequest approve(
            long requestId,
            AtemschutzEntryRequestType entryType,
            LocalDate eventDate,
            List<Long> carrierIds,
            long reviewerUserId,
            String reviewNote) {
        AtemschutzEntryRequest request = requirePending(requestId);
        long unitId = request.getUnit().getId();

        AtemschutzEntryRequestType type = entryType != null ? entryType : request.getEntryType();
        LocalDate date = eventDate != null ? eventDate : request.getEventDate();
        Set<AtemschutzCarrier> carriers =
                carrierIds != null
                        ? resolveCarriers(unitId, type, carrierIds)
                        : new LinkedHashSet<>(request.getCarriers());
        if (carriers.isEmpty()) {
            throw new IllegalArgumentException("Bitte mindestens einen Geräteträger auswählen.");
        }

        request.setEntryType(type);
        request.setEventDate(date);
        request.setCarriers(carriers);
        request.setStatus(AtemschutzEntryRequestStatus.APPROVED);
        request.setReviewedBy(requireUser(reviewerUserId));
        request.setReviewedAt(Instant.now());
        request.setReviewNote(blankToNull(reviewNote));
        AtemschutzEntryRequest saved = requestRepository.save(request);

        List<Long> ids = carriers.stream().map(AtemschutzCarrier::getId).toList();
        String sourceLabel = type.label() + " · Antrag #" + saved.getId();
        atemschutzService.bulkAddFitnessRecords(
                unitId,
                ids,
                type.toFitnessType(),
                date,
                reviewerUserId,
                SOURCE_REF_TYPE,
                saved.getId(),
                sourceLabel);
        return saved;
    }

    @Transactional
    public AtemschutzEntryRequest reject(long requestId, long reviewerUserId, String reviewNote) {
        AtemschutzEntryRequest request = requirePending(requestId);
        request.setStatus(AtemschutzEntryRequestStatus.REJECTED);
        request.setReviewedBy(requireUser(reviewerUserId));
        request.setReviewedAt(Instant.now());
        request.setReviewNote(blankToNull(reviewNote));
        return requestRepository.save(request);
    }

    @Transactional(readOnly = true)
    public boolean isReviewer(long unitId, long userId, boolean hasAtemschutzWrite) {
        if (hasAtemschutzWrite) {
            return true;
        }
        return settingsService.instructorUserIds(settingsService.requireSettings(unitId)).contains(userId);
    }

    private AtemschutzEntryRequest requirePending(long requestId) {
        AtemschutzEntryRequest request = requireRequest(requestId);
        if (request.getStatus() != AtemschutzEntryRequestStatus.PENDING) {
            throw new IllegalArgumentException("Antrag ist nicht mehr offen.");
        }
        return request;
    }

    private Set<AtemschutzCarrier> resolveCarriers(
            long unitId, AtemschutzEntryRequestType entryType, List<Long> carrierIds) {
        if (carrierIds == null || carrierIds.isEmpty()) {
            throw new IllegalArgumentException("Bitte mindestens einen Geräteträger auswählen.");
        }
        boolean testData = testModeService.isEnabled();
        Set<Long> csaEligible =
                entryType == AtemschutzEntryRequestType.CSA
                        ? atemschutzService.listCsaEligiblePersonIds(unitId)
                        : Set.of();
        LinkedHashSet<AtemschutzCarrier> carriers = new LinkedHashSet<>();
        for (Long carrierId : carrierIds) {
            if (carrierId == null || carrierId <= 0) {
                continue;
            }
            AtemschutzCarrier carrier = carrierRepository
                    .findByIdAndTestData(carrierId, testData)
                    .orElseThrow(() -> new IllegalArgumentException("Geräteträger nicht gefunden."));
            if (carrier.getUnit().getId() != unitId) {
                throw new IllegalArgumentException("Geräteträger gehört nicht zu dieser Einheit.");
            }
            if (carrier.getStatus() != AtemschutzCarrierStatus.ACTIVE) {
                throw new IllegalArgumentException(
                        "Nur aktive Geräteträger können ausgewählt werden: "
                                + carrier.getPerson().displayName());
            }
            if (entryType == AtemschutzEntryRequestType.CSA
                    && !csaEligible.contains(carrier.getPerson().getId())) {
                throw new IllegalArgumentException(
                        "Keine CSA-Berechtigung: " + carrier.getPerson().displayName());
            }
            carriers.add(carrier);
        }
        if (carriers.isEmpty()) {
            throw new IllegalArgumentException("Bitte mindestens einen Geräteträger auswählen.");
        }
        return carriers;
    }

    private void notifyInstructors(AtemschutzEntryRequest request) {
        long unitId = request.getUnit().getId();
        List<Long> instructorIds =
                settingsService.instructorUserIds(settingsService.requireSettings(unitId));
        if (instructorIds.isEmpty()) {
            log.info(
                    "Atemschutz-Antrag #{}: Keine Ausbilder hinterlegt — keine E-Mail versendet.",
                    request.getId());
            return;
        }
        String requesterName = request.getRequestedBy() != null
                ? request.getRequestedBy().getDisplayName()
                : "Unbekannt";
        String carrierNames = request.getCarriers().stream()
                .map(c -> c.getPerson().displayName())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.joining(", "));
        String reviewUrl = buildReviewUrl(unitId, request.getId());
        String cta = reviewUrl == null
                ? "<p>Bitte prüfen und freigeben Sie den Antrag in der Anwendung (Startseite → Hinweis bzw. Atemschutz → Anträge).</p>"
                : """
                  <p style="margin:18px 0 8px;">
                    <a href="%s" style="background:#e63022;color:#fff;padding:10px 16px;text-decoration:none;border-radius:6px;display:inline-block;font-weight:600;">
                      Antrag jetzt prüfen
                    </a>
                  </p>
                  <p style="color:#64748b;font-size:13px;">Oder in der Anwendung: Startseite → Hinweis bzw. Atemschutz → Anträge</p>
                  """
                        .formatted(escapeHtml(reviewUrl));
        String subject = "Neuer Atemschutz-Antrag zur Freigabe";
        String body =
                """
                <p>Es liegt ein neuer Antrag auf Eintragung eines Atemschutz-Nachweises vor.</p>
                <ul>
                  <li><strong>Antragsteller:</strong> %s</li>
                  <li><strong>Eintragstyp:</strong> %s</li>
                  <li><strong>Datum:</strong> %s</li>
                  <li><strong>Geräteträger:</strong> %s</li>
                </ul>
                %s
                """
                        .formatted(
                                escapeHtml(requesterName),
                                escapeHtml(request.getEntryType().label()),
                                DATE_FMT.format(request.getEventDate()),
                                escapeHtml(carrierNames),
                                cta);

        int sent = 0;
        for (Long userId : instructorIds) {
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isEmpty()) {
                continue;
            }
            User user = userOpt.get();
            if (!userNotificationPreferenceService.isEmailEnabled(userId, UserNotificationTopic.ATEMSCHUTZ)) {
                continue;
            }
            String email = user.getLoginEmail();
            if (email == null || email.isBlank()) {
                continue;
            }
            Optional<String> error = unitMailService.sendHtmlMail(unitId, email.trim(), subject, body);
            if (error.isPresent()) {
                log.warn(
                        "Atemschutz-Antrag #{}: Mail an Ausbilder {} fehlgeschlagen: {}",
                        request.getId(),
                        userId,
                        error.get());
            } else {
                sent++;
            }
        }
        log.info("Atemschutz-Antrag #{}: {} Ausbilder-Mail(s) versendet.", request.getId(), sent);
    }

    private String buildReviewUrl(long unitId, long requestId) {
        String base = globalSettingsService.get().getAppUrl();
        if (base == null || base.isBlank()) {
            return null;
        }
        String normalized = base.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized + "/atemschutz/antraege/" + requestId + "?unit=" + unitId;
    }

    private User requireUser(long userId) {
        return userRepository
                .findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Benutzer nicht gefunden."));
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    public record CarrierOption(long id, String name, boolean csaEligible) {}
}
