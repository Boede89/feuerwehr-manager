package de.feuerwehr.manager.berichte;

import java.time.Instant;

public record IncidentAttachmentDto(long id, String filename, String mimeType, long fileSize, Instant createdAt) {

    public static IncidentAttachmentDto from(IncidentReportAttachment attachment) {
        return new IncidentAttachmentDto(
                attachment.getId(),
                attachment.getFilename(),
                attachment.getMimeType(),
                attachment.getFileSize(),
                attachment.getCreatedAt());
    }
}
