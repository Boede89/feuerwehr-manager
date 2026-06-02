package de.feuerwehr.manager.web;

import de.feuerwehr.manager.personal.Person;
import de.feuerwehr.manager.personal.PersonCourseCompletion;
import de.feuerwehr.manager.personal.PersonDiveraRic;
import de.feuerwehr.manager.personal.PersonStatus;
import de.feuerwehr.manager.personal.PersonalService;
import de.feuerwehr.manager.personal.PersonalService.CourseCompletionInput;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/personal")
@RequiredArgsConstructor
public class PersonalController {

    private final UnitService unitService;
    private final PersonalService personalService;

    @GetMapping
    public String index(@RequestParam(name = "unit", required = false) Long unitId, Model model) {
        Unit unit = resolveUnit(unitId, model);
        var persons = personalService.listPersons(unit.getId());
        model.addAttribute("persons", persons);
        model.addAttribute("personCount", persons.size());
        return "personal/index";
    }

    @GetMapping("/new")
    public String newForm(@RequestParam(name = "unit", required = false) Long unitId, Model model) {
        Unit unit = resolveUnit(unitId, model);
        personalService.seedDefaultQualificationsIfEmpty(unit.getId());
        return "personal/person-new";
    }

    @PostMapping("/new")
    public String create(
            @RequestParam long unit,
            @RequestParam String firstName,
            @RequestParam String lastName,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String phone,
            RedirectAttributes redirectAttributes) {
        try {
            Person created = personalService.createPerson(
                    unit, firstName, lastName, email, phone, null, null, null, null, null);
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", created.displayName() + " wurde angelegt.");
            return "redirect:/personal/" + created.getId() + "?unit=" + unit;
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            redirectAttributes.addFlashAttribute("firstName", firstName);
            redirectAttributes.addFlashAttribute("lastName", lastName);
            redirectAttributes.addFlashAttribute("email", email);
            redirectAttributes.addFlashAttribute("phone", phone);
            return "redirect:/personal/new?unit=" + unit;
        }
    }

    @GetMapping("/{id}")
    public String detail(
            @PathVariable long id,
            @RequestParam(name = "unit", required = false) Long unitId,
            @RequestParam(name = "tab", defaultValue = "stammdaten") String tab,
            @RequestParam(name = "edit", required = false) String edit,
            Model model) {
        Person person = personalService.requirePerson(id);
        Unit unit = person.getUnit();
        model.addAttribute("unitId", unit.getId());
        model.addAttribute("currentUnitName", unit.getName());
        model.addAttribute("person", person);
        String activeTab = normalizeTab(tab);
        model.addAttribute("activeTab", activeTab);
        model.addAttribute("editMode", edit != null && (edit.equals("1") || edit.equals(activeTab)));
        model.addAttribute("qualificationTypes", personalService.listQualificationTypes(unit.getId(), true));
        model.addAttribute("linkableUsers", personalService.listLinkableUsers());
        model.addAttribute("statuses", PersonStatus.values());
        List<PersonCourseCompletion> completions = personalService.listCompletions(id);
        model.addAttribute("completions", completions);
        model.addAttribute("unitCourses", personalService.listCourses(unit.getId(), true));
        Set<Long> completedCourseIds = completions.stream()
                .map(c -> c.getCourse().getId())
                .collect(Collectors.toCollection(HashSet::new));
        model.addAttribute("completedCourseIds", completedCourseIds);
        Map<Long, Integer> completionYears = new HashMap<>();
        for (PersonCourseCompletion c : completions) {
            completionYears.put(c.getCourse().getId(), c.getCompletionYear());
        }
        model.addAttribute("completionYears", completionYears);
        model.addAttribute("diveraRics", personalService.listDiveraRics(id));
        return "personal/person-detail";
    }

