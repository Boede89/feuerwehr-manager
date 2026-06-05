package de.feuerwehr.manager.transfer;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UnitDataExportDocument {

    public static final String FORMAT = "feuerwehr-manager-personal-atemschutz";
    public static final int VERSION = 1;

    private String format = FORMAT;
    private int version = VERSION;
    private String exportedAt;
    private String unitName;
    private Long unitId;

    private List<QualificationTypeRow> qualificationTypes = new ArrayList<>();
    private List<CourseRow> courses = new ArrayList<>();
    private List<PersonRow> persons = new ArrayList<>();
    private List<CourseCompletionRow> personCourseCompletions = new ArrayList<>();
    private List<GroupRow> personGroups = new ArrayList<>();
    private List<AttendanceRow> personAttendance = new ArrayList<>();
    private List<RicRow> personDiveraRics = new ArrayList<>();
    private List<CarrierRow> atemschutzCarriers = new ArrayList<>();
    private List<FitnessRecordRow> atemschutzFitnessRecords = new ArrayList<>();
    private AtemschutzSettingsRow atemschutzSettings;
    private List<EmailTemplateRow> atemschutzEmailTemplates = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    public static class QualificationTypeRow {
        private Long sourceId;
        private String name;
        private Integer sortOrder;
        private Boolean active;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class CourseRow {
        private Long sourceId;
        private String name;
        private Integer sortOrder;
        private Boolean active;
        private Long qualificationTypeSourceId;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class PersonRow {
        private Long sourceId;
        private String firstName;
        private String lastName;
        private String email;
        private String emailPrivate;
        private String phone;
        private String address;
        private String birthdate;
        private String status;
        private String diveraUcrId;
        private String notes;
        private String personnelNumber;
        private String entryDate;
        private String exitDate;
        private Long qualificationTypeSourceId;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class CourseCompletionRow {
        private Long personSourceId;
        private Long courseSourceId;
        private Integer completionYear;
        private String completedOn;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class GroupRow {
        private Long sourceId;
        private String name;
        private List<Long> memberSourceIds = new ArrayList<>();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class AttendanceRow {
        private Long personSourceId;
        private String serviceDate;
        private String serviceLabel;
        private String serviceType;
        private String status;
        private String notes;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class RicRow {
        private Long personSourceId;
        private String ricCode;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class CarrierRow {
        private Long personSourceId;
        private String status;
        private String notes;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class FitnessRecordRow {
        private Long personSourceId;
        private String recordType;
        private String validFrom;
        private String validUntil;
        private String physician;
        private String resultNotes;
        private String sourceLabel;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class AtemschutzSettingsRow {
        private Integer warnDays;
        private String agtCourseName;
        private Long agtCourseSourceId;
        private String notificationUserIds;
        private String ccUserIds;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class EmailTemplateRow {
        private String templateKey;
        private String templateName;
        private String subject;
        private String body;
    }
}
