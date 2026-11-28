package com.mentis.hrms.service;

import com.mentis.hrms.model.LeavePolicy;
import com.mentis.hrms.model.LeaveType;
import com.mentis.hrms.model.enums.LeaveEntryType;
import com.mentis.hrms.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * Year-end / periodic leave jobs (REQ-4-13). ALL jobs are idempotent and re-runnable:
 * every posted row carries reference_type+reference_id and the ledger unique key prevents duplicates.
 */
@Service
public class LeaveAccrualService {

    private static final Logger logger = LoggerFactory.getLogger(LeaveAccrualService.class);

    @Autowired private LeavePolicyRepository policyRepository;
    @Autowired private LeaveTypeRepository leaveTypeRepository;
    @Autowired private LeaveLedgerService ledgerService;
    @Autowired private EmployeeRepository employeeRepository;

    @Transactional
    public int runMonthlyAccrual(int year, int month, String runBy) {
        List<LeavePolicy> policies = policyRepository.findAll();
        int posted = 0;
        String refId = "accrual-" + year + "-" + String.format("%02d", month);
        for (LeavePolicy p : policies) {
            if (!"MONTHLY".equalsIgnoreCase(p.getAccrualMethod())) continue;
            if (p.getEffectiveTo() != null && LocalDate.now().isAfter(p.getEffectiveTo())) continue;
            BigDecimal perMonth = p.getAnnualQuota().divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
            for (com.mentis.hrms.model.Employee emp : employeeRepository.findAll()) {
                if (!isEligible(emp, p)) continue;
                BigDecimal amount = prorated(emp, p, perMonth);
                if (amount.signum() <= 0) continue;
                var row = ledgerService.credit(emp.getEmployeeId(), p.getLeaveTypeId(), amount,
                        LeaveEntryType.CREDIT_ACCRUAL, LocalDate.of(year, month, 1),
                        "accrual_job", refId + ":" + emp.getEmployeeId() + ":" + p.getLeaveTypeId(),
                        "Monthly accrual " + year + "-" + month, runBy);
                if (row != null) posted++;
            }
        }
        logger.info("Monthly accrual {}-{} posted {} rows", year, month, posted);
        return posted;
    }

    @Transactional
    public int runYearlyUpfront(int year, String runBy) {
        List<LeavePolicy> policies = policyRepository.findAll();
        int posted = 0;
        String refId = "upfront-" + year;
        for (LeavePolicy p : policies) {
            if (!"YEARLY_UPFRONT".equalsIgnoreCase(p.getAccrualMethod())) continue;
            for (com.mentis.hrms.model.Employee emp : employeeRepository.findAll()) {
                if (!isEligible(emp, p)) continue;
                BigDecimal amount = "NONE".equalsIgnoreCase(p.getProbationRule()) && isOnProbation(emp)
                        ? BigDecimal.ZERO : p.getAnnualQuota();
                if (amount.signum() <= 0) continue;
                var row = ledgerService.credit(emp.getEmployeeId(), p.getLeaveTypeId(), amount,
                        LeaveEntryType.CREDIT_ACCRUAL, LocalDate.of(year, 1, 1),
                        "accrual_job", refId + ":" + emp.getEmployeeId() + ":" + p.getLeaveTypeId(),
                        "Yearly upfront accrual " + year, runBy);
                if (row != null) posted++;
            }
        }
        logger.info("Yearly upfront {} posted {} rows", year, posted);
        return posted;
    }

    @Transactional
    public int runCarryForward(int fromYear, String runBy) {
        List<LeavePolicy> policies = policyRepository.findAll();
        int posted = 0;
        String refId = "carryforward-" + fromYear + "-to-" + (fromYear + 1);
        for (LeavePolicy p : policies) {
            if (p.getCarryForwardLimit() == null || p.getCarryForwardLimit().signum() <= 0) continue;
            for (com.mentis.hrms.model.Employee emp : employeeRepository.findAll()) {
                BigDecimal balance = ledgerService.balance(emp.getEmployeeId(), p.getLeaveTypeId());
                BigDecimal carry = balance.min(p.getCarryForwardLimit());
                if (carry.signum() <= 0) continue;
                var row = ledgerService.credit(emp.getEmployeeId(), p.getLeaveTypeId(), carry,
                        LeaveEntryType.CREDIT_CARRY_FORWARD, LocalDate.of(fromYear + 1, 1, 1),
                        "carry_forward_job", refId + ":" + emp.getEmployeeId() + ":" + p.getLeaveTypeId(),
                        "Carry forward from " + fromYear, runBy);
                if (row != null) posted++;
            }
        }
        logger.info("Carry forward {} → {} posted {} rows", fromYear, fromYear + 1, posted);
        return posted;
    }

    private boolean isEligible(com.mentis.hrms.model.Employee emp, LeavePolicy p) {
        // Department filter (legacy free-text department matched against policy.department)
        if (p.getDepartment() != null && !p.getDepartment().isBlank()) {
            String d = emp.getDepartment();
            if (d == null || !d.equalsIgnoreCase(p.getDepartment().trim())) return false;
        }
        if (p.getLocationId() != null) {
            if (emp.getLocationId() == null || !emp.getLocationId().equals(p.getLocationId())) return false;
        }
        if (p.getEmploymentType() != null && !p.getEmploymentType().isBlank()) {
            String et = emp.getEmploymentType();
            if (et == null || !et.equalsIgnoreCase(p.getEmploymentType().trim())) return false;
        }
        if (isOnProbation(emp) && "NONE".equalsIgnoreCase(p.getProbationRule())) return false;
        return true;
    }

    private boolean isOnProbation(com.mentis.hrms.model.Employee emp) {
        try {
            String s = emp.getEmploymentStatus();
            return s != null && s.equalsIgnoreCase("PROBATION");
        } catch (Throwable t) {
            return false;
        }
    }

    private BigDecimal prorated(com.mentis.hrms.model.Employee emp, LeavePolicy p, BigDecimal monthly) {
        if ("PRORATA".equalsIgnoreCase(p.getProbationRule()) && isOnProbation(emp)) {
            return monthly.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        }
        return monthly;
    }
}
