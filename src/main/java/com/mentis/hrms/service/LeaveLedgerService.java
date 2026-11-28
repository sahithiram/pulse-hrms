package com.mentis.hrms.service;

import com.mentis.hrms.model.LeaveLedger;
import com.mentis.hrms.model.enums.LeaveEntryType;
import com.mentis.hrms.repository.LeaveLedgerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * THE only writer of leave_ledger. Append-only; sign convention enforced here:
 *  - credits (CREDIT_*) are POSITIVE amounts
 *  - debits (DEBIT_*) are NEGATIVE amounts
 * Balance = SUM(all rows). Every write is idempotent via (reference_type, reference_id, entry_type, leave_type_id, employee_id).
 */
@Service
public class LeaveLedgerService {

    private static final Logger logger = LoggerFactory.getLogger(LeaveLedgerService.class);

    @Autowired
    private LeaveLedgerRepository ledgerRepository;

    public BigDecimal balance(String employeeId, Long leaveTypeId) {
        return nz(ledgerRepository.sumAmount(employeeId, leaveTypeId));
    }

    public BigDecimal balanceForEmployee(String employeeId) {
        return nz(ledgerRepository.sumAmountForEmployee(employeeId));
    }

    public List<LeaveLedger> ledger(String employeeId, Long leaveTypeId) {
        return ledgerRepository.findByEmployeeIdAndLeaveTypeIdOrderByEntryDateAscIdAsc(employeeId, leaveTypeId);
    }

    @Transactional
    public LeaveLedger credit(String employeeId, Long leaveTypeId, BigDecimal amount,
                              LeaveEntryType creditType, LocalDate entryDate,
                              String referenceType, String referenceId, String remarks, String createdBy) {
        requireCreditType(creditType);
        return write(employeeId, leaveTypeId, amount.abs(), creditType, entryDate, referenceType, referenceId, remarks, createdBy);
    }

    @Transactional
    public LeaveLedger debit(String employeeId, Long leaveTypeId, BigDecimal amount,
                             LeaveEntryType debitType, LocalDate entryDate,
                             String referenceType, String referenceId, String remarks, String createdBy) {
        requireDebitType(debitType);
        return write(employeeId, leaveTypeId, amount.abs().negate(), debitType, entryDate, referenceType, referenceId, remarks, createdBy);
    }

    /** Idempotent reversal of a previously written leave debit (cancellation). */
    @Transactional
    public LeaveLedger reverseDebit(String employeeId, Long leaveTypeId, BigDecimal amount,
                                    LocalDate entryDate, String referenceType, String referenceId,
                                    String remarks, String createdBy) {
        return write(employeeId, leaveTypeId, amount.abs(), LeaveEntryType.CREDIT_REVERSAL,
                entryDate, referenceType, referenceId, remarks, createdBy);
    }

    private LeaveLedger write(String employeeId, Long leaveTypeId, BigDecimal signedAmount,
                              LeaveEntryType type, LocalDate entryDate,
                              String referenceType, String referenceId, String remarks, String createdBy) {
        if (referenceType != null && referenceId != null
                && ledgerRepository.existsByReferenceTypeAndReferenceIdAndEntryTypeAndLeaveTypeIdAndEmployeeId(
                        referenceType, referenceId, type, leaveTypeId, employeeId)) {
            logger.info("Ledger idempotent skip: {} {} ref={}/{} emp={}", type, signedAmount, referenceType, referenceId, employeeId);
            return null;
        }
        LeaveLedger row = new LeaveLedger();
        row.setEmployeeId(employeeId);
        row.setLeaveTypeId(leaveTypeId);
        row.setEntryDate(entryDate != null ? entryDate : LocalDate.now());
        row.setEntryType(type);
        row.setAmount(signedAmount.setScale(2, java.math.RoundingMode.HALF_UP));
        row.setReferenceType(referenceType);
        row.setReferenceId(referenceId);
        row.setRemarks(remarks);
        row.setCreatedBy(createdBy);
        return ledgerRepository.save(row);
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    private static void requireCreditType(LeaveEntryType t) {
        if (t != LeaveEntryType.CREDIT_ACCRUAL && t != LeaveEntryType.CREDIT_CARRY_FORWARD
                && t != LeaveEntryType.CREDIT_MANUAL && t != LeaveEntryType.CREDIT_REVERSAL) {
            throw new IllegalArgumentException("not a credit type: " + t);
        }
    }

    private static void requireDebitType(LeaveEntryType t) {
        if (t != LeaveEntryType.DEBIT_LEAVE && t != LeaveEntryType.DEBIT_EXPIRY
                && t != LeaveEntryType.DEBIT_ENCASHMENT) {
            throw new IllegalArgumentException("not a debit type: " + t);
        }
    }
}
