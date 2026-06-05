package de.feuerwehr.manager.web;

import de.feuerwehr.manager.personal.AttendanceServiceType;
import de.feuerwehr.manager.personal.AttendanceStatus;
import de.feuerwehr.manager.personal.Course;
import de.feuerwehr.manager.personal.EquipmentType;
import de.feuerwehr.manager.personal.Person;
import de.feuerwehr.manager.personal.PersonCourseCompletion;
import de.feuerwehr.manager.personal.PersonStatus;
import de.feuerwehr.manager.personal.PersonalGroupService;
import de.feuerwehr.manager.personal.PersonalMemberService;
import de.feuerwehr.manager.personal.PersonalService;
import de.feuerwehr.manager.personal.PersonGroup;
import de.feuerwehr.manager.personal.PersonalService.CourseCompletionInput;
import de.feuerwehr.manager.personal.PersonalService.PersonCreateResult;
import de.feuerwehr.manager.personal.PersonalService.PersonDetailView;
import de.feuerwehr.manager.personal.PersonalService.StammdatenUpdateResult;
import de.feuerwehr.manager.security.AccessControlService;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    private final PersonalMemberService personalMemberService;
    private final PersonalGroupService personalGroupService;
    private final AccessControlService accessControlService;

    @GetMapping
    public String index(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            @RequestParam(name = "tab", defaultValue = "mitglieder") String tab,
            Model model) {
        Unit unit = resolveUnit(unitId, actor, model);
        String personalTab = normalizePersonalTab(tab);
        model.addAttribute("personalTab", personalTab);
        var persons = personalService.listPersons(unit.getId());
        model.addAttribute("persons", persons);
        model.addAttribute("personCount", persons.size());
        if ("gruppen".equals(personalTab)) {
            List<PersonGroup> groups = personalGroupService.listGroups(unit.getId());
            model.addAttribute("groups", groups);
            model.addAttribute("groupCount", groups.size());
        }
        return "personal/index";
    }

    @PostMapping("/groups")
    public String createGroup(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam String name,
            @RequestParam(name = "personIds", required = false) List<Long> personIds,
            RedirectAttributes redirectAttributes) {
        try {
            accessControlService.requireUnitAccess(actor, unit);
            personalGroupService.createGroup(unit, name, personIds);
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", "Gruppe wurde angelegt.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/personal?unit=" + unit + "&tab=gruppen";
    }

    @PostMapping("/groups/{groupId}")
    public String updateGroup(
            @AuthenticationPrincipal AppUserDetails actor,
            @PathVariable long groupId,
            @RequestParam long unit,
            @RequestParam String name,
            @RequestParam(name = "personIds", required = false) List<Long> personIds,
            RedirectAttributes redirectAttributes) {
        try {
            accessControlService.requireUnitAccess(actor, unit);
            personalGroupService.updateGroup(groupId, name, personIds);
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", "Gruppe wurde gespeichert.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/personal?unit=" + unit + "&tab=gruppen";
    }

    @PostMapping("/groups/{groupId}/delete")
    public String deleteGroup(
            @AuthenticationPrincipal AppUserDetails actor,
            @PathVariable long groupId,
            @RequestParam long unit,
            RedirectAttributes redirectAttributes) {
        try {
            accessControlService.requireUnitAccess(actor, unit);
            personalGroupService.deleteGroup(groupId);
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", "Gruppe wurde gelöscht.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/personal?unit=" + unit + "&tab=gruppen";
    }

    @GetMapping("/new")
    public String newForm(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            @RequestParam(name = "tab", defaultValue = "stammdaten") String tab,
            Model model) {
        Unit unit = resolveUnit(unitId, actor, model);
        personalService.seedDefaultQualificationsIfEmpty(unit.getId());
        populateNewPersonModel(model, unit, normalizeTab(tab));
        return "personal/person-detail";
    }

    @PostMapping("/new")
    public String create(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam String firstName,
            @RequestParam String lastName,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate birthdate,
            @RequestParam(required = false) String personnelNumber,
            @RequestParam(required = false, defaultValue = "false") boolean allowLogin,
            @RequestParam(required = false, defaultValue = "manual") String passwordDelivery,
            @RequestParam(required = false) String initialPassword,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {
        try {
            if (allowLogin) {
                accessControlService.requireAdminLevel(actor);
                if (email == null || email.isBlank()) {
                    throw new IllegalArgumentException(
                            "Für „Login erlauben“ ist eine E-Mail-Adresse erforderlich.");
                }
            }
            PersonCreateResult result = personalService.createPersonComplete(
                    unit,
                    firstName,
                    lastName,
                    email,
                    phone,
                    birthdate,
                    allowLogin,
                    passwordDelivery,
                    initialPassword,
                    null,
                    PersonStatus.ACTIVE,
                    null,
                    List.of(),
                    null,
                    List.of(),
                    actor.getUserId(),
                    request);
            if (personnelNumber != null && !personnelNumber.isBlank()) {
                personalMemberService.updateFwHubStammdaten(
                        result.person().getId(),
                        firstName,
                        lastName,
                        birthdate,
                        personnelNumber.trim(),
                        null,
                        null,
                        null);
            }
            Person created = result.person();
            redirectAttributes.addFlashAttribute("saved", true);
            String message = created.displayName() + " wurde angelegt.";
            if (result.createdUsername() != null) {
                message = appendLoginCreatedNotice(
                        message, result.createdUsername(), email, result.initialPassword(), result.mailNotice());
            }
            redirectAttributes.addFlashAttribute("message", message);
            return "redirect:/personal/" + created.getId() + "?unit=" + unit + "&tab=stammdaten";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            storeNewPersonFlash(
                    redirectAttributes, firstName, lastName, email, phone, birthdate, allowLogin, passwordDelivery);
            return "redirect:/personal/new?unit=" + unit;
        }
    }

    @GetMapping("/{id}")
    public String detail(
            @AuthenticationPrincipal AppUserDetails actor,
            @PathVariable long id,
            @RequestParam(name = "unit", required = false) Long unitId,
            @RequestParam(name = "tab", defaultValue = "stammdaten") String tab,
            Model model) {
        Person person = personalService.requirePerson(id);
        accessControlService.requireUnitAccess(actor, person.getUnit().getId());
        Unit unit = person.getUnit();
        model.addAttribute("unitId", unit.getId());
        model.addAttribute("currentUnitName", unit.getName());
        model.addAttribute("isNewPerson", false);
        String activeTab = normalizeTab(tab);
        populateMemberDetailData(model, actor, id, activeTab);
        return "personal/person-detail";
    }

    @PostMapping("/{id}")
    public String update(
            @AuthenticationPrincipal AppUserDetails actor,
            @PathVariable long id,
            @RequestParam long unit,
            @RequestParam(defaultValue = "stammdaten") String section,
            @RequestParam(required = false) String firstName,
            @RequestParam(required = false) String lastName,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate birthdate,
            @RequestParam(required = false) Long qualificationTypeId,
            @RequestParam(required = false, defaultValue = "false") boolean allowLogin,
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
                case "divera", "schnittstellen" ->
                        personalService.updateDivera(id, diveraUcrId, ricCodes);
                default -> {
                    StammdatenUpdateResult result = personalService.updateStammdaten(
                            id,
                            firstName,
                            lastName,
                            email,
                            phone,
                            birthdate,
                            allowLogin,
                            notes,
                            status,
                            actor.getUserId(),
                            request);
                    String message = "Gespeichert.";
                    if (result.createdUsername() != null) {
                        message = appendLoginCreatedNotice(
                                message, result.createdUsername(), email, result.initialPassword(), result.mailNotice());
                    }
                    redirectAttributes.addFlashAttribute("message", message);
                }
            }
            redirectAttributes.addFlashAttribute("saved", true);
            if (!redirectAttributes.getFlashAttributes().containsKey("message")) {
                redirectAttributes.addFlashAttribute("message", "Gespeichert.");
            }
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/personal/" + id + "?unit=" + unit + "&tab=" + tab + "&edit=1";
        }
        return "redirect:/personal/" + id + "?unit=" + unit + "&tab=" + tab;
    }

    @PostMapping("/{id}/login-access")
    public String updateLoginAccess(
            @AuthenticationPrincipal AppUserDetails actor,
            @PathVariable long id,
            @RequestParam long unit,
            @RequestParam(required = false, defaultValue = "false") boolean allowLogin,
            @RequestParam(required = false, defaultValue = "manual") String passwordDelivery,
            @RequestParam(required = false) String initialPassword,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {
        try {
            Person person = personalService.requirePerson(id);
            accessControlService.requireUnitAccess(actor, person.getUnit().getId());
            accessControlService.requireAdminLevel(actor);
            StammdatenUpdateResult result = personalMemberService.updateLoginAccess(
                    id, allowLogin, passwordDelivery, initialPassword, actor, request);
            redirectAttributes.addFlashAttribute("saved", true);
            if (result.createdUsername() != null) {
                String email = personalMemberService.resolvePersonEmail(result.person());
                redirectAttributes.addFlashAttribute(
                        "message",
                        appendLoginCreatedNotice(
                                "Benutzerkonto angelegt.",
                                result.createdUsername(),
                                email,
                                result.initialPassword(),
                                result.mailNotice()));
            } else if (allowLogin) {
                redirectAttributes.addFlashAttribute("message", "Systemzugang ist aktiv.");
            } else {
                redirectAttributes.addFlashAttribute("message", "Benutzerkonto wurde gelöscht.");
            }
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/personal/" + id + "?unit=" + unit + "&tab=stammdaten";
    }

    @PostMapping("/{id}/fw-stammdaten")
    public String updateFwStammdaten(
            @AuthenticationPrincipal AppUserDetails actor,
            @PathVariable long id,
            @RequestParam long unit,
            @RequestParam String firstName,
            @RequestParam String lastName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate birthdate,
            @RequestParam(required = false) String personnelNumber,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate entryDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate exitDate,
            @RequestParam(required = false) String notes,
            RedirectAttributes redirectAttributes) {
        return memberAction(actor, id, unit, "stammdaten", redirectAttributes, () -> personalMemberService.updateFwHubStammdaten(
                id, firstName, lastName, birthdate, personnelNumber, entryDate, exitDate, notes));
    }

    @PostMapping("/{id}/course-completions")
    public String createCourseCompletion(
            @AuthenticationPrincipal AppUserDetails actor,
            @PathVariable long id,
            @RequestParam long unit,
            @RequestParam long courseId,
            @RequestParam(required = false) Integer completionYear,
            RedirectAttributes redirectAttributes) {
        return memberAction(actor, id, unit, "lehrgaenge", redirectAttributes, () ->
                personalMemberService.addCourseCompletion(id, courseId, completionYear));
    }

    @PostMapping("/{id}/course-completions/{cid}")
    public String updateCourseCompletion(
            @AuthenticationPrincipal AppUserDetails actor,
            @PathVariable long id,
            @PathVariable long cid,
            @RequestParam long unit,
            @RequestParam long courseId,
            @RequestParam(required = false) Integer completionYear,
            RedirectAttributes redirectAttributes) {
        return memberAction(actor, id, unit, "lehrgaenge", redirectAttributes, () ->
                personalMemberService.updateCourseCompletion(id, cid, courseId, completionYear));
    }

    @PostMapping("/{id}/course-completions/{cid}/delete")
    public String deleteCourseCompletion(
            @AuthenticationPrincipal AppUserDetails actor,
            @PathVariable long id,
            @PathVariable long cid,
            @RequestParam long unit,
            RedirectAttributes redirectAttributes) {
        return memberAction(actor, id, unit, "lehrgaenge", redirectAttributes, () ->
                personalMemberService.deleteCourseCompletion(id, cid));
    }

    @PostMapping("/{id}/contact-data")
    public String updateContactData(
            @AuthenticationPrincipal AppUserDetails actor,
            @PathVariable long id,
            @RequestParam long unit,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String address,
            RedirectAttributes redirectAttributes) {
        return memberAction(actor, id, unit, "stammdaten", redirectAttributes, () -> personalMemberService.updateContactData(
                id, phone, email, address, actor.getUserId(), actor.getDisplayName()));
    }

    @PostMapping("/{id}/qualifications")
    public String createQualification(
            @AuthenticationPrincipal AppUserDetails actor,
            @PathVariable long id,
            @RequestParam long unit,
            @RequestParam String name,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate acquiredAt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiresAt,
            @RequestParam(required = false) String notes,
            @RequestParam(required = false, defaultValue = "false") boolean healthData,
            RedirectAttributes redirectAttributes) {
        return memberAction(actor, id, unit, "qualifikationen", redirectAttributes, () ->
                personalMemberService.createQualification(id, name, acquiredAt, expiresAt, notes, healthData));
    }

    @PostMapping("/{id}/qualifications/{qid}")
    public String updateQualification(
            @AuthenticationPrincipal AppUserDetails actor,
            @PathVariable long id,
            @PathVariable long qid,
            @RequestParam long unit,
            @RequestParam String name,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate acquiredAt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiresAt,
            @RequestParam(required = false) String notes,
            @RequestParam(required = false, defaultValue = "false") boolean healthData,
            RedirectAttributes redirectAttributes) {
        return memberAction(actor, id, unit, "qualifikationen", redirectAttributes, () ->
                personalMemberService.updateQualification(id, qid, name, acquiredAt, expiresAt, notes, healthData));
    }

    @PostMapping("/{id}/qualifications/{qid}/delete")
    public String deleteQualification(
            @AuthenticationPrincipal AppUserDetails actor,
            @PathVariable long id,
            @PathVariable long qid,
            @RequestParam long unit,
            RedirectAttributes redirectAttributes) {
        return memberAction(actor, id, unit, "qualifikationen", redirectAttributes, () ->
                personalMemberService.deleteQualification(id, qid));
    }

    @PostMapping("/{id}/equipment")
    public String createEquipment(
            @AuthenticationPrincipal AppUserDetails actor,
            @PathVariable long id,
            @RequestParam long unit,
            @RequestParam EquipmentType type,
            @RequestParam(required = false) String identifier,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate issuedAt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiresAt,
            @RequestParam(required = false) String notes,
            RedirectAttributes redirectAttributes) {
        return memberAction(actor, id, unit, "ausruestung", redirectAttributes, () ->
                personalMemberService.createEquipment(id, type, identifier, issuedAt, expiresAt, notes));
    }

    @PostMapping("/{id}/equipment/{eid}")
    public String updateEquipment(
            @AuthenticationPrincipal AppUserDetails actor,
            @PathVariable long id,
            @PathVariable long eid,
            @RequestParam long unit,
            @RequestParam EquipmentType type,
            @RequestParam(required = false) String identifier,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate issuedAt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiresAt,
            @RequestParam(required = false) String notes,
            RedirectAttributes redirectAttributes) {
        return memberAction(actor, id, unit, "ausruestung", redirectAttributes, () ->
                personalMemberService.updateEquipment(id, eid, type, identifier, issuedAt, expiresAt, notes));
    }

    @PostMapping("/{id}/equipment/{eid}/delete")
    public String deleteEquipment(
            @AuthenticationPrincipal AppUserDetails actor,
            @PathVariable long id,
            @PathVariable long eid,
            @RequestParam long unit,
            RedirectAttributes redirectAttributes) {
        return memberAction(actor, id, unit, "ausruestung", redirectAttributes, () ->
                personalMemberService.deleteEquipment(id, eid));
    }

    @PostMapping("/{id}/honors")
    public String createHonor(
            @AuthenticationPrincipal AppUserDetails actor,
            @PathVariable long id,
            @RequestParam long unit,
            @RequestParam String name,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate awardedAt,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String notes,
            RedirectAttributes redirectAttributes) {
        return memberAction(actor, id, unit, "ehrungen", redirectAttributes, () ->
                personalMemberService.createHonor(id, name, awardedAt, status, notes));
    }

    @PostMapping("/{id}/honors/{hid}")
    public String updateHonor(
            @AuthenticationPrincipal AppUserDetails actor,
            @PathVariable long id,
            @PathVariable long hid,
            @RequestParam long unit,
            @RequestParam String name,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate awardedAt,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String notes,
            RedirectAttributes redirectAttributes) {
        return memberAction(actor, id, unit, "ehrungen", redirectAttributes, () ->
                personalMemberService.updateHonor(id, hid, name, awardedAt, status, notes));
    }

    @PostMapping("/{id}/honors/{hid}/delete")
    public String deleteHonor(
            @AuthenticationPrincipal AppUserDetails actor,
            @PathVariable long id,
            @PathVariable long hid,
            @RequestParam long unit,
            RedirectAttributes redirectAttributes) {
        return memberAction(actor, id, unit, "ehrungen", redirectAttributes, () ->
                personalMemberService.deleteHonor(id, hid));
    }

    @PostMapping("/{id}/attendance")
    public String createAttendance(
            @AuthenticationPrincipal AppUserDetails actor,
            @PathVariable long id,
            @RequestParam long unit,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate serviceDate,
            @RequestParam AttendanceServiceType serviceType,
            @RequestParam(required = false) String serviceLabel,
            @RequestParam(required = false) AttendanceStatus status,
            @RequestParam(required = false) String notes,
            RedirectAttributes redirectAttributes) {
        return memberAction(actor, id, unit, "anwesenheit", redirectAttributes, () ->
                personalMemberService.createAttendance(
                        id, serviceDate, serviceType, serviceLabel, status, notes, actor.getUserId()));
    }

    @PostMapping("/{id}/attendance/{aid}")
    public String updateAttendance(
            @AuthenticationPrincipal AppUserDetails actor,
            @PathVariable long id,
            @PathVariable long aid,
            @RequestParam long unit,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate serviceDate,
            @RequestParam AttendanceServiceType serviceType,
            @RequestParam(required = false) String serviceLabel,
            @RequestParam(required = false) AttendanceStatus status,
            @RequestParam(required = false) String notes,
            RedirectAttributes redirectAttributes) {
        return memberAction(actor, id, unit, "anwesenheit", redirectAttributes, () ->
                personalMemberService.updateAttendance(
                        id, aid, serviceDate, serviceType, serviceLabel, status, notes));
    }

    @PostMapping("/{id}/schnittstellen/divera")
    public String updateDiveraUcr(
            @AuthenticationPrincipal AppUserDetails actor,
            @PathVariable long id,
            @RequestParam long unit,
            @RequestParam(required = false) String diveraUcrId,
            RedirectAttributes redirectAttributes) {
        return memberAction(actor, id, unit, "schnittstellen", redirectAttributes, () ->
                personalService.updateDiveraUcrId(id, diveraUcrId));
    }

    @PostMapping("/{id}/rics")
    public String addRic(
            @AuthenticationPrincipal AppUserDetails actor,
            @PathVariable long id,
            @RequestParam long unit,
            @RequestParam String ricCode,
            RedirectAttributes redirectAttributes) {
        return memberAction(actor, id, unit, "schnittstellen", redirectAttributes, () ->
                personalService.addDiveraRic(id, ricCode));
    }

    @PostMapping("/{id}/rics/{ricId}/delete")
    public String deleteRic(
            @AuthenticationPrincipal AppUserDetails actor,
            @PathVariable long id,
            @PathVariable long ricId,
            @RequestParam long unit,
            RedirectAttributes redirectAttributes) {
        return memberAction(actor, id, unit, "schnittstellen", redirectAttributes, () ->
                personalService.deleteDiveraRic(id, ricId));
    }

    @PostMapping("/{id}/attendance/{aid}/delete")
    public String deleteAttendance(
            @AuthenticationPrincipal AppUserDetails actor,
            @PathVariable long id,
            @PathVariable long aid,
            @RequestParam long unit,
            RedirectAttributes redirectAttributes) {
        return memberAction(actor, id, unit, "anwesenheit", redirectAttributes, () ->
                personalMemberService.deleteAttendance(id, aid));
    }

    @GetMapping("/{id}/attendance/export")
    public ResponseEntity<byte[]> exportAttendance(
            @AuthenticationPrincipal AppUserDetails actor, @PathVariable long id, @RequestParam long unit) {
        Person person = personalService.requirePerson(id);
        accessControlService.requireUnitAccess(actor, person.getUnit().getId());
        byte[] body = personalMemberService.exportAttendanceCsv(id);
        String filename = "anwesenheit_" + person.getLastName() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }

    @PostMapping("/{id}/delete")
    public String delete(
            @AuthenticationPrincipal AppUserDetails actor,
            @PathVariable long id,
            @RequestParam long unit,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {
        try {
            Person person = personalService.requirePerson(id);
            accessControlService.requireUnitAccess(actor, person.getUnit().getId());
            accessControlService.requireAdminLevel(actor);
            personalMemberService.deletePerson(id, actor, request);
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", "Person wurde gelöscht.");
            return "redirect:/personal?unit=" + unit;
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/personal/" + id + "?unit=" + unit + "&tab=stammdaten";
        }
    }

    @GetMapping("/setup/qualifications")
    public String qualifications(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            Model model) {
        Unit unit = resolveUnit(unitId, actor, model);
        model.addAttribute("types", personalService.listQualificationTypes(unit.getId(), false));
        return "personal/qualifications";
    }

    @PostMapping("/setup/qualifications")
    public String createQualification(
            @RequestParam long unit, @RequestParam String name, RedirectAttributes redirectAttributes) {
        try {
            personalService.createQualificationType(unit, name, null);
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", "Qualifikation angelegt.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/personal/setup/qualifications?unit=" + unit;
    }

    @GetMapping("/setup/courses")
    public String courses(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            Model model) {
        Unit unit = resolveUnit(unitId, actor, model);
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

    private void populateNewPersonModel(Model model, Unit unit, String activeTab) {
        Person person = new Person();
        person.setUnit(unit);
        person.setStatus(PersonStatus.ACTIVE);
        model.addAttribute("isNewPerson", true);
        model.addAttribute("person", person);
        model.addAttribute("activeTab", activeTab);
    }

    private String memberAction(
            AppUserDetails actor,
            long personId,
            long unitId,
            String tab,
            RedirectAttributes redirectAttributes,
            Runnable action) {
        try {
            Person person = personalService.requirePerson(personId);
            accessControlService.requireUnitAccess(actor, person.getUnit().getId());
            action.run();
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", "Gespeichert.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/personal/" + personId + "?unit=" + unitId + "&tab=" + tab;
    }

    private void populateMemberDetailData(Model model, AppUserDetails actor, long personId, String activeTab) {
        PersonDetailView detail = personalService.loadPersonDetailView(personId);
        Person person = detail.person();
        model.addAttribute("person", person);
        model.addAttribute("canDeletePerson", accessControlService.canDeletePerson(actor, person));
        model.addAttribute("personDisplayName", person.displayName());
        model.addAttribute("personInitials", personInitials(person));
        model.addAttribute("activeTab", activeTab);
        model.addAttribute("emergencyContacts", personalMemberService.listEmergencyContacts(personId));
        model.addAttribute("personEmail", personalMemberService.resolvePersonEmail(person));
        model.addAttribute("attendanceDisplay", personalMemberService.displayAttendanceStats(personId));
        model.addAttribute("attendanceRecords", personalMemberService.listAttendance(personId));
        model.addAttribute("attendanceServiceTypes", AttendanceServiceType.values());
        populatePersonDetailData(model, person.getUnit().getId(), detail);
    }

    private void populatePersonDetailData(Model model, long unitId, PersonDetailView detail) {
        model.addAttribute("qualificationTypes", personalService.listQualificationTypes(unitId, true));
        model.addAttribute("statuses", PersonStatus.values());
        model.addAttribute("unitCourses", personalService.listCourses(unitId, true));
        if (detail == null) {
            model.addAttribute("completions", List.of());
            model.addAttribute("completedCourseIds", Set.of());
            model.addAttribute("availableCourses", List.of());
            model.addAttribute("diveraRics", List.of());
            return;
        }
        List<PersonCourseCompletion> completions = detail.completions();
        model.addAttribute("completions", completions);
        Set<Long> completedCourseIds =
                completions.stream().map(c -> c.getCourse().getId()).collect(Collectors.toCollection(HashSet::new));
        model.addAttribute("completedCourseIds", completedCourseIds);
        List<Course> unitCourses = personalService.listCourses(unitId, true);
        model.addAttribute(
                "availableCourses",
                unitCourses.stream()
                        .filter(course -> !completedCourseIds.contains(course.getId()))
                        .toList());
        model.addAttribute("diveraRics", detail.diveraRics());
    }

    private static String personInitials(Person person) {
        return initial(person.getFirstName()) + initial(person.getLastName());
    }

    private static String initial(String name) {
        if (name == null || name.isBlank()) {
            return "?";
        }
        return name.substring(0, 1);
    }

    private static void storeNewPersonFlash(
            RedirectAttributes redirectAttributes,
            String firstName,
            String lastName,
            String email,
            String phone,
            LocalDate birthdate,
            boolean allowLogin,
            String passwordDelivery) {
        redirectAttributes.addFlashAttribute("formFirstName", firstName);
        redirectAttributes.addFlashAttribute("formLastName", lastName);
        redirectAttributes.addFlashAttribute("formEmail", email);
        redirectAttributes.addFlashAttribute("formPhone", phone);
        redirectAttributes.addFlashAttribute("formBirthdate", birthdate);
        redirectAttributes.addFlashAttribute("formAllowLogin", allowLogin);
        redirectAttributes.addFlashAttribute("formPasswordDelivery", passwordDelivery);
    }

    private static String appendLoginCreatedNotice(
            String baseMessage,
            String username,
            String email,
            String initialPassword,
            String mailNotice) {
        String loginPart = " Login: „" + username + "“";
        if (email != null && !email.isBlank()) {
            loginPart += " oder E-Mail „" + email.trim() + "“";
        }
        if (initialPassword != null) {
            return baseMessage + loginPart + ", Startpasswort: " + initialPassword + ".";
        }
        if (mailNotice != null && !mailNotice.isBlank()) {
            return baseMessage + loginPart + ". " + mailNotice;
        }
        return baseMessage + loginPart + ".";
    }

    private static String normalizePersonalTab(String tab) {
        if ("gruppen".equals(tab)) {
            return "gruppen";
        }
        return "mitglieder";
    }

    private static String normalizeTab(String tab) {
        if (tab == null) {
            return "stammdaten";
        }
        return switch (tab) {
            case "lehrgaenge", "anwesenheit", "schnittstellen" -> tab;
            case "divera" -> "schnittstellen";
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

    private Unit resolveUnit(Long unitId, AppUserDetails actor, Model model) {
        Optional<Unit> unit = unitService.resolveActiveUnit(unitId, actor);
        if (unit.isEmpty()) {
            throw new IllegalStateException("Keine aktive Einheit");
        }
        Unit resolved = unit.get();
        model.addAttribute("unitId", resolved.getId());
        model.addAttribute("currentUnitName", resolved.getName());
        model.addAttribute("units", unitService.findActiveOrdered(actor));
        model.addAttribute("unitSwitchDisabled", actor != null && !actor.getRole().isSuperAdmin());
        return resolved;
    }
}
