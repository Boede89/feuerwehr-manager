package de.feuerwehr.manager.uvv;

import de.feuerwehr.manager.berichte.AnwesenheitslisteService;
import de.feuerwehr.manager.berichte.AttendanceReport;
import de.feuerwehr.manager.berichte.AttendanceReportRepository;
import de.feuerwehr.manager.personal.Person;
import de.feuerwehr.manager.personal.PersonalService;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.settings.TestModeService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitRepository;
import de.feuerwehr.manager.user.UserRepository;
import de.feuerwehr.manager.util.PersonMembership;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UvvService {

    public static final int MAX_QUESTIONS = 5;

    private final UvvSettingsService settingsService;
    private final UvvCampaignRepository campaignRepository;
    private final UvvQuestionRepository questionRepository;
    private final UvvCompletionRepository completionRepository;
    private final PersonalService personalService;
    private final UnitRepository unitRepository;
    private final UserRepository userRepository;
    private final AnwesenheitslisteService anwesenheitslisteService;
    private final AttendanceReportRepository attendanceReportRepository;
    private final TestModeService testModeService;
    private final UvvPresentationService presentationService;

    public static UvvStatusLevel computeLevel(LocalDate lastCompletedOn, int intervalMonths, int warnDays, LocalDate today) {
        if (lastCompletedOn == null) {
            return UvvStatusLevel.MISSING;
        }
        LocalDate nextDue = lastCompletedOn.plusMonths(intervalMonths);
        if (nextDue.isBefore(today)) {
            return UvvStatusLevel.OVERDUE;
        }
        long days = ChronoUnit.DAYS.between(today, nextDue);
        if (days <= warnDays) {
            return UvvStatusLevel.WARN;
        }
        return UvvStatusLevel.OK;
    }

    public static LocalDate nextDueOn(LocalDate lastCompletedOn, int intervalMonths) {
        return lastCompletedOn == null ? null : lastCompletedOn.plusMonths(intervalMonths);
    }

    @Transactional
    public List<UvvCampaign> listCampaigns(long unitId) {
        settingsService.ensureSettings(unitId);
        return campaignRepository.findByUnitIdOrderByEventDateDescIdDesc(unitId);
    }

    @Transactional
    public UvvCampaign requireCampaign(long unitId, long campaignId) {
        return campaignRepository
                .findByIdAndUnitId(campaignId, unitId)
                .orElseThrow(() -> new IllegalArgumentException("Kampagne nicht gefunden."));
    }

    @Transactional
    public UvvCampaign createCampaign(long unitId, String title, LocalDate eventDate, String contentText) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Bitte einen Titel angeben.");
        }
        if (eventDate == null) {
            throw new IllegalArgumentException("Bitte ein Datum angeben.");
        }
        Unit unit = unitRepository
                .findById(unitId)
                .orElseThrow(() -> new IllegalArgumentException("Einheit nicht gefunden."));
        settingsService.ensureSettings(unitId);
        UvvCampaign campaign = new UvvCampaign();
        campaign.setUnit(unit);
        campaign.setTitle(title.trim());
        campaign.setEventDate(eventDate);
        campaign.setContentText(trimToNull(contentText));
        campaign.setStatus(UvvCampaignStatus.OPEN);
        return campaignRepository.save(campaign);
    }

    @Transactional
    public UvvCampaign updateCampaign(
            long unitId,
            long campaignId,
            String title,
            LocalDate eventDate,
            String contentText,
            UvvCampaignStatus status,
            Long attendanceReportId) {
        UvvCampaign campaign = requireCampaign(unitId, campaignId);
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Bitte einen Titel angeben.");
        }
        if (eventDate == null) {
            throw new IllegalArgumentException("Bitte ein Datum angeben.");
        }
        campaign.setTitle(title.trim());
        campaign.setEventDate(eventDate);
        campaign.setContentText(trimToNull(contentText));
        if (status != null) {
            campaign.setStatus(status);
        }
        if (attendanceReportId != null && attendanceReportId > 0) {
            AttendanceReport report = attendanceReportRepository
                    .findById(attendanceReportId)
                    .orElseThrow(() -> new IllegalArgumentException("Anwesenheitsliste nicht gefunden."));
            if (!report.getUnit().getId().equals(unitId)) {
                throw new IllegalArgumentException("Anwesenheitsliste gehört nicht zu dieser Einheit.");
            }
            campaign.setAttendanceReport(report);
            if (report.getUnitTermin() != null) {
                campaign.setUnitTermin(report.getUnitTermin());
            }
        } else if (attendanceReportId != null && attendanceReportId == 0) {
            campaign.setAttendanceReport(null);
            campaign.setUnitTermin(null);
        }
        return campaignRepository.save(campaign);
    }

    @Transactional
    public void deleteCampaign(long unitId, long campaignId) {
        UvvCampaign campaign = requireCampaign(unitId, campaignId);
        presentationService.deleteAllFilesForCampaign(campaign.getId());
        campaignRepository.delete(campaign);
    }

    @Transactional
    public void replaceQuestions(long unitId, long campaignId, List<QuestionInput> inputs) {
        UvvCampaign campaign = requireCampaign(unitId, campaignId);
        List<QuestionInput> cleaned = inputs == null ? List.of() : inputs.stream()
                .filter(q -> q != null && q.questionText() != null && !q.questionText().isBlank())
                .toList();
        if (cleaned.size() > MAX_QUESTIONS) {
            throw new IllegalArgumentException("Es sind höchstens " + MAX_QUESTIONS + " Fragen erlaubt.");
        }
        questionRepository.deleteByCampaignId(campaign.getId());
        int order = 0;
        for (QuestionInput input : cleaned) {
            validateQuestion(input);
            UvvQuestion q = new UvvQuestion();
            q.setCampaign(campaign);
            q.setSortOrder(order++);
            q.setQuestionText(input.questionText().trim());
            q.setOptionA(input.optionA().trim());
            q.setOptionB(input.optionB().trim());
            q.setOptionC(trimToNull(input.optionC()));
            q.setOptionD(trimToNull(input.optionD()));
            q.setCorrectOption(input.correctOption().trim().toUpperCase(Locale.ROOT));
            questionRepository.save(q);
        }
    }

    @Transactional(readOnly = true)
    public List<UvvQuestion> listQuestions(long campaignId) {
        return questionRepository.findByCampaignIdOrderBySortOrderAscIdAsc(campaignId);
    }

    @Transactional
    public CampaignDetail loadCampaignDetail(long unitId, long campaignId) {
        UvvCampaign campaign = requireCampaign(unitId, campaignId);
        return new CampaignDetail(campaign, listQuestions(campaignId), completionRepository.findByCampaignId(campaignId));
    }

    @Transactional
    public OverviewPage listOverview(long unitId, String filter) {
        UnitUvvSettings settings = settingsService.ensureSettings(unitId);
        LocalDate today = LocalDate.now();
        String normalized = normalizeFilter(filter);
        List<Person> persons = personalService.listPersons(unitId).stream()
                .filter(PersonMembership::isCurrentlyMember)
                .toList();
        Map<Long, LocalDate> lastByPerson = latestCompletionDates(persons.stream().map(Person::getId).toList());

        List<OverviewRow> rows = new ArrayList<>();
        int overdue = 0;
        int warn = 0;
        int ok = 0;
        int missing = 0;
        for (Person person : persons) {
            LocalDate last = lastByPerson.get(person.getId());
            LocalDate nextDue = nextDueOn(last, settings.getIntervalMonths());
            UvvStatusLevel level = computeLevel(last, settings.getIntervalMonths(), settings.getWarnDays(), today);
            switch (level) {
                case OVERDUE -> overdue++;
                case WARN -> warn++;
                case OK -> ok++;
                case MISSING -> missing++;
            }
            if (!matchesFilter(normalized, level)) {
                continue;
            }
            rows.add(new OverviewRow(person, level, last, nextDue));
        }
        rows.sort(Comparator
                .comparing((OverviewRow r) -> sortRank(r.level()))
                .thenComparing(r -> r.person().getLastName(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(r -> r.person().getFirstName(), String.CASE_INSENSITIVE_ORDER));
        return new OverviewPage(
                rows,
                new OverviewStats(persons.size(), overdue, warn, ok, missing),
                settings.getIntervalMonths(),
                settings.getWarnDays(),
                normalized,
                campaignRepository.findOpenByUnitId(unitId));
    }

    @Transactional
    public PersonUvvView loadPersonView(long personId) {
        Person person = personalService.requirePerson(personId);
        long unitId = person.getUnit().getId();
        UnitUvvSettings settings = settingsService.ensureSettings(unitId);
        List<UvvCompletion> completions = completionRepository.findByPersonIdOrderByCompletedOnDesc(personId);
        LocalDate last = completions.isEmpty() ? null : completions.get(0).getCompletedOn();
        LocalDate today = LocalDate.now();
        return new PersonUvvView(
                person,
                completions,
                computeLevel(last, settings.getIntervalMonths(), settings.getWarnDays(), today),
                nextDueOn(last, settings.getIntervalMonths()),
                settings.getIntervalMonths(),
                settings.getWarnDays(),
                campaignRepository.findByUnitIdOrderByEventDateDescIdDesc(unitId));
    }

    @Transactional
    public UvvCompletion recordManualPresence(
            long personId, long campaignId, LocalDate completedOn, String notes, AppUserDetails actor) {
        Person person = personalService.requirePerson(personId);
        UvvCampaign campaign = requireCampaign(person.getUnit().getId(), campaignId);
        return saveCompletion(
                person,
                campaign,
                completedOn != null ? completedOn : campaign.getEventDate(),
                UvvCompletionChannel.MANUAL,
                false,
                notes,
                actor);
    }

    @Transactional
    public int importPresenceFromAttendance(long unitId, long campaignId, AppUserDetails actor) {
        UvvCampaign campaign = requireCampaign(unitId, campaignId);
        AttendanceReport report = campaign.getAttendanceReport();
        if (report == null) {
            throw new IllegalArgumentException("Der Kampagne ist keine Anwesenheitsliste zugeordnet.");
        }
        Set<Long> presentIds = anwesenheitslisteService.presentPersonIds(unitId, report.getId());
        int created = 0;
        LocalDate on = campaign.getEventDate() != null ? campaign.getEventDate() : report.getEventDate();
        for (Long personId : presentIds) {
            if (personId == null) {
                continue;
            }
            if (completionRepository.existsByPersonIdAndCampaignId(personId, campaignId)) {
                continue;
            }
            Person person = personalService.requirePerson(personId);
            if (!person.getUnit().getId().equals(unitId)) {
                continue;
            }
            saveCompletion(person, campaign, on, UvvCompletionChannel.PRESENCE, false, null, actor);
            created++;
        }
        return created;
    }

    @Transactional
    public OnlineCampaignView loadOnlineForPerson(long personId) {
        Person person = personalService.requirePerson(personId);
        long unitId = person.getUnit().getId();
        List<UvvCampaign> open = campaignRepository.findOpenByUnitId(unitId);
        for (UvvCampaign campaign : open) {
            if (!completionRepository.existsByPersonIdAndCampaignId(personId, campaign.getId())) {
                return new OnlineCampaignView(campaign, listQuestions(campaign.getId()), true);
            }
        }
        return new OnlineCampaignView(null, List.of(), false);
    }

    @Transactional
    public UvvCompletion completeOnline(
            long personId,
            long campaignId,
            Map<Long, String> answersByQuestionId,
            boolean confirmed,
            boolean presentationCompleted,
            AppUserDetails actor) {
        if (!confirmed) {
            throw new IllegalArgumentException("Bitte die Durchführung bestätigen.");
        }
        Person person = personalService.requirePerson(personId);
        UvvCampaign campaign = requireCampaign(person.getUnit().getId(), campaignId);
        if (campaign.getStatus() != UvvCampaignStatus.OPEN) {
            throw new IllegalArgumentException("Diese Kampagne ist nicht mehr offen.");
        }
        if (completionRepository.existsByPersonIdAndCampaignId(personId, campaignId)) {
            throw new IllegalArgumentException("Die Unterweisung wurde bereits erledigt.");
        }
        if (campaign.hasPresentation() && !presentationCompleted) {
            throw new IllegalArgumentException(
                    "Bitte die Präsentation zuerst vollständig durchklicken.");
        }
        List<UvvQuestion> questions = listQuestions(campaignId);
        boolean quizPassed = true;
        if (!questions.isEmpty()) {
            for (UvvQuestion question : questions) {
                String answer = answersByQuestionId == null
                        ? null
                        : answersByQuestionId.get(question.getId());
                if (answer == null || answer.isBlank()) {
                    throw new IllegalArgumentException("Bitte alle Fragen beantworten.");
                }
                if (!question.getCorrectOption().equalsIgnoreCase(answer.trim())) {
                    quizPassed = false;
                }
            }
            if (!quizPassed) {
                throw new IllegalArgumentException(
                        "Nicht alle Antworten waren korrekt. Bitte den Inhalt nochmals lesen und erneut versuchen.");
            }
        }
        return saveCompletion(
                person,
                campaign,
                LocalDate.now(),
                UvvCompletionChannel.ONLINE,
                quizPassed || questions.isEmpty(),
                null,
                actor);
    }

    @Transactional(readOnly = true)
    public List<AttendanceReportOption> listAttendanceOptions(long unitId) {
        int year = LocalDate.now().getYear();
        boolean test = testModeService.isEnabled();
        List<AttendanceReport> reports = new ArrayList<>();
        reports.addAll(attendanceReportRepository.findByUnitIdAndYear(
                unitId, LocalDate.of(year, 1, 1), LocalDate.of(year + 1, 1, 1), test));
        reports.addAll(attendanceReportRepository.findByUnitIdAndYear(
                unitId, LocalDate.of(year - 1, 1, 1), LocalDate.of(year, 1, 1), test));
        return reports.stream()
                .sorted(Comparator.comparing(AttendanceReport::getEventDate).reversed())
                .map(r -> new AttendanceReportOption(
                        r.getId(),
                        r.getTitle(),
                        r.getEventDate(),
                        r.getReportNumber()))
                .toList();
    }

    private UvvCompletion saveCompletion(
            Person person,
            UvvCampaign campaign,
            LocalDate completedOn,
            UvvCompletionChannel channel,
            boolean quizPassed,
            String notes,
            AppUserDetails actor) {
        if (completedOn == null) {
            throw new IllegalArgumentException("Bitte ein Datum angeben.");
        }
        if (completionRepository.existsByPersonIdAndCampaignId(person.getId(), campaign.getId())) {
            throw new IllegalArgumentException("Für diese Person ist die Kampagne bereits erledigt.");
        }
        UvvCompletion completion = new UvvCompletion();
        completion.setPerson(person);
        completion.setCampaign(campaign);
        completion.setCompletedOn(completedOn);
        completion.setChannel(channel);
        completion.setQuizPassed(quizPassed);
        completion.setNotes(trimToNull(notes));
        if (actor != null) {
            userRepository.findById(actor.getUserId()).ifPresent(completion::setRecordedBy);
            completion.setRecordedByDisplayName(actor.getDisplayName());
        }
        return completionRepository.save(completion);
    }

    private Map<Long, LocalDate> latestCompletionDates(List<Long> personIds) {
        Map<Long, LocalDate> result = new HashMap<>();
        if (personIds == null || personIds.isEmpty()) {
            return result;
        }
        for (UvvCompletion c : completionRepository.findByPersonIdIn(personIds)) {
            Long pid = c.getPerson().getId();
            LocalDate existing = result.get(pid);
            if (existing == null || c.getCompletedOn().isAfter(existing)) {
                result.put(pid, c.getCompletedOn());
            }
        }
        return result;
    }

    private static void validateQuestion(QuestionInput input) {
        if (input.optionA() == null || input.optionA().isBlank()
                || input.optionB() == null || input.optionB().isBlank()) {
            throw new IllegalArgumentException("Jede Frage braucht mindestens Antwort A und B.");
        }
        String correct = input.correctOption() == null ? "" : input.correctOption().trim().toUpperCase(Locale.ROOT);
        Set<String> allowed = new HashSet<>();
        allowed.add("A");
        allowed.add("B");
        if (input.optionC() != null && !input.optionC().isBlank()) {
            allowed.add("C");
        }
        if (input.optionD() != null && !input.optionD().isBlank()) {
            allowed.add("D");
        }
        if (!allowed.contains(correct)) {
            throw new IllegalArgumentException("Bitte eine gültige richtige Antwort (A–D) wählen.");
        }
    }

    private static String normalizeFilter(String filter) {
        if (filter == null || filter.isBlank()) {
            return "all";
        }
        return switch (filter.trim().toLowerCase()) {
            case "overdue", "warn", "ok", "missing" -> filter.trim().toLowerCase();
            default -> "all";
        };
    }

    private static boolean matchesFilter(String filter, UvvStatusLevel level) {
        return switch (filter) {
            case "overdue" -> level == UvvStatusLevel.OVERDUE;
            case "warn" -> level == UvvStatusLevel.WARN;
            case "ok" -> level == UvvStatusLevel.OK;
            case "missing" -> level == UvvStatusLevel.MISSING;
            default -> true;
        };
    }

    private static int sortRank(UvvStatusLevel level) {
        return switch (level) {
            case OVERDUE -> 0;
            case WARN -> 1;
            case MISSING -> 2;
            case OK -> 3;
        };
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public record QuestionInput(
            String questionText, String optionA, String optionB, String optionC, String optionD, String correctOption) {}

    public record CampaignDetail(UvvCampaign campaign, List<UvvQuestion> questions, List<UvvCompletion> completions) {}

    public record OverviewRow(Person person, UvvStatusLevel level, LocalDate lastCompletedOn, LocalDate nextDueOn) {}

    public record OverviewStats(int total, int overdue, int warn, int ok, int missing) {}

    public record OverviewPage(
            List<OverviewRow> rows,
            OverviewStats stats,
            int intervalMonths,
            int warnDays,
            String activeFilter,
            List<UvvCampaign> openCampaigns) {}

    public record PersonUvvView(
            Person person,
            List<UvvCompletion> completions,
            UvvStatusLevel level,
            LocalDate nextDueOn,
            int intervalMonths,
            int warnDays,
            List<UvvCampaign> campaigns) {}

    public record OnlineCampaignView(UvvCampaign campaign, List<UvvQuestion> questions, boolean available) {}

    public record AttendanceReportOption(long id, String title, LocalDate eventDate, String reportNumber) {}
}
