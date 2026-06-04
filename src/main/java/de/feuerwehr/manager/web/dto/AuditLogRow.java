package de.feuerwehr.manager.web.dto;

import java.time.Instant;

/** actionHtml enthält Icon-SVG und Label (HTML-escaped, für th:utext). */
public record AuditLogRow(Instant occurredAt, String actorLabel, String actionHtml, String detail) {}
