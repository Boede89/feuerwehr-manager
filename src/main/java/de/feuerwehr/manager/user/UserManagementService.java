package de.feuerwehr.manager.user;

import de.feuerwehr.manager.dsgvo.AuditEventType;
import de.feuerwehr.manager.dsgvo.AuditService;
import de.feuerwehr.manager.security.SecurityProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserManagementService {

    private final UserRepository userRepository;
    private final UserRfidCardRepository rfidCardRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final SecurityProperties securityProperties;

    public List<User> listAccounts() {
        return userRepository.findAllByAnonymizedAtIsNullOrderByUsernameAsc();
    }

    @Transactional
    public User createUser(
            String username,
            String displayName,
            String plainPassword,
            UserRole role,
            long actorUserId,
            HttpServletRequest request) {
        UsernameHelper.validate(username);
        validatePassword(plainPassword);
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new IllegalArgumentException("Benutzername ist bereits vergeben");
        }
        User user = new User();
        user.setUsername(username.trim());
        user.setDisplayName(displayName.trim());
        user.setRole(role != null ? role : UserRole.USER);
        user.setActive(true);
        user.setPasswordHash(passwordEncoder.encode(plainPassword));
        User saved = userRepository.save(user);
        auditService.record(
                AuditEventType.USER_CREATED,
                actorUserId,
                saved.getId(),
                request,
                "Benutzer angelegt");
        return saved;
    }

    /** Vorschlag + ggf. Suffix bei Kollision (m.mustermann, m.mustermann2, …). */
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
            UserRole role,
            boolean active,
            long actorUserId,
            HttpServletRequest request) {
        User user = userRepository.findById(userId).orElseThrow();
        if (user.getAnonymizedAt() != null) {
            throw new IllegalArgumentException("Konto wurde gelöscht");
        }
        UsernameHelper.validate(username);
        String trimmedUsername = username.trim();
        if (!trimmedUsername.equalsIgnoreCase(user.getUsername())) {
            if (userRepository.existsByUsernameIgnoreCaseAndIdNot(trimmedUsername, userId)) {
                throw new IllegalArgumentException("Benutzername ist bereits vergeben");
            }
            user.setUsername(trimmedUsername);
        }
        if (!active && user.getId().equals(actorUserId)) {
            throw new IllegalArgumentException("Sie können sich nicht selbst deaktivieren");
        }
        if (role != UserRole.ADMIN && user.getRole() == UserRole.ADMIN) {
            long admins = userRepository.countByRoleAndActiveTrueAndAnonymizedAtIsNull(UserRole.ADMIN);
            if (admins <= 1) {
                throw new IllegalArgumentException("Es muss mindestens ein aktiver Administrator bleiben");
            }
        }
        if (!active && user.getRole() == UserRole.ADMIN) {
            long admins = userRepository.countByRoleAndActiveTrueAndAnonymizedAtIsNull(UserRole.ADMIN);
            if (admins <= 1) {
                throw new IllegalArgumentException("Der letzte Administrator kann nicht deaktiviert werden");
            }
        }
        user.setDisplayName(displayName.trim());
        user.setRole(role);
        user.setActive(active);
        User saved = userRepository.save(user);
        auditService.record(
                AuditEventType.USER_UPDATED, actorUserId, saved.getId(), request, "Benutzer aktualisiert");
        return saved;
    }

    @Transactional
    public void setPasswordByAdmin(
            long userId, String plainPassword, long actorUserId, HttpServletRequest request) {
        validatePassword(plainPassword);
        User user = userRepository.findById(userId).orElseThrow();
        if (user.getAnonymizedAt() != null) {
            throw new IllegalArgumentException("Konto wurde gelöscht");
        }
        user.setPasswordHash(passwordEncoder.encode(plainPassword));
        userRepository.save(user);
        auditService.record(
                AuditEventType.PASSWORD_CHANGED,
                actorUserId,
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
            long userId, String rawCardUid, String label, long actorUserId, HttpServletRequest request) {
        User user = userRepository.findById(userId).orElseThrow();
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
                actorUserId,
                userId,
                request,
                "RFID-Karte registriert");
        return saved;
    }

    @Transactional
    public void revokeRfidCard(long cardId, long actorUserId, HttpServletRequest request) {
        UserRfidCard card = rfidCardRepository.findById(cardId).orElseThrow();
        card.setActive(false);
        rfidCardRepository.save(card);
        auditService.record(
                AuditEventType.RFID_CARD_REVOKED,
                actorUserId,
                card.getUser().getId(),
                request,
                "RFID-Karte deaktiviert");
    }

    public List<UserRfidCard> listRfidCards(long userId) {
        return rfidCardRepository.findByUserId(userId);
    }

    private void validatePassword(String plainPassword) {
        int min = securityProperties.minPasswordLength();
        if (plainPassword == null || plainPassword.length() < min) {
            throw new IllegalArgumentException("Passwort mindestens " + min + " Zeichen");
        }
    }

}
