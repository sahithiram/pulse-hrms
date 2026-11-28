package com.mentis.hrms.service;

import com.mentis.hrms.model.LeaveLedger;
import com.mentis.hrms.model.enums.LeaveEntryType;
import com.mentis.hrms.repository.LeaveLedgerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * NOTE: not executed yet — run with: mvn -Dtest=LeaveLedgerServiceTest test
 */
class LeaveLedgerServiceTest {

    private LeaveLedgerService service;
    private LeaveLedgerRepository repository;

    @BeforeEach
    void setUp() {
        service = new LeaveLedgerService();
        repository = Mockito.mock(LeaveLedgerRepository.class);
        try {
            var f = LeaveLedgerService.class.getDeclaredField("ledgerRepository");
            f.setAccessible(true);
            f.set(service, repository);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        when(repository.save(any(LeaveLedger.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void creditStoresPositiveAndDebitStoresNegative() {
        when(repository.existsByReferenceTypeAndReferenceIdAndEntryTypeAndLeaveTypeIdAndEmployeeId(
                any(), any(), any(), any(), any())).thenReturn(false);

        LeaveLedger credit = service.credit("E1", 1L, new BigDecimal("5.0"),
                LeaveEntryType.CREDIT_MANUAL, LocalDate.now(), "manual", "ref-1", "r", "hr");
        LeaveLedger debit = service.debit("E1", 1L, new BigDecimal("2.0"),
                LeaveEntryType.DEBIT_LEAVE, LocalDate.now(), "leave_request", "ref-2", "r", "sys");

        assertEquals(new BigDecimal("5.00"), credit.getAmount());
        assertEquals(new BigDecimal("-2.00"), debit.getAmount());
    }

    @Test
    void idempotentWriteSkipsDuplicateReference() {
        when(repository.existsByReferenceTypeAndReferenceIdAndEntryTypeAndLeaveTypeIdAndEmployeeId(
                eq("accrual_job"), eq("accrual-2026-11:E1:1"), eq(LeaveEntryType.CREDIT_ACCRUAL), eq(1L), eq("E1")))
                .thenReturn(true);

        LeaveLedger row = service.credit("E1", 1L, new BigDecimal("1.0"),
                LeaveEntryType.CREDIT_ACCRUAL, LocalDate.now(),
                "accrual_job", "accrual-2026-11:E1:1", "monthly", "scheduler");

        assertNull(row);
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsMismatchedEntryTypes() {
        assertThrows(IllegalArgumentException.class, () ->
                service.credit("E1", 1L, BigDecimal.ONE, LeaveEntryType.DEBIT_LEAVE,
                        LocalDate.now(), null, null, null, "x"));
        assertThrows(IllegalArgumentException.class, () ->
                service.debit("E1", 1L, BigDecimal.ONE, LeaveEntryType.CREDIT_ACCRUAL,
                        LocalDate.now(), null, null, null, "x"));
    }
}
