package de.feuerwehr.manager.web.dto;

import de.feuerwehr.manager.dsgvo.AuditEventType;
import java.time.Instant;

public record AuditLogRow(Instant occurredAt, String actorLabel, String actionLabel, String detail) {}
