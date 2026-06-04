package de.feuerwehr.manager.web.dto;

import java.time.Instant;
import java.util.List;

public record UserDataExport(
        long id,
        String username,
        String displayName,
        String loginEmail,
        String role,
        String unitName,
        boolean active,
        Instant createdAt,
        Instant lastLoginAt,
        String privacyNoticeVersion,
        Instant privacyNoticeAcceptedAt,
        List<RfidCardExport> rfidCards,
        Instant exportedAt) {

    public record RfidCardExport(long id, String label, boolean active, String cardUidMasked) {}
}
