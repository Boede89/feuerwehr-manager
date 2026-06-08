package de.feuerwehr.manager.pdf;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

public final class PdfDownloadResponse {

    private PdfDownloadResponse() {}

    public static ResponseEntity<byte[]> attachment(String filename, byte[] pdfBytes) {
        String safeName = filename != null && !filename.isBlank() ? filename : "dokument.pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + safeName + "\"")
                .body(pdfBytes);
    }
}
