package de.feuerwehr.manager.personal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.feuerwehr.manager.pdf.HtmlPdfService;
import de.feuerwehr.manager.personal.CourseDetailPlanService.CourseItemView;
import de.feuerwehr.manager.personal.CourseDetailPlanService.DetailPlanView;
import de.feuerwehr.manager.personal.CourseDetailPlanService.EntryView;
import de.feuerwehr.manager.unit.Unit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

class CourseDetailPlanPdfTemplateTest {

    @Test
    void rendersDetailPlanPdf() {
        Unit unit = new Unit();
        unit.setId(1L);
        unit.setName("Freiwillige Feuerwehr Test");

        Course course = new Course();
        course.setId(4L);
        course.setName("Atemschutzgeräteträger & Trupp <A>");

        Person person = new Person();
        person.setId(9L);
        person.setFirstName("Jörg");
        person.setLastName("Müller");

        EntryView seat = new EntryView(
                1L, 1, person, true, true, false, List.of("Truppmann"), "Truppmann fehlt", "80 %", 80);
        EntryView wait = new EntryView(
                2L, 2, person, false, false, true, List.of(), "—", "—", 0);
        CourseItemView item = new CourseItemView(3L, course, 1, List.of(seat, wait), 1);

        CourseDetailPlan plan = new CourseDetailPlan();
        plan.setUseParticipation(true);
        plan.setPlanYear(2027);

        DetailPlanView view = new DetailPlanView(
                unit, 2027, 2026, plan, List.of(course), List.of(item), Set.of(4L), Map.of(4L, 1));

        HtmlPdfService pdfService = new HtmlPdfService(templateEngine());
        byte[] pdf = pdfService.renderPdf(
                "personal/lehrgangsplanung-detail-druck",
                Map.of(
                        "printTitle", "Lehrgangsplanung 2027",
                        "printSubtitle", "Freiwillige Feuerwehr Test · Stand: 07.10.2026 17:40 Uhr",
                        "planView", view,
                        "planYear", 2027));

        assertTrue(pdf.length > 100);
        assertTrue(new String(pdf, 0, 5).startsWith("%PDF"));
    }

    @Test
    void rendersEmptyPlanWithoutParticipation() {
        Unit unit = new Unit();
        unit.setName("FF");
        CourseDetailPlan plan = new CourseDetailPlan();
        plan.setUseParticipation(false);
        DetailPlanView view = new DetailPlanView(
                unit, 2027, 2026, plan, List.of(), List.of(), Set.of(), Map.of());

        HtmlPdfService pdfService = new HtmlPdfService(templateEngine());
        byte[] pdf = pdfService.renderPdf(
                "personal/lehrgangsplanung-detail-druck",
                Map.of(
                        "printTitle", "Lehrgangsplanung 2027",
                        "printSubtitle", "FF",
                        "planView", view,
                        "planYear", 2027));
        assertFalse(pdf.length == 0);
    }

    private static SpringTemplateEngine templateEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false);
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }
}
