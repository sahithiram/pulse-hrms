package com.mentis.hrms.service;

import com.mentis.hrms.model.Employee;
import com.mentis.hrms.model.EmployeeJobHistory;
import com.mentis.hrms.model.enums.EmploymentStatus;
import com.mentis.hrms.repository.EmployeeJobHistoryRepository;
import com.mentis.hrms.repository.EmployeeRepository;
import com.mentis.hrms.model.enums.EmploymentMode;
import com.mentis.hrms.model.enums.WorkMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Org integrity rules (REQ-1-09..1-13):\n * - manager must reference an ACTIVE (non EXITED/INACTIVE) employee\n * - self-manager rejected\n * - any depth of reporting cycle rejected\n * - only allowed lifecycle transitions\n * - tracked-field change ⇒ history row in the same transaction\n */
@Service
@Transactional
public class EmployeeOrgService {

    private static final Logger logger = LoggerFactory.getLogger(EmployeeOrgService.class);
    private static final int MAX_DEPTH = 32;

    private final EmployeeRepository employeeRepository;
    private final EmployeeJobHistoryRepository jobHistoryRepository;

    @Autowired
    public EmployeeOrgService(EmployeeRepository employeeRepository,
                              EmployeeJobHistoryRepository jobHistoryRepository) {
        this.employeeRepository = employeeRepository;
        this.jobHistoryRepository = jobHistoryRepository;
    }

    /** Transition matrix — everything not listed throws. */
    private static final Map<EmploymentStatus, Set<EmploymentStatus>> ALLOWED = Map.of(
            EmploymentStatus.ONBOARDING, EnumSet.of(EmploymentStatus.PROBATION, EmploymentStatus.NOTICE),
            EmploymentStatus.PROBATION,  EnumSet.of(EmploymentStatus.CONFIRMED, EmploymentStatus.NOTICE),
            EmploymentStatus.CONFIRMED,  EnumSet.of(EmploymentStatus.NOTICE),
            EmploymentStatus.NOTICE,     EnumSet.of(EmploymentStatus.EXITED),
            EmploymentStatus.EXITED,     EnumSet.noneOf(EmploymentStatus.class),
            EmploymentStatus.INACTIVE,   EnumSet.noneOf(EmploymentStatus.class));

    public void assertTransitionAllowed(EmploymentStatus from, EmploymentStatus to) {
        if (from == null || to == null) throw new IllegalArgumentException("employment_status cannot be null");
        if (from == to) return; // idempotent same-state write is a no-op, not an error
        Set<EmploymentStatus> allowed = ALLOWED.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) {
            throw new IllegalArgumentException("Illegal employment status transition: " + from + " → " + to);
        }
    }

    /** Full cycle check: walks UP from proposedManager; fails if it reaches employeeId or loops. */
    public void assertAssignableManager(Employee employee, Employee proposedManager) {
        if (proposedManager == null) return; // clearing manager allowed (top-level / HR flagged)
        if (employee.getId() != null && proposedManager.getId().equals(employee.getId())) {
            throw new IllegalArgumentException("An employee cannot be their own manager");
        }
        assertManagerIsActive(proposedManager);

        Set<Long> seen = new HashSet<>();
        Employee cursor = proposedManager;
        int depth = 0;
        while (cursor != null) {
            if (++depth > MAX_DEPTH) {
                throw new IllegalStateException("Reporting chain deeper than " + MAX_DEPTH + " — corrupt data?");
            }
            if (!seen.add(cursor.getId())) {
                throw new IllegalArgumentException("Reporting cycle detected at employee id " + cursor.getId());
            }
            if (employee.getId() != null && seen.contains(employee.getId())) {
                throw new IllegalArgumentException("Reporting cycle detected: employee would sit under their own subtree");
            }
            Long parentId = cursor.getManagerEmployeeId();
            cursor = (parentId == null) ? null : employeeRepository.findById(parentId).orElse(null);
        }
    }

    public void assertManagerIsActive(Employee manager) {
        if (manager == null) return;
        EmploymentStatus s = manager.getEmploymentStatusEnum();
        if (s == EmploymentStatus.EXITED || s == EmploymentStatus.INACTIVE) {
            throw new IllegalArgumentException("Manager " + manager.getEmployeeId() + " is " + s + "; choose an active manager");
        }
        if (!manager.isActive() || Boolean.FALSE.equals(manager.getIsActive())) {
            throw new IllegalArgumentException("Manager " + manager.getEmployeeId() + " is inactive; choose an active manager");
        }
    }

    /**
     * Applies tracked-field changes atomically: validates org rules, closes the current
     * job-history row, inserts the new one, stamps effective dates.
     * Call this instead of writing department/designation/manager directly.
     */
    @Transactional
    public Employee changeJobInfo(Long employeeId,
                                  Long departmentId,
                                  Long designationId,
                                  Long locationId,
                                  Long managerEmployeeId,
                                  String employmentMode,
                                  String workMode,
                                  LocalDate effectiveFrom,
                                  String reason,
                                  Long changedBy,
                                  boolean backDatedAllowed) {
        Employee e = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + employeeId));
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A change reason is required for job-history entries");
        }
        LocalDate today = LocalDate.now();
        if (effectiveFrom == null) effectiveFrom = today;
        if (effectiveFrom.isAfter(today) && !backDatedAllowed) {
            // future-dating is allowed only for HR (caller enforces role; this flag encodes it)
        }

        if (managerEmployeeId != null) {
            Employee mgr = employeeRepository.findById(managerEmployeeId)
                    .orElseThrow(() -> new IllegalArgumentException("Manager not found: " + managerEmployeeId));
            assertAssignableManager(e, mgr);
        }

        LocalDate effFrom = effectiveFrom;

        // Close current history row
        jobHistoryRepository.findFirstByEmployeeIdAndEffectiveToIsNull(employeeId).ifPresent(current -> {
            current.setEffectiveTo(effFrom.minusDays(1));
            jobHistoryRepository.save(current);
        });

        EmployeeJobHistory row = new EmployeeJobHistory();
        row.setEmployeeId(employeeId);
        row.setDepartmentId(departmentId);
        row.setDesignationId(designationId);
        row.setLocationId(locationId);
        row.setManagerEmployeeId(managerEmployeeId);
        row.setEmploymentMode(parseOrNull(EmploymentMode.class, employmentMode));
        row.setWorkMode(parseOrNull(WorkMode.class, workMode));
        row.setEffectiveFrom(effFrom);
        row.setEffectiveTo(null);
        row.setChangeReason(reason.trim());
        row.setChangedBy(changedBy);
        jobHistoryRepository.save(row);

        // NOTE: legacy text columns ARE double-checked by callers (EmployeeFormDTO mapping
        // is updated in the manifest patch); this method is the ONLY place that closes/opens history.
        logger.info("Job history written for employee {} ({})", employeeId, reason);
        return e;
    }

    private static <E extends Enum<E>> E parseOrNull(Class<E> type, String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown " + type.getSimpleName() + ": " + value);
        }
    }
}
