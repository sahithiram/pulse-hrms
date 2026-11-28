package com.mentis.hrms;

import com.mentis.hrms.util.SalaryParser;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SalaryParserTest {

    @Test
    void accepts_plain_and_formatted_salaries() {
        assertEquals(Optional.of(new BigDecimal("50000.00")), SalaryParser.parse("50000"));
        assertEquals(Optional.of(new BigDecimal("50000.00")), SalaryParser.parse("50,000"));
        assertEquals(Optional.of(new BigDecimal("1250000.00")), SalaryParser.parse("₹12,50,000"));
        assertEquals(Optional.of(new BigDecimal("50000.50")), SalaryParser.parse("$50,000.50"));
        assertEquals(Optional.of(new BigDecimal("90000.00")), SalaryParser.parse("USD 90000"));
    }

    @Test
    void rejects_unparseable_and_ambiguous() {
        assertTrue(SalaryParser.parse("abc").isEmpty());
        assertTrue(SalaryParser.parse("").isEmpty());
        assertTrue(SalaryParser.parse(null).isEmpty());
        assertTrue(SalaryParser.parse("(50000)").isEmpty(), "accounting negatives must be rejected");
        assertTrue(SalaryParser.parse("-50000").isEmpty());
        assertTrue(SalaryParser.parse("10.20.30").isEmpty());
        assertTrue(SalaryParser.parse("10L").isEmpty());
    }
}
