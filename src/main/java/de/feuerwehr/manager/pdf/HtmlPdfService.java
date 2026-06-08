package de.feuerwehr.manager.pdf;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

@Service
@RequiredArgsConstructor
public class HtmlPdfService {

    private final SpringTemplateEngine templateEngine;

    public byte[] renderPdf(String templateName, Model model) {
        return renderPdf(templateName, model.asMap());
    }

    public byte[] renderPdf(String templateName, Map<String, Object> variables) {
        Context context = new Context(Locale.GERMANY);
        context.setVariables(variables);
        String html = templateEngine.process(templateName, context);
        return htmlToPdf(html);
    }

    private static byte[] htmlToPdf(String html) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, "/");
            builder.useDefaultPageSize(210, 297, PdfRendererBuilder.PageSizeUnits.MM);
            builder.toStream(output);
            builder.run();
            return output.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("PDF konnte nicht erzeugt werden: " + e.getMessage(), e);
        }
    }
}