    @PostMapping("/{id}")
    public String update(
            @PathVariable long id,
            @RequestParam long unit,
            @RequestParam(defaultValue = "stammdaten") String section,
            @RequestParam(required = false) String firstName,
            @RequestParam(required = false) String lastName,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate birthdate,
            @RequestParam(required = false) Long qualificationTypeId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String diveraUcrId,
            @RequestParam(required = false) String notes,
            @RequestParam(required = false) PersonStatus status,
            @RequestParam(required = false) List<Long> courseIds,
            @RequestParam(required = false) List<String> ricCodes,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {
        String tab = normalizeTab(section);
        try {
            switch (tab) {
                case "lehrgaenge" -> personalService.updateLehrgaenge(
                        id, qualificationTypeId, parseCourseCompletions(courseIds, request));
                case "divera" -> personalService.updateDivera(id, diveraUcrId, ricCodes);
                default -> personalService.updateStammdaten(
                        id, firstName, lastName, email, phone, birthdate, userId, notes, status);
            }
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", "Gespeichert.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/personal/" + id + "?unit=" + unit + "&tab=" + tab + "&edit=1";
        }
        return "redirect:/personal/" + id + "?unit=" + unit + "&tab=" + tab;
    }

    @PostMapping("/{id}/delete")
    public String delete(
            @PathVariable long id, @RequestParam long unit, RedirectAttributes redirectAttributes) {
        try {
            personalService.anonymizePerson(id);
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", "Person wurde gelöscht.");
            return "redirect:/personal?unit=" + unit;
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/personal/" + id + "?unit=" + unit + "&tab=stammdaten";
        }
    }

    @GetMapping("/setup/qualifications")
    public String qualifications(@RequestParam(name = "unit", required = false) Long unitId, Model model) {
        Unit unit = resolveUnit(unitId, model);
        model.addAttribute("types", personalService.listQualificationTypes(unit.getId(), false));
        return "personal/qualifications";
    }

    @PostMapping("/setup/qualifications")
    public String createQualification(
            @RequestParam long unit, @RequestParam String name, RedirectAttributes redirectAttributes) {
        try {
            personalService.createQualificationType(unit, name);
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", "Qualifikation angelegt.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/personal/setup/qualifications?unit=" + unit;
    }

    @GetMapping("/setup/courses")
    public String courses(@RequestParam(name = "unit", required = false) Long unitId, Model model) {
        Unit unit = resolveUnit(unitId, model);
        model.addAttribute("courses", personalService.listCourses(unit.getId(), false));
        model.addAttribute("qualificationTypes", personalService.listQualificationTypes(unit.getId(), true));
        return "personal/courses";
    }

    @PostMapping("/setup/courses")
    public String createCourse(
            @RequestParam long unit,
            @RequestParam String name,
            @RequestParam(required = false) Long qualificationTypeId,
            RedirectAttributes redirectAttributes) {
        try {
            personalService.createCourse(unit, name, qualificationTypeId);
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", "Lehrgang angelegt.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/personal/setup/courses?unit=" + unit;
    }

    private static String normalizeTab(String tab) {
        if (tab == null) {
            return "stammdaten";
        }
        return switch (tab) {
            case "atemschutz", "lehrgaenge", "divera" -> tab;
            default -> "stammdaten";
        };
    }

    private static List<CourseCompletionInput> parseCourseCompletions(
            List<Long> courseIds, HttpServletRequest request) {
        List<CourseCompletionInput> inputs = new ArrayList<>();
        if (courseIds == null) {
            return inputs;
        }
        for (Long courseId : courseIds) {
            if (courseId == null || courseId <= 0) {
                continue;
            }
            Integer year = null;
            String yearParam = request.getParameter("completionYear_" + courseId);
            if (yearParam != null && !yearParam.isBlank()) {
                try {
                    year = Integer.parseInt(yearParam.trim());
                } catch (NumberFormatException ignored) {
                    // ungültiges Jahr ignorieren
                }
            }
            inputs.add(new CourseCompletionInput(courseId, year, null));
        }
        return inputs;
    }

    private Unit resolveUnit(Long unitId, Model model) {
        Optional<Unit> unit = unitService.resolveActiveUnit(unitId);
        if (unit.isEmpty()) {
            throw new IllegalStateException("Keine aktive Einheit");
        }
        Unit resolved = unit.get();
        model.addAttribute("unitId", resolved.getId());
        model.addAttribute("currentUnitName", resolved.getName());
        return resolved;
    }
}
