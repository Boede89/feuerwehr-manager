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
        return userRepository.findByUsernameIgnoreCase(username.trim());
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
        user.setRole(UserRole.ADMIN);
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
