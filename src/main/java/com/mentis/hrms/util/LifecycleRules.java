package com.mentis.hrms.util;

import java.time.LocalDate;
import java.util.*;

/** PURE rules for the employee lifecycle (onboarding → probation → confirmation → resignation/exit). */
public final class LifecycleRules {

    public static final Set<String> EMPLOYMENT_STATUS = Set.of(
            "ONBOARDING", "PROBATION", "CONFIRMED", "NOTICE", "EXITED", "INACTIVE");
    public static final Set<String> RECOMMENDATIONS = Set.of("CONFIRM", "EXTEND", "TERMINATE");
    public static final String ANY_GRADE = "__ANY__";

    private LifecycleRules() {}

    /** Manager recommendation validation. Returns list of problems (empty = valid). */
    public static List<String> validateManagerDecision(String recommendation, Integer managerRating,
                                                       Integer extensionDays, String comments) {
        List<String> problems = new ArrayList<>();
        String rec = recommendation == null ? "" : recommendation.trim().toUpperCase(Locale.ROOT);
        if (!RECOMMENDATIONS.contains(rec)) {
            problems.add("recommendation must be one of CONFIRM|EXTEND|TERMINATE");
        }
        if (managerRating != null && (managerRating < 1 || managerRating > 5)) {
            problems.add("managerRating must be between 1 and 5");
        }
        if ("EXTEND".equals(rec) && (extensionDays == null || extensionDays <= 0)) {
            problems.add("EXTEND requires extensionDays > 0");
        }
        if ("TERMINATE".equals(rec) && (comments == null || comments.trim().length() < 10)) {
            problems.add("TERMINATE requires comments of at least 10 characters");
        }
        return problems;
    }

    /** Notice shortfall: positive when requested last day is earlier than submitted + required notice. */
    public static long noticeShortfallDays(LocalDate submittedOn, LocalDate requestedLastDay, int noticeDaysRequired) {
        long actual = java.time.temporal.ChronoUnit.DAYS.between(submittedOn, requestedLastDay);
        return Math.max(0, noticeDaysRequired - actual);
    }

    public static String employmentMode(String employmentType) {
        if (employmentType == null) return "FULL_TIME";
        String t = employmentType.trim().toUpperCase(Locale.ROOT);
        if (t.startsWith("FULL")) return "FULL_TIME";
        if (t.startsWith("PART")) return "PART_TIME";
        if (t.startsWith("CONTRACT") || t.startsWith("CONSULT")) return "CONTRACT";
        if (t.startsWith("INTERN")) return "INTERN";
        return "FULL_TIME";
    }

    /** Task titles for the default onboarding checklist (REQ-8-01) — order matters for UI. */
    public static List<String[]> defaultSeedTasks() {
        // title, owner_role, due_offset_days, mandatory, position
        return List.of(
                new String[]{"Upload onboarding documents", "EMPLOYEE", "1", "1", "1"},
                new String[]{"Create IT account (email + SSO)", "IT", "1", "1", "2"},
                new String[]{"Issue laptop / equipment", "IT", "1", "1", "3"},
                new String[]{"Issue ID card", "HR", "2", "1", "4"},
                new String[]{"Assign buddy", "MANAGER", "2", "0", "5"},
                new String[]{"Schedule induction", "HR", "3", "1", "6"},
                new String[]{"Policy acknowledgement signed", "EMPLOYEE", "3", "1", "7"},
                new String[]{"Bank details submitted", "EMPLOYEE", "5", "1", "8"},
                new String[]{"First-week meeting with manager", "MANAGER", "7", "1", "9"}
        );
    }

    /** REQ-8-08 exit checklist (task, owner). Order matters for UI. */
    public static List<String[]> defaultExitChecklist() {
        // task, owner
        return List.of(
                new String[]{"KNOWLEDGE_TRANSFER", "EMPLOYEE"},
                new String[]{"ASSET_RETURN", "EMPLOYEE"},
                new String[]{"ACCESS_REMOVAL", "IT"},
                new String[]{"LEAVE_ENCASHMENT", "HR"},
                new String[]{"DOCUMENT_HANDOVER", "EMPLOYEE"},
                new String[]{"MANAGER_REASSIGN", "HR"},
                new String[]{"MANAGER_SIGNOFF", "MANAGER"},
                new String[]{"HR_SIGNOFF", "HR"}
        );
    }
}
