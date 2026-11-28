package com.mentis.hrms;

import com.mentis.hrms.service.InterviewService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** NOT executed in the delivery sandbox — run: mvn -Dtest=InterviewFeedbackVisibilityTest test */
class InterviewFeedbackVisibilityTest {

    @Test
    void interviewerCsvParsing_trimsAndFilters() {
        assertEquals(List.of("EMP001", "EMP002"), InterviewService.interviewerIds(" EMP001 , , EMP002,"));
        assertTrue(InterviewService.interviewerIds(null).isEmpty());
        assertTrue(InterviewService.interviewerIds("   ").isEmpty());
    }

    @Test
    void recommendationVocabulary() {
        assertEquals(4, InterviewService.RECOMMENDATIONS.size());
        assertTrue(InterviewService.RECOMMENDATIONS.contains("STRONG_YES"));
        assertTrue(InterviewService.RECOMMENDATIONS.contains("STRONG_NO"));
    }
}
