package de.feuerwehr.manager.user;

import de.feuerwehr.manager.dsgvo.AuditEventType;
import de.feuerwehr.manager.dsgvo.AuditService;
import de.feuerwehr.manager.security.TotpSecretCodec;
import de.feuerwehr.manager.security.TotpService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserTotpService {

    private final UserRepository userRepository;
    private final TotpService totpService;
    private final TotpSecretCodec totpSecretCodec;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public boolean isTotpEnabled(long userId) {
        return userRepository.findById(userId).map(User::isTotpEnabled).orElse(false);
    }

    @Transactional
    public String beginSetup(long userId, HttpSession session) {
        User user = requireActiveUser(userId);
        if (user.isTotpEnabled()) {
            throw new IllegalArgumentException("2FA ist bereits aktiviert.");
        }
        String secret = totpService.generateSecret();
        String uri = totpService.buildOtpAuthUri(secret, user.getUsername());
        user.setTotpSecret(totpSecretCodec.encode(secret));
        user.setTotpEnabled(false);
        userRepository.save(user);
        session.setAttribute(de.feuerwehr.manager.security.TotpSessionKeys.SETUP_OTPAUTH_URI, uri);
        return uri;
    }

    @Transactional
    public void confirmSetup(long userId, String code, HttpServletRequest request, HttpSession session) {
        User user = requireActiveUser(userId);
        String secret = requirePendingSecret(user);
        if (!totpService.verifyCode(secret, code)) {
            throw new IllegalArgumentException("Ungültiger Authenticator-Code.");
        }
        user.setTotpEnabled(true);
        userRepository.save(user);
        session.removeAttribute(de.feuerwehr.manager.security.TotpSessionKeys.SETUP_OTPAUTH_URI);
        auditService.record(AuditEventType.USER_UPDATED, userId, userId, request, "2FA aktiviert");
    }

    @Transactional
    public void cancelSetup(long userId, HttpSession session) {
        User user = requireActiveUser(userId);
        user.setTotpSecret(null);
        user.setTotpEnabled(false);
        userRepository.save(user);
        session.removeAttribute(de.feuerwehr.manager.security.TotpSessionKeys.SETUP_OTPAUTH_URI);
    }

    @Transactional
    public void disable(long userId, String code, HttpServletRequest request) {
        User user = requireActiveUser(userId);
        if (!user.isTotpEnabled()) {
            throw new IllegalArgumentException("2FA ist nicht aktiv.");
        }
        String secret = requireStoredSecret(user);
        if (!totpService.verifyCode(secret, code)) {
            throw new IllegalArgumentException("Ungültiger Authenticator-Code.");
        }
        user.setTotpEnabled(false);
        user.setTotpSecret(null);
        userRepository.save(user);
        auditService.record(AuditEventType.USER_UPDATED, userId, userId, request, "2FA deaktiviert");
    }

    @Transactional
    public boolean verifyLoginCode(long userId, String code) {
        User user = requireActiveUser(userId);
        if (!user.isTotpEnabled()) {
            return true;
        }
        return totpService.verifyCode(requireStoredSecret(user), code);
    }

    @Transactional(readOnly = true)
    public byte[] qrImageForSession(long userId, HttpSession session) {
        Object uri = session.getAttribute(de.feuerwehr.manager.security.TotpSessionKeys.SETUP_OTPAUTH_URI);
        if (uri == null || uri.toString().isBlank()) {
            throw new IllegalArgumentException("Kein aktives 2FA-Setup.");
        }
        User user = requireActiveUser(userId);
        return totpService.generateQrPng(requirePendingSecret(user), user.getUsername());
    }

    @Transactional
    public void resetByAdmin(long targetUserId, long actorUserId, HttpServletRequest request) {
        if (targetUserId == actorUserId) {
            throw new IllegalArgumentException("Eigene 2FA bitte unter Einstellungen deaktivieren.");
        }
        User user = requireActiveUser(targetUserId);
        user.setTotpEnabled(false);
        user.setTotpSecret(null);
        userRepository.save(user);
        auditService.record(AuditEventType.USER_UPDATED, actorUserId, targetUserId, request, "2FA durch Admin zurückgesetzt");
    }

    private User requireActiveUser(long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("Benutzer nicht gefunden"));
        if (user.getAnonymizedAt() != null) {
            throw new IllegalArgumentException("Konto wurde gelöscht");
        }
        return user;
    }

    private String requirePendingSecret(User user) {
        if (user.getTotpSecret() == null) {
            throw new IllegalArgumentException("Bitte zuerst „2FA einrichten“ starten.");
        }
        return totpSecretCodec.decode(user.getTotpSecret());
    }

    private String requireStoredSecret(User user) {
        if (user.getTotpSecret() == null) {
            throw new IllegalArgumentException("Kein 2FA-Secret hinterlegt.");
        }
        return totpSecretCodec.decode(user.getTotpSecret());
    }

}
