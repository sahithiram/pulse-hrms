package com.mentis.hrms;

import com.mentis.hrms.util.PipelineTransitionRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** NOT executed in the delivery sandbox — run: mvn -Dtest=PipelineTransitionRulesTest test */
class PipelineTransitionRulesTest {

    @Test
    void happyPath_movesForward() {
        assertTrue(PipelineTransitionRules.isAllowed("APPLIED", "SCREENING"));
        assertTrue(PipelineTransitionRules.isAllowed("SCREENING", "INTERVIEW_1"));
        assertTrue(PipelineTransitionRules.isAllowed("INTERVIEW_1", "INTERVIEW_2"));
        assertTrue(PipelineTransitionRules.isAllowed("INTERVIEW_2", "HR_ROUND"));
        assertTrue(PipelineTransitionRules.isAllowed("HR_ROUND", "OFFER"));
        assertTrue(PipelineTransitionRules.isAllowed("OFFER", "HIRED"));
    }

    @Test
    void rejection_allowed_fromAnyNonTerminal() {
        for (String stage : new String[]{"APPLIED", "SCREENING", "INTERVIEW_1", "INTERVIEW_2", "HR_ROUND", "OFFER"}) {
            assertTrue(PipelineTransitionRules.isAllowed(stage, "REJECTED"), stage + " -> REJECTED must be allowed");
            assertTrue(PipelineTransitionRules.isAllowed(stage, "WITHDRAWN"), stage + " -> WITHDRAWN must be allowed");
        }
    }

    @Test
    void backward_transitions_forbidden() {
        assertFalse(PipelineTransitionRules.isAllowed("INTERVIEW_2", "APPLIED"));
        assertFalse(PipelineTransitionRules.isAllowed("OFFER", "INTERVIEW_1"));
        assertFalse(PipelineTransitionRules.isAllowed("HIRED", "OFFER"));
    }

    @Test
    void terminals_have_no_outgoing() {
        assertFalse(PipelineTransitionRules.isAllowed("HIRED", "REJECTED"));
        assertFalse(PipelineTransitionRules.isAllowed("REJECTED", "APPLIED"));
        assertFalse(PipelineTransitionRules.isAllowed("WITHDRAWN", "APPLIED"));
    }

    @Test
    void unknownStages_rejected() {
        assertFalse(PipelineTransitionRules.isAllowed("MAGIC", "HIRED"));
        assertFalse(PipelineTransitionRules.isAllowed("OFFER", "MAGIC"));
        assertFalse(PipelineTransitionRules.isAllowed(null, "OFFER"));
    }

    @Test
    void legacyStatusMapping() {
        assertEquals("APPLIED", PipelineTransitionRules.mapLegacyStatus("Applied"));
        assertEquals("SCREENING", PipelineTransitionRules.mapLegacyStatus("In Review"));
        assertEquals("INTERVIEW_1", PipelineTransitionRules.mapLegacyStatus("Interview"));
        assertEquals("HIRED", PipelineTransitionRules.mapLegacyStatus("Hired"));
        assertEquals("REJECTED", PipelineTransitionRules.mapLegacyStatus("Rejected"));
        assertEquals("INTERVIEW_2", PipelineTransitionRules.mapLegacyStatus("INTERVIEW_2"));
        assertEquals("APPLIED", PipelineTransitionRules.mapLegacyStatus("some-unknown-thing"));
    }
}
