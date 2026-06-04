package de.feuerwehr.manager.user;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserRfidCardRepository rfidCardRepository;
    private final PasswordEncoder passwordEncoder;

    public Optional<User> findById(long id) {
        return userRepository.findById(id);
    }

    public Optional<User> findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByUsernameIgnoreCase(username.trim()).filter(u -> u.getAnonymizedAt() == null);
    }

    public Optional<User> findByUsernameWithUnit(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByUsernameIgnoreCaseWithUnit(username.trim());
    }

    /** Anmeldung mit Benutzername, login_email oder E-Mail aus verknüpften Personen-Stammdaten. */
    public Optional<User> findActiveForLogin(String login) {
        if (login == null || login.isBlank()) {
            return Optional.empty();
        }
        String trimmed = login.trim();
        Optional<User> byUsername = userRepository.findByUsernameIgnoreCaseWithUnit(trimmed);
        if (byUsername.isPresent()) {
            return byUsername.filter(this::isLoginAllowed);
        }
        String normalized = LoginIdentifierHelper.normalize(trimmed);
        Optional<User> byLoginEmail = userRepository.findByLoginEmailIgnoreCaseWithUnit(normalized);
        if (byLoginEmail.isPresent()) {
            return byLoginEmail.filter(this::isLoginAllowed);
        }
        return userRepository.findByPersonEmailIgnoreCaseWithUnit(normalized).filter(this::isLoginAllowed);
    }

    private boolean isLoginAllowed(User user) {
        return user.isActive() && user.getAnonymizedAt() == null;
    }

    public Optional<User> findByIdWithUnit(long id) {
        return userRepository.findByIdWithUnit(id);
    }

    public Optional<User> findActiveByRfidCardUid(String rawCardUid) {
        String normalized = RfidCardUidNormalizer.normalize(rawCardUid);
        if (!RfidCardUidNormalizer.isValid(normalized)) {
            return Optional.empty();
        }
        return rfidCardRepository.findActiveCardWithUser(normalized).map(UserRfidCard::getUser);
    }

    @Transactional
    public User ensureBootstrapAdmin(String username, String plainPassword, String displayName) {
        String normalizedUsername = username.trim();
        Optional<User> existing = userRepository.findByUsernameIgnoreCase(normalizedUsername);
        if (existing.isPresent()) {
            return existing.get();
        }
        User user = new User();
        user.setUsername(normalizedUsername);
        user.setDisplayName(displayName);
        user.setRole(UserRole.SUPER_ADMIN);
        user.setActive(true);
        user.setPasswordHash(passwordEncoder.encode(plainPassword));
        return userRepository.save(user);
    }

    @Transactional
    public void resetPassword(long userId, String plainPassword) {
        User user = userRepository.findById(userId).orElseThrow();
        if (user.getAnonymizedAt() != null) {
            throw new IllegalStateException("Konto wurde anonymisiert");
        }
        user.setPasswordHash(passwordEncoder.encode(plainPassword));
        user.setActive(true);
        userRepository.save(user);
    }

    @Transactional
    public void recordSuccessfulLogin(long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setLastLoginAt(Instant.now());
            userRepository.save(user);
        });
    }

    /**
     * DSGVO Art. 17: Konto anonymisieren (Protokolle behalten nur noch die technische Nutzer-ID).
     */
    @Transactional
    public void anonymizeUser(long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        user.setUsername("deleted-" + userId + "-" + UUID.randomUUID().toString().substring(0, 8));
        user.setDisplayName("Gelöschter Nutzer");
        user.setPasswordHash(null);
        user.setActive(false);
        user.setAnonymizedAt(Instant.now());
        rfidCardRepository.findByUserId(userId).forEach(card -> card.setActive(false));
        userRepository.save(user);
    }
}
