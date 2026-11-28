package com.mentis.hrms;

import com.mentis.hrms.util.LifecycleRules;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Pure-rule tests for lifecycle logic. NOT executed yet — run with: mvn -Dtest=LifecycleRulesTest test */
class LifecycleRulesTest {

    @Test
    void defaultSeedTasksCoverReq801() {
        List<String[]> tasks = LifecycleRules.defaultSeedTasks();
        assertEquals(9, tasks.size(), "REQ-8-01 seed checklist must have 9 entries");
        Set<String> titles = new HashSet<>();
        for (String[] t : tasks) {
            assertEquals(5, t.length, "each seed task is (title, owner, offset, mandatory, position)");
            titles.add(t[0].toLowerCase(Locale.ROOT));
        }
        // All REQ-8-01 seed items present (documents, IT account, laptop, ID card, buddy, induction, policy, bank, first-week)
        String joined = String.join("|", titles);
        for (String keyword : List.of("document", "it account", "laptop", "id card", "buddy", "induction", "policy", "bank", "first-week")) {
            assertTrue(joined.contains(keyword), "seed checklist missing: " + keyword);
        }
        // position values are 1..9 in order
        for (int i = 0; i < tasks.size(); i++) {
            assertEquals(String.valueOf(i + 1), tasks.get(i)[4]);
        }
    }

    @Test
    void defaultExitChecklistCoversReq808() {
        List<String[]> tasks = LifecycleRules.defaultExitChecklist();
        assertEquals(8, tasks.size());
        Set<String> names = new HashSet<>();
        for (String[] t : tasks) {
            assertEquals(2, t.length);
            names.add(t[0]);
        }
        assertEquals(Set.of("KNOWLEDGE_TRANSFER", "ASSET_RETURN", "ACCESS_REMOVAL", "LEAVE_ENCASHMENT",
                "DOCUMENT_HANDOVER", "MANAGER_REASSIGN", "MANAGER_SIGNOFF", "HR_SIGNOFF"), names);
    }

    @Test
    void validateManagerDecisionRules() {
        assertTrue(LifecycleRules.validateManagerDecision("CONFIRM", 4, null, null).isEmpty());
        assertTrue(LifecycleRules.validateManagerDecision("EXTEND", 3, 30, null).isEmpty());
        assertFalse(LifecycleRules.validateManagerDecision("EXTEND", 3, null, null).isEmpty(),
                "EXTEND without extensionDays must fail");
        assertFalse(LifecycleRules.validateManagerDecision("TERMINATE", 1, null, "bad").isEmpty(),
                "TERMINATE needs a >=10-char comment");
        assertTrue(LifecycleRules.validateManagerDecision("TERMINATE", 1, null, "well-documented reason").isEmpty());
        assertFalse(LifecycleRules.validateManagerDecision("PROMOTE", 5, null, null).isEmpty(),
                "unknown recommendation rejected");
        assertFalse(LifecycleRules.validateManagerDecision("CONFIRM", 7, null, null).isEmpty(),
                "rating above 5 rejected");
    }

    @Test
    void noticeShortfallDays() {
        LocalDate submitted = LocalDate.of(2026, 1, 1);
        assertEquals(0, LifecycleRules.noticeShortfallDays(submitted, submitted.plusDays(30), 30));
        assertEquals(10, LifecycleRules.noticeShortfallDays(submitted, submitted.plusDays(20), 30));
        assertEquals(0, LifecycleRules.noticeShortfallDays(submitted, submitted.plusDays(45), 30));
    }

    @Test
    void employmentModeMapping() {
        assertEquals("FULL_TIME", LifecycleRules.employmentMode("FULL_TIME"));
        assertEquals("PART_TIME", LifecycleRules.employmentMode("Part Time"));
        assertEquals("CONTRACT", LifecycleRules.employmentMode("consultant"));
        assertEquals("INTERN", LifecycleRules.employmentMode("INTERN"));
        assertEquals("FULL_TIME", LifecycleRules.employmentMode(null));
        assertEquals("FULL_TIME", LifecycleRules.employmentMode("something-else"));
    }

    @Test
    void employmentStatusVocabulary() {
        assertEquals(Set.of("ONBOARDING", "PROBATION", "CONFIRMED", "NOTICE", "EXITED", "INACTIVE"),
                LifecycleRules.EMPLOYMENT_STATUS);
    }
}
