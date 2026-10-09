package com.mentis.hrms.service.ai;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Rule-based classifier for admin AI chat messages.
 *
 * Looks at what the admin typed and decides which sections of org data are
 * relevant, so the caller only builds/sends those sections instead of the
 * entire database on every message. Pure keyword matching -- no LLM call,
 * no extra latency, no extra tokens spent deciding.
 *
 * NOTE: this is intentionally simple (substring matching). It will
 * over-trigger on some words (e.g. "site" matching LOCATIONS inside an
 * unrelated sentence) -- that's a safe failure mode (you include one extra
 * section) vs the alternative of missing a domain the user actually asked
 * about. If you see it firing wrong keywords in practice, tighten the
 * specific keyword set rather than the matching logic.
 */
public class QueryIntentClassifier {

    public enum Domain {
        EMPLOYEES,
        LEAVE,
        RECRUITMENT,      // jobs + applications
        LOCATIONS,
        ANNOUNCEMENTS,
        HOLIDAYS,
        ROLE_PERMISSIONS,
        DOCUMENTS
    }

    private static final Set<String> LEAVE_KEYWORDS = Set.of(
            "leave", "vacation", "pto", "time off", "attendance", "absent", "sick",
            "approve", "reject", "pending request", "checked in", "checked out",
            "check-in", "check-out");

    private static final Set<String> RECRUITMENT_KEYWORDS = Set.of(
            "job", "jobs", "recruit", "candidate", "applicant", "application",
            "hiring", "vacancy", "position", "interview", "offer letter",
            "onboarding", "pipeline");

    private static final Set<String> EMPLOYEE_KEYWORDS = Set.of(
            "employee", "staff", "who is", "headcount", "team", "department",
            "manager", "reports to", "designation", "org structure",
            "organization structure", "hire date", "joined");

    private static final Set<String> LOCATION_KEYWORDS = Set.of(
            "location", "office", "branch", "site");

    private static final Set<String> ANNOUNCEMENT_KEYWORDS = Set.of(
            "announcement", "news", "notice", "memo", "post an update");

    private static final Set<String> HOLIDAY_KEYWORDS = Set.of(
            "holiday", "holidays", "calendar", "public holiday");

    private static final Set<String> ROLE_KEYWORDS = Set.of(
            "role", "permission", "access", "rbac", "who can", "authorized to");

    private static final Set<String> DOCUMENT_KEYWORDS = Set.of(
            "document", "documents", "docs", "onboarding doc", "offer letter", "offerletter",
            "offer letters", "offerletters", "pan card", "aadhaar", "verification", "checklist");

    /**
     * @return domains relevant to this message. An empty set means nothing
     *         specific was detected (greetings, thanks, vague small talk) --
     *         callers should fall back to a small counts-only summary rather
     *         than a full dump.
     */
    public Set<Domain> classify(String message) {
        if (message == null || message.isBlank()) {
            return EnumSet.noneOf(Domain.class);
        }

        String text = " " + message.toLowerCase(Locale.ROOT) + " ";
        Set<Domain> domains = EnumSet.noneOf(Domain.class);

        if (containsAny(text, LEAVE_KEYWORDS)) domains.add(Domain.LEAVE);
        if (containsAny(text, RECRUITMENT_KEYWORDS)) domains.add(Domain.RECRUITMENT);
        if (containsAny(text, EMPLOYEE_KEYWORDS)) domains.add(Domain.EMPLOYEES);
        if (containsAny(text, LOCATION_KEYWORDS)) domains.add(Domain.LOCATIONS);
        if (containsAny(text, ANNOUNCEMENT_KEYWORDS)) domains.add(Domain.ANNOUNCEMENTS);
        if (containsAny(text, HOLIDAY_KEYWORDS)) domains.add(Domain.HOLIDAYS);
        if (containsAny(text, ROLE_KEYWORDS)) domains.add(Domain.ROLE_PERMISSIONS);
        if (containsAny(text, DOCUMENT_KEYWORDS)) domains.add(Domain.DOCUMENTS);

        return domains;
    }

    private boolean containsAny(String text, Set<String> keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
