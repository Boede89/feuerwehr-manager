package de.feuerwehr.manager.user;

import de.feuerwehr.manager.dsgvo.AuditEventType;
import de.feuerwehr.manager.dsgvo.AuditService;
import de.feuerwehr.manager.security.AccessControlService;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.security.SecurityProperties;
import de.feuerwehr.manager.personal.PersonUserLinkService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitRepository;
import de.feuerwehr.manager.unit.UnitRole;
import de.feuerwehr.manager.unit.UnitRoleService;
import de.feuerwehr.manager.unit.UserUnitFunction;
import de.feuerwehr.manager.unit.UserUnitFunctionId;
import de.feuerwehr.manager.unit.UserUnitFunctionRepository;
import de.feuerwehr.manager.web.dto.UserDataExport;
import jakarta.servlet.http.HttpServletRequest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserManagementService {

    private static final SecureRandom PASSWORD_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final UnitRepository unitRepository;
    private final UserRfidCardRepository rfidCardRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final SecurityProperties securityProperties;
    private final AccessControlService accessControlService;
    private final PersonUserLinkService personUserLinkService;
    private final UserService userService;
    private final UnitRoleService unitRoleService;
    private final UserUnitFunctionRepository userUnitFunctionRepository;
    private final UserTotpService userTotpService;

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
            return userRepository.findUnitScopedAccountsByUnitId(actor.getUnitId());
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
            Long organizationalRoleId,
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
        applyDienstgrad(user, organizationalRoleId);
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
            Long organizationalRoleId,
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
        clearFunctionsIfPrivileged(user);
        applyDienstgrad(user, organizationalRoleId);
        User saved = userRepository.findByIdWithUnit(userRepository.save(user).getId()).orElseThrow();
        personUserLinkService.ensurePersonForUser(saved);
        auditService.record(
                AuditEventType.USER_UPDATED, actor.getUserId(), saved.getId(), request, "Benutzer aktualisiert");
        return saved;
    }

    @Transactional
    public void resetTotpByAdmin(long userId, AppUserDetails actor, HttpServletRequest request) {
        User user = userRepository.findByIdWithUnit(userId).orElseThrow();
        if (user.getAnonymizedAt() != null) {
            throw new IllegalArgumentException("Konto wurde gelöscht");
        }
        accessControlService.requireCanManageUser(actor, user);
        userTotpService.resetByAdmin(userId, actor.getUserId(), request);
    }

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
    public void updateOwnTheme(long userId, String theme, HttpServletRequest request) {
        if (!"light".equals(theme) && !"dark".equals(theme)) {
            throw new IllegalArgumentException("Ungültiges Design.");
        }
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("Benutzer nicht gefunden"));
        if (user.getAnonymizedAt() != null) {
            throw new IllegalArgumentException("Konto wurde gelöscht");
        }
        user.setTheme(theme);
        userRepository.save(user);
        auditService.record(AuditEventType.USER_UPDATED, userId, userId, request, "Design auf " + theme + " gesetzt");
    }

    @Transactional
    public User updateOwnAccount(
            long userId,
            String username,
            String diveraApiKey,
            boolean clearDiveraApiKey,
            HttpServletRequest request) {
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("Benutzer nicht gefunden"));
        if (user.getAnonymizedAt() != null) {
            throw new IllegalArgumentException("Konto wurde gelöscht");
        }
        UsernameHelper.validate(username);
        String trimmedUsername = username.trim();
        if (!trimmedUsername.equalsIgnoreCase(user.getUsername())
                && userRepository.existsByUsernameIgnoreCaseAndIdNot(trimmedUsername, userId)) {
            throw new IllegalArgumentException("Benutzername ist bereits vergeben");
        }
        user.setUsername(trimmedUsername);
        if (clearDiveraApiKey) {
            user.setDiveraApiKey(null);
        } else if (diveraApiKey != null && !diveraApiKey.isBlank()) {
            user.setDiveraApiKey(normalizeDiveraApiKey(diveraApiKey));
        }
        User saved = userRepository.save(user);
        auditService.record(
                AuditEventType.USER_UPDATED,
                userId,
                userId,
                request,
                "Eigene Kontoeinstellungen gespeichert");
        return saved;
    }

    private static String normalizeDiveraApiKey(String key) {
        return key.trim().replaceAll("[\\r\\n\\t\\v]+", "");
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
    public void deleteUserByAdmin(long userId, AppUserDetails actor, HttpServletRequest request) {
        if (actor.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Sie können sich nicht selbst löschen.");
        }
        User target = userRepository.findByIdWithUnit(userId).orElseThrow();
        if (target.getAnonymizedAt() != null) {
            throw new IllegalArgumentException("Konto wurde bereits gelöscht.");
        }
        accessControlService.requireCanManageUser(actor, target);
        if (target.getRole() == UserRole.SUPER_ADMIN && target.isActive()) {
            long supers = userRepository.countByRoleAndActiveTrueAndAnonymizedAtIsNull(UserRole.SUPER_ADMIN);
            if (supers <= 1) {
                throw new IllegalArgumentException("Der letzte aktive Superadmin kann nicht gelöscht werden.");
            }
        }
        String auditDetail = target.getUsername() + " · " + target.getDisplayName();
        auditService.record(AuditEventType.USER_ANONYMIZED, actor.getUserId(), userId, request, auditDetail);
        userService.anonymizeUser(userId);
    }

    @Transactional
    public void revokeRfidCard(long cardId, AppUserDetails actor, HttpServletRequest request) {
        UserRfidCard card = rfidCardRepository.findById(cardId).orElseThrow();
        accessControlService.requireCanManageUser(actor, card.getUser());
        if (!card.isActive()) {
            throw new IllegalArgumentException("Chip ist bereits gesperrt.");
        }
        card.setActive(false);
        rfidCardRepository.save(card);
        auditService.record(
                AuditEventType.RFID_CARD_REVOKED,
                actor.getUserId(),
                card.getUser().getId(),
                request,
                "RFID-Karte gesperrt");
    }

    @Transactional
    public void reactivateRfidCard(long cardId, AppUserDetails actor, HttpServletRequest request) {
        if (!actor.getRole().isAdminLevel()) {
            throw new IllegalArgumentException("Nur Administratoren können gesperrte Chips entsperren.");
        }
        UserRfidCard card = rfidCardRepository.findById(cardId).orElseThrow();
        accessControlService.requireCanManageUser(actor, card.getUser());
        if (card.isActive()) {
            throw new IllegalArgumentException("Chip ist bereits aktiv.");
        }
        card.setActive(true);
        rfidCardRepository.save(card);
        auditService.record(
                AuditEventType.RFID_CARD_REGISTERED,
                actor.getUserId(),
                card.getUser().getId(),
                request,
                "RFID-Karte entsperrt");
    }

    @Transactional
    public void deleteRfidCard(long cardId, AppUserDetails actor, HttpServletRequest request) {
        UserRfidCard card = rfidCardRepository.findById(cardId).orElseThrow();
        accessControlService.requireCanManageUser(actor, card.getUser());
        long ownerId = card.getUser().getId();
        rfidCardRepository.delete(card);
        auditService.record(
                AuditEventType.RFID_CARD_REVOKED,
                actor.getUserId(),
                ownerId,
                request,
                "RFID-Karte gelöscht");
    }

    @Transactional
    public void deleteOwnRfidCard(long userId, long cardId, HttpServletRequest request) {
        UserRfidCard card = rfidCardRepository.findById(cardId).orElseThrow();
        if (card.getUser() == null || card.getUser().getId() != userId) {
            throw new IllegalArgumentException("Chip gehört nicht zu Ihrem Benutzerkonto");
        }
        rfidCardRepository.delete(card);
        auditService.record(
                AuditEventType.RFID_CARD_REVOKED,
                userId,
                userId,
                request,
                "RFID-Karte in Einstellungen gelöscht");
    }

    public List<UserRfidCard> listRfidCards(long userId) {
        return rfidCardRepository.findByUserId(userId);
    }

    @Transactional
    public UserRfidCard registerOwnRfidCard(long userId, String rawCardUid, String label, HttpServletRequest request) {
        User user = userRepository.findByIdWithUnit(userId).orElseThrow();
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
                userId,
                userId,
                request,
                "RFID-Karte in Einstellungen registriert");
        return saved;
    }

    @Transactional
    public void revokeOwnRfidCard(long userId, long cardId, HttpServletRequest request) {
        UserRfidCard card = rfidCardRepository.findById(cardId).orElseThrow();
        if (card.getUser() == null || card.getUser().getId() != userId) {
            throw new IllegalArgumentException("Chip gehört nicht zu Ihrem Benutzerkonto");
        }
        if (!card.isActive()) {
            throw new IllegalArgumentException("Chip ist bereits gesperrt.");
        }
        card.setActive(false);
        rfidCardRepository.save(card);
        auditService.record(
                AuditEventType.RFID_CARD_REVOKED,
                userId,
                userId,
                request,
                "RFID-Karte in Einstellungen gesperrt");
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

    @Transactional(readOnly = true)
    public List<UnitRole> listUserFunctions(long userId) {
        return userUnitFunctionRepository.findByUserIdWithRoleOrderByRoleNameAsc(userId).stream()
                .map(UserUnitFunction::getRole)
                .toList();
    }

    @Transactional
    public void assignDienstgrad(
            long userId, Long dienstgradRoleId, AppUserDetails actor, HttpServletRequest request) {
        User user = userRepository.findByIdWithUnit(userId).orElseThrow();
        accessControlService.requireCanManageUser(actor, user);
        if (user.getRole() != UserRole.USER) {
            throw new IllegalArgumentException("Dienstgrade sind nur für Benutzer mit Systemrolle „Benutzer“ verfügbar.");
        }
        applyDienstgrad(user, dienstgradRoleId);
        userRepository.save(user);
        auditService.record(
                AuditEventType.USER_UPDATED,
                actor.getUserId(),
                userId,
                request,
                "Dienstgrad zugewiesen");
    }

    @Transactional
    public void assignFunction(long userId, long roleId, AppUserDetails actor, HttpServletRequest request) {
        User user = userRepository.findByIdWithUnit(userId).orElseThrow();
        accessControlService.requireCanManageUser(actor, user);
        if (user.getRole() != UserRole.USER) {
            throw new IllegalArgumentException("Zusatzfunktionen sind nur für normale Benutzer verfügbar.");
        }
        if (user.getUnit() == null) {
            throw new IllegalArgumentException("Benutzer ohne Einheit.");
        }
        UnitRole funktion = unitRoleService.requireFunktionRole(user.getUnit().getId(), roleId);
        if (userUnitFunctionRepository.existsByUserIdAndRoleId(userId, roleId)) {
            return;
        }
        UserUnitFunction link = new UserUnitFunction();
        link.setId(new UserUnitFunctionId(user.getId(), funktion.getId()));
        link.setUser(user);
        link.setRole(funktion);
        userUnitFunctionRepository.save(link);
        auditService.record(
                AuditEventType.USER_UPDATED,
                actor.getUserId(),
                userId,
                request,
                "Zusatzfunktion zugewiesen: " + funktion.getName());
    }

    @Transactional
    public void removeFunction(long userId, long roleId, AppUserDetails actor, HttpServletRequest request) {
        User user = userRepository.findByIdWithUnit(userId).orElseThrow();
        accessControlService.requireCanManageUser(actor, user);
        userUnitFunctionRepository.deleteByUserIdAndRoleId(userId, roleId);
        auditService.record(
                AuditEventType.USER_UPDATED,
                actor.getUserId(),
                userId,
                request,
                "Zusatzfunktion entfernt");
    }

    private void clearFunctionsIfPrivileged(User user) {
        if (user.getRole() != UserRole.USER) {
            user.setOrganizationalRole(null);
            userUnitFunctionRepository.deleteByUserId(user.getId());
        }
    }

    /** Dienstgrad am Benutzer setzen (z. B. aus Qualifikation der Person). */
    @Transactional
    public void syncDienstgradForUser(long userId, Long dienstgradRoleId) {
        User user = userRepository.findByIdWithUnit(userId).orElseThrow(() -> new IllegalArgumentException("Benutzer nicht gefunden"));
        applyDienstgrad(user, dienstgradRoleId);
        userRepository.save(user);
    }

    private void applyDienstgrad(User user, Long dienstgradRoleId) {
        if (user.getRole() != UserRole.USER) {
            user.setOrganizationalRole(null);
            return;
        }
        if (user.getUnit() == null) {
            throw new IllegalArgumentException("Benutzer ohne Einheit kann keinen Dienstgrad erhalten.");
        }
        if (dienstgradRoleId == null || dienstgradRoleId <= 0) {
            user.setOrganizationalRole(null);
            return;
        }
        UnitRole role = unitRoleService.requireDienstgradRole(user.getUnit().getId(), dienstgradRoleId);
        user.setOrganizationalRole(role);
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

    public void validatePlainPassword(String plainPassword) {
        validatePassword(plainPassword);
    }

    /** Vierstelliges Zahlencode (0000–9999) für automatisch generierte Initialpasswörter. */
    public String generateNumericPassword() {
        return String.format("%04d", PASSWORD_RANDOM.nextInt(10_000));
    }

    private void validatePassword(String plainPassword) {
        int min = securityProperties.minPasswordLength();
        if (plainPassword == null || plainPassword.length() < min) {
            throw new IllegalArgumentException("Passwort mindestens " + min + " Zeichen");
        }
    }
}
