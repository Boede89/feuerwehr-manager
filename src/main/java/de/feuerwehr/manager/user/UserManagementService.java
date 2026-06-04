package de.feuerwehr.manager.user;

import de.feuerwehr.manager.dsgvo.AuditEventType;
import de.feuerwehr.manager.dsgvo.AuditService;
import de.feuerwehr.manager.security.AccessControlService;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.security.SecurityProperties;
import de.feuerwehr.manager.personal.PersonUserLinkService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitRepository;
import de.feuerwehr.manager.web.dto.UserDataExport;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserManagementService {

    private final UserRepository userRepository;
    private final UnitRepository unitRepository;
    private final UserRfidCardRepository rfidCardRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final SecurityProperties securityProperties;
    private final AccessControlService accessControlService;
    private final PersonUserLinkService personUserLinkService;

    public List<User> listAccounts(AppUserDetails actor) {
        return listAccounts(actor, null);
    }

    /**
     * @param scopeUnitId nur für Superadmin: Konten der gewählten Einheit (Kopfzeilen-Umschalter)
     */
    public List<User> listAdminLevelAccounts() {
        return userRepository.findAdminLevelAccountsWithUnit();
    }

    public List<User> listAccounts(AppUserDetails actor, Long scopeUnitId) {
        if (actor != null && actor.getRole().isSuperAdmin()) {
            if (scopeUnitId != null && scopeUnitId > 0) {
                return userRepository.findAllByAnonymizedAtIsNullAndUnitIdOrderByUsernameAsc(scopeUnitId);
            }
            return userRepository.findAllByAnonymizedAtIsNullWithUnitOrderByUsernameAsc();
        }
        if (actor != null && actor.getRole().isUnitAdmin() && actor.getUnitId() != null) {
            return userRepository.findAllByAnonymizedAtIsNullAndUnitIdOrderByUsernameAsc(actor.getUnitId());
        }
        return List.of();
    }

    @Transactional
    public User createUser(
            String username,
            String displayName,
            String loginEmail,
            String plainPassword,
            UserRole role,
            Long unitId,
            String rfidCardUid,
            String rfidLabel,
            AppUserDetails actor,
            HttpServletRequest request) {
        accessControlService.requireCanAssignRole(actor, role);
        UsernameHelper.validate(username);
        validatePassword(plainPassword);
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new IllegalArgumentException("Benutzername ist bereits vergeben");
        }
        String normalizedEmail = normalizeLoginEmail(loginEmail);
        if (normalizedEmail != null
                && userRepository.findByLoginEmailIgnoreCaseExcludingId(normalizedEmail, null).isPresent()) {
            throw new IllegalArgumentException("E-Mail wird bereits für die Anmeldung verwendet");
        }
        User user = new User();
        user.setUsername(username.trim());
        user.setDisplayName(displayName.trim());
        user.setLoginEmail(normalizedEmail);
        user.setRole(role != null ? role : UserRole.USER);
        user.setActive(true);
        user.setPasswordHash(passwordEncoder.encode(plainPassword));
        applyUnit(user, unitId, actor);
        User saved = userRepository.findByIdWithUnit(userRepository.save(user).getId()).orElseThrow();
        if (rfidCardUid != null && !rfidCardUid.isBlank()) {
            registerRfidCard(saved.getId(), rfidCardUid, rfidLabel, actor, request);
            saved = userRepository.findByIdWithUnit(saved.getId()).orElseThrow();
        }
        personUserLinkService.ensurePersonForUser(saved);
        auditService.record(
                AuditEventType.USER_CREATED,
                actor.getUserId(),
                saved.getId(),
                request,
                "Benutzer angelegt");
        return saved;
    }

    /** Login-Konto beim Anlegen einer Person (immer Rolle USER, Einheit = Person). */
    @Transactional
    public User createUserForPerson(
            String username,
            String displayName,
            String plainPassword,
            long unitId,
            String loginEmail,
            long actorUserId,
            HttpServletRequest request) {
        UsernameHelper.validate(username);
        validatePassword(plainPassword);
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new IllegalArgumentException("Benutzername ist bereits vergeben");
        }
        String normalizedEmail = normalizeLoginEmail(loginEmail);
        if (normalizedEmail != null
                && userRepository.findByLoginEmailIgnoreCaseExcludingId(normalizedEmail, null).isPresent()) {
            throw new IllegalArgumentException("E-Mail wird bereits für die Anmeldung verwendet");
        }
        Unit unit = unitRepository.findById(unitId).orElseThrow(() -> new IllegalArgumentException("Einheit nicht gefunden"));
        User user = new User();
        user.setUsername(username.trim());
        user.setLoginEmail(normalizedEmail);
        user.setDisplayName(displayName.trim());
        user.setRole(UserRole.USER);
        user.setUnit(unit);
        user.setActive(true);
        user.setPasswordHash(passwordEncoder.encode(plainPassword));
        User saved = userRepository.save(user);
        auditService.record(AuditEventType.USER_CREATED, actorUserId, saved.getId(), request, "Benutzer über Personal angelegt");
        return saved;
    }

    public String allocateUniqueUsername(String firstName, String lastName) {
        String base = UsernameHelper.suggestFromPersonName(firstName, lastName);
        if (!userRepository.existsByUsernameIgnoreCase(base)) {
            return base;
        }
        for (int i = 2; i < 1000; i++) {
            String candidate = UsernameHelper.truncate(base + i);
            if (!userRepository.existsByUsernameIgnoreCase(candidate)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Kein freier Benutzername ermittelbar");
    }

    @Transactional
    public User updateUser(
            long userId,
            String username,
            String displayName,
            String loginEmail,
            UserRole role,
            Long unitId,
            boolean active,
            AppUserDetails actor,
            HttpServletRequest request) {
        User user = userRepository.findByIdWithUnit(userId).orElseThrow();
        if (user.getAnonymizedAt() != null) {
            throw new IllegalArgumentException("Konto wurde gelöscht");
        }
        accessControlService.requireCanManageUser(actor, user);
        accessControlService.requireCanAssignRole(actor, role);
        UsernameHelper.validate(username);
        String trimmedUsername = username.trim();
        if (!trimmedUsername.equalsIgnoreCase(user.getUsername())) {
            if (userRepository.existsByUsernameIgnoreCaseAndIdNot(trimmedUsername, userId)) {
                throw new IllegalArgumentException("Benutzername ist bereits vergeben");
            }
            user.setUsername(trimmedUsername);
        }
        if (!active && user.getId().equals(actor.getUserId())) {
            throw new IllegalArgumentException("Sie können sich nicht selbst deaktivieren");
        }
        ensureNotLastSuperAdmin(user, role, active);
        String normalizedEmail = normalizeLoginEmail(loginEmail);
        if (normalizedEmail != null
                && userRepository
                        .findByLoginEmailIgnoreCaseExcludingId(normalizedEmail, userId)
                        .isPresent()) {
            throw new IllegalArgumentException("E-Mail wird bereits für die Anmeldung verwendet");
        }
        user.setDisplayName(displayName.trim());
        user.setLoginEmail(normalizedEmail);
        user.setRole(role);
        user.setActive(active);
        applyUnit(user, unitId, actor);
        User saved = userRepository.findByIdWithUnit(userRepository.save(user).getId()).orElseThrow();
        personUserLinkService.ensurePersonForUser(saved);
        auditService.record(
                AuditEventType.USER_UPDATED, actor.getUserId(), saved.getId(), request, "Benutzer aktualisiert");
        return saved;
    }

    @Transactional
    public void setPasswordByAdmin(
            long userId, String plainPassword, AppUserDetails actor, HttpServletRequest request) {
        validatePassword(plainPassword);
        User user = userRepository.findByIdWithUnit(userId).orElseThrow();
        if (user.getAnonymizedAt() != null) {
            throw new IllegalArgumentException("Konto wurde gelöscht");
        }
        accessControlService.requireCanManageUser(actor, user);
        user.setPasswordHash(passwordEncoder.encode(plainPassword));
        userRepository.save(user);
        auditService.record(
                AuditEventType.PASSWORD_CHANGED,
                actor.getUserId(),
                userId,
                request,
                "Passwort durch Administrator gesetzt");
    }

    @Transactional
    public void changeOwnPassword(long userId, String currentPassword, String newPassword) {
        validatePassword(newPassword);
        User user = userRepository.findById(userId).orElseThrow();
        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Aktuelles Passwort ist falsch");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Transactional
    public UserRfidCard registerRfidCard(
            long userId, String rawCardUid, String label, AppUserDetails actor, HttpServletRequest request) {
        User user = userRepository.findByIdWithUnit(userId).orElseThrow();
        accessControlService.requireCanManageUser(actor, user);
        if (user.getAnonymizedAt() != null) {
            throw new IllegalArgumentException("Konto wurde gelöscht");
        }
        String normalized = RfidCardUidNormalizer.normalize(rawCardUid);
        if (!RfidCardUidNormalizer.isValid(normalized)) {
            throw new IllegalArgumentException("Ungültige Chip-ID (nur Hex-Zeichen, min. 4 Zeichen)");
        }
        if (rfidCardRepository.existsByCardUid(normalized)) {
            throw new IllegalArgumentException("Chip ist bereits registriert");
        }
        UserRfidCard card = new UserRfidCard();
        card.setUser(user);
        card.setCardUid(normalized);
        card.setLabel(label != null ? label.trim() : null);
        card.setActive(true);
        UserRfidCard saved = rfidCardRepository.save(card);
        auditService.record(
                AuditEventType.RFID_CARD_REGISTERED,
                actor.getUserId(),
                userId,
                request,
                "RFID-Karte registriert");
        return saved;
    }

    @Transactional
    public void revokeRfidCard(long cardId, AppUserDetails actor, HttpServletRequest request) {
        UserRfidCard card = rfidCardRepository.findById(cardId).orElseThrow();
        accessControlService.requireCanManageUser(actor, card.getUser());
        card.setActive(false);
        rfidCardRepository.save(card);
        auditService.record(
                AuditEventType.RFID_CARD_REVOKED,
                actor.getUserId(),
                card.getUser().getId(),
                request,
                "RFID-Karte deaktiviert");
    }

    public List<UserRfidCard> listRfidCards(long userId) {
        return rfidCardRepository.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public UserDataExport buildUserExport(long userId, AppUserDetails actor) {
        User user = userRepository.findByIdWithUnit(userId).orElseThrow();
        if (user.getAnonymizedAt() != null) {
            throw new IllegalArgumentException("Konto wurde gelöscht");
        }
        accessControlService.requireCanManageUser(actor, user);
        List<UserDataExport.RfidCardExport> cards = listRfidCards(userId).stream()
                .map(card -> new UserDataExport.RfidCardExport(
                        card.getId(),
                        card.getLabel(),
                        card.isActive(),
                        maskCardUid(card.getCardUid())))
                .toList();
        return new UserDataExport(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getLoginEmail(),
                user.getRole().name(),
                user.getUnit() != null ? user.getUnit().getName() : null,
                user.isActive(),
                user.getCreatedAt(),
                user.getLastLoginAt(),
                user.getPrivacyNoticeVersion(),
                user.getPrivacyNoticeAcceptedAt(),
                cards,
                Instant.now());
    }

    private static String maskCardUid(String uid) {
        if (uid == null || uid.length() < 4) {
            return "****";
        }
        return "****" + uid.substring(uid.length() - 4);
    }

    private void applyUnit(User user, Long unitId, AppUserDetails actor) {
        if (user.getRole() == UserRole.SUPER_ADMIN) {
            if (unitId == null || unitId <= 0) {
                user.setUnit(null);
            } else {
                Unit unit = unitRepository
                        .findById(unitId)
                        .orElseThrow(() -> new IllegalArgumentException("Einheit nicht gefunden"));
                user.setUnit(unit);
            }
            return;
        }
        Long effectiveUnitId = unitId;
        if (actor != null && actor.getRole().isUnitAdmin()) {
            effectiveUnitId = actor.getUnitId();
        }
        if (effectiveUnitId == null) {
            throw new IllegalArgumentException("Bitte eine Einheit zuordnen");
        }
        Unit unit = unitRepository.findById(effectiveUnitId).orElseThrow(() -> new IllegalArgumentException("Einheit nicht gefunden"));
        user.setUnit(unit);
    }

    private void ensureNotLastSuperAdmin(User user, UserRole newRole, boolean active) {
        if (user.getRole() != UserRole.SUPER_ADMIN) {
            return;
        }
        boolean removingSuper =
                newRole != UserRole.SUPER_ADMIN || !active;
        if (!removingSuper) {
            return;
        }
        long supers = userRepository.countByRoleAndActiveTrueAndAnonymizedAtIsNull(UserRole.SUPER_ADMIN);
        if (supers <= 1) {
            throw new IllegalArgumentException("Es muss mindestens ein aktiver Superadmin bleiben");
        }
    }

    private static String normalizeLoginEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase();
    }

    private void validatePassword(String plainPassword) {
        int min = securityProperties.minPasswordLength();
        if (plainPassword == null || plainPassword.length() < min) {
            throw new IllegalArgumentException("Passwort mindestens " + min + " Zeichen");
        }
    }
}
