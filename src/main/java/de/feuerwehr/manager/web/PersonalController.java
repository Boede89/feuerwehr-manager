package de.feuerwehr.manager.web;

import de.feuerwehr.manager.personal.Person;
import de.feuerwehr.manager.personal.PersonStatus;
import de.feuerwehr.manager.personal.PersonalService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitService;
import java.time.LocalDate;
import java.util.Optional;
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
        personalService.seedDefaultQualificationsIfEmpty(unit.getId());
        var persons = personalService.listPersons(unit.getId());
        model.addAttribute("persons", persons);
        model.addAttribute("personCount", persons.size());
        return "personal/index";
    }

    @PostMapping
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
            return "redirect:/personal?unit=" + unit;
        }
    }

    @GetMapping("/{id}")
    public String editForm(
            @PathVariable long id,
            @RequestParam(name = "unit", required = false) Long unitId,
            Model model) {
        Person person = personalService.requirePerson(id);
        Unit unit = person.getUnit();
        model.addAttribute("unitId", unit.getId());
        model.addAttribute("units", unitService.findActiveOrdered());
        model.addAttribute("currentUnitName", unit.getName());
        model.addAttribute("person", person);
        model.addAttribute("qualificationTypes", personalService.listQualificationTypes(unit.getId(), true));
        model.addAttribute("linkableUsers", personalService.listLinkableUsers());
        model.addAttribute("statuses", PersonStatus.values());
        model.addAttribute("completions", personalService.listCompletions(id));
        return "personal/person-edit";
    }

    @PostMapping("/{id}")
    public String update(
            @PathVariable long id,
            @RequestParam long unit,
            @RequestParam String firstName,
            @RequestParam String lastName,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate birthdate,
            @RequestParam(required = false) Long qualificationTypeId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String diveraUcrId,
            @RequestParam(required = false) String notes,
            @RequestParam PersonStatus status,
            RedirectAttributes redirectAttributes) {
        try {
            personalService.updatePerson(
                    id,
                    firstName,
                    lastName,
                    email,
                    phone,
                    birthdate,
                    qualificationTypeId,
                    userId,
                    diveraUcrId,
                    notes,
                    status);
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", "Gespeichert.");
            return "redirect:/personal/" + id + "?unit=" + unit;
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/personal/" + id + "?unit=" + unit;
        }
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
            return "redirect:/personal/" + id + "?unit=" + unit;
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

    private Unit resolveUnit(Long unitId, Model model) {
        Optional<Unit> unit = unitService.resolveActiveUnit(unitId);
        if (unit.isEmpty()) {
            throw new IllegalStateException("Keine aktive Einheit");
        }
        Unit resolved = unit.get();
        model.addAttribute("units", unitService.findActiveOrdered());
        model.addAttribute("unitId", resolved.getId());
        model.addAttribute("currentUnitName", resolved.getName());
        return resolved;
    }
}
