package com.mentis.hrms;

import com.mentis.hrms.service.OfferHandoffService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** NOT executed in the delivery sandbox — run: mvn -Dtest=OfferHandoffServiceTest test */
class OfferHandoffServiceTest {

    @Test
    void missingMandatoryFields_detectsAllGaps() {
        List<String> missing = OfferHandoffService.missingMandatoryFields(
                null, "", "  ", null, null, null, null, null);
        assertTrue(missing.contains("candidateName"));
        assertTrue(missing.contains("candidateEmail"));
        assertTrue(missing.contains("designation"));
        assertTrue(missing.contains("department"));
        assertTrue(missing.contains("annualSalary"));
        assertTrue(missing.contains("joiningDate"));
        assertTrue(missing.contains("employmentType"));
        assertTrue(missing.contains("workLocation"));
        assertEquals(8, missing.size());
    }

    @Test
    void missingMandatoryFields_completeData_passes() {
        assertTrue(OfferHandoffService.missingMandatoryFields(
                "Asha Rao", "asha@mentis.example", "Software Engineer", "Engineering",
                "1200000", "2026-12-01", "Full-time", "Hyderabad").isEmpty());
    }

    @Test
    void salaryParsing_handlesFormats() {
        assertEquals(new java.math.BigDecimal("1200000"), OfferHandoffService.parseSalary("₹12,00,000"));
        assertEquals(new java.math.BigDecimal("1200000"), OfferHandoffService.parseSalary("1200000"));
        assertNull(OfferHandoffService.parseSalary("To be discussed"));
        assertNull(OfferHandoffService.parseSalary(null));
    }

    @Test
    void joiningDateParsing_isoAndFriendlyFormats() {
        assertEquals(java.time.LocalDate.of(2026, 12, 1), OfferHandoffService.parseJoiningDate("2026-12-01"));
        assertEquals(java.time.LocalDate.of(2026, 12, 1), OfferHandoffService.parseJoiningDate("01-Dec-2026"));
        assertEquals(java.time.LocalDate.of(2026, 12, 1), OfferHandoffService.parseJoiningDate("2026-12-01T00:00:00"));
        assertNull(OfferHandoffService.parseJoiningDate("not a date"));
        assertNull(OfferHandoffService.parseJoiningDate(null));
    }
}
