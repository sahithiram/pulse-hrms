package com.mentis.hrms.util;

import java.util.*;

/**
 * REQ-7-02 — canonical pipeline + allowed transitions. PURE (no IO) → unit-testable.
 * Terminal stages: HIRED, REJECTED, WITHDRAWN. REJECTED/WITHDRAWN reachable at ANY time from any non-terminal.
 * No backward transitions (admin override is a SUPER_ADMIN flow, out of scope for this sprint — documented).
 * Per-job custom stage sets are NOT modelled this sprint: a single canonical funnel is enforced;
 * configurability = separate `pipeline_stage` seed rows is a planned follow-up (see 02_PIPELINE_CONTRACT.md).
 */
public final class PipelineTransitionRules {

    private PipelineTransitionRules() {}

    public static final String APPLIED = "APPLIED";
    public static final String SCREENING = "SCREENING";
    public static final String INTERVIEW_1 = "INTERVIEW_1";
    public static final String INTERVIEW_2 = "INTERVIEW_2";
    public static final String HR_ROUND = "HR_ROUND";
    public static final String OFFER = "OFFER";
    public static final String HIRED = "HIRED";
    public static final String REJECTED = "REJECTED";
    public static final String WITHDRAWN = "WITHDRAWN";

    public static final Set<String> ALL = Set.of(APPLIED, SCREENING, INTERVIEW_1, INTERVIEW_2,
            HR_ROUND, OFFER, HIRED, REJECTED, WITHDRAWN);
    public static final Set<String> TERMINAL = Set.of(HIRED, REJECTED, WITHDRAWN);

    private static final Map<String, Set<String>> FWD = new LinkedHashMap<>();
    static {
        FWD.put(APPLIED, Set.of(SCREENING, INTERVIEW_1, INTERVIEW_2, OFFER));
        FWD.put(SCREENING, Set.of(INTERVIEW_1, INTERVIEW_2, HR_ROUND, OFFER));
        FWD.put(INTERVIEW_1, Set.of(INTERVIEW_2, HR_ROUND, OFFER));
        FWD.put(INTERVIEW_2, Set.of(HR_ROUND, OFFER));
        FWD.put(HR_ROUND, Set.of(OFFER));
        FWD.put(OFFER, Set.of(HIRED));
        FWD.put(HIRED, Set.of());
        FWD.put(REJECTED, Set.of());
        FWD.put(WITHDRAWN, Set.of());
    }

    public static boolean isTerminal(String stage) {
        return TERMINAL.contains(stage);
    }

    public static boolean isKnown(String stage) {
        return ALL.contains(stage);
    }

    public static boolean isAllowed(String from, String to) {
        if (from == null || to == null) return false;
        if (!ALL.contains(from) || !ALL.contains(to)) return false;
        if (isTerminal(from)) return false;
        // Any time, from any non-terminal: reject / candidate withdraws.
        if (REJECTED.equals(to) || WITHDRAWN.equals(to)) return true;
        return FWD.getOrDefault(from, Set.of()).contains(to);
    }

    /** Map legacy free-text job_applications.status → canonical stage; unknown → APPLIED (assumption documented). */
    public static String mapLegacyStatus(String status) {
        if (status == null) return APPLIED;
        String s = status.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        switch (s) {
            case "applied": case "new": case "submitted": return APPLIED;
            case "screening": case "in_review": case "review": return SCREENING;
            case "interview": case "interviewed": return INTERVIEW_1;
            case "offer": return OFFER;
            case "hired": case "joined": case "selected": return HIRED;
            case "rejected": return REJECTED;
            case "withdrawn": case "withdrew": return WITHDRAWN;
            default:
                String upper = status.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
                return ALL.contains(upper) ? upper : APPLIED;
        }
    }

    public static String humanLabel(String stage) {
        switch (stage) {
            case APPLIED: return "Applied";
            case SCREENING: return "Screening";
            case INTERVIEW_1: return "Interview 1";
            case INTERVIEW_2: return "Interview 2";
            case HR_ROUND: return "HR Round";
            case OFFER: return "Offer";
            case HIRED: return "Hired";
            case REJECTED: return "Rejected";
            case WITHDRAWN: return "Withdrawn";
            default: return stage;
        }
    }
}
