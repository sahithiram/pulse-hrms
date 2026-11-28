package com.mentis.hrms.service;

import com.mentis.hrms.model.Employee;
import com.mentis.hrms.model.enums.DelegateScope;
import com.mentis.hrms.repository.EmployeeDelegateRepository;
import com.mentis.hrms.repository.EmployeeRepository;
import com.mentis.hrms.util.Roles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

/** Object-level authorization (REQ-2-04). All methods default to DENY on unknown input. */
@Service
@Transactional(readOnly = true)
public class AccessPolicy {

    private static final int MAX_TREE_WALK = 32;

    private final EmployeeRepository employeeRepository;
    private final EmployeeDelegateRepository delegateRepository;

    @Autowired
    public AccessPolicy(EmployeeRepository employeeRepository, EmployeeDelegateRepository delegateRepository) {
        this.employeeRepository = employeeRepository;
        this.delegateRepository = delegateRepository;
    }

    /** employee can view self; HR/SUPER_ADMIN all; manager can view anyone under them in the tree. */
    public boolean canViewEmployee(String sessionRole, Long sessionEmployeeId, Employee target) {
        if (target == null) return false;
        if (Roles.isAnyHrOrSuper(sessionRole)) return true;
        if (sessionEmployeeId == null) return false;
        if (sessionEmployeeId.equals(target.getId())) return true;
        return isInManagerTree(sessionEmployeeId, target.getId());
    }

    /** Salary: HR_MANAGER / SUPER_ADMIN only. HR can VIEW but not edit/see figures here — figure
        visibility happens in DTO masking; policy governs the ACTION. */
    public boolean canEditSalary(String sessionRole) {
        return Roles.isSuperAdmin(sessionRole) || Roles.is(sessionRole, "HR_MANAGER");
    }

    public boolean canViewSalary(String sessionRole) {
        return Roles.isAnyHrOrSuper(sessionRole);
    }

    /** Leave approval: own manager, an active delegate of that manager (scope LEAVE_APPROVAL/ALL), or HR. */
    public boolean canApproveLeave(String sessionRole, Long sessionEmployeeId, Employee requester) {
        if (Roles.isAnyHrOrSuper(sessionRole)) return true;
        if (sessionEmployeeId == null || requester == null) return false;
        Long mgrId = requester.getManagerEmployeeId();
        if (mgrId == null) return false;
        if (sessionEmployeeId.equals(mgrId)) return true;
        // delegate-of-manager path
        List<com.mentis.hrms.model.EmployeeDelegate> active = delegateRepository
                .findByDelegateIdAndFromDateLessThanEqualAndToDateGreaterThanEqualAndScopeIn(
                        sessionEmployeeId, LocalDate.now(), LocalDate.now(),
                        List.of(DelegateScope.LEAVE_APPROVAL, DelegateScope.ALL));
        return active.stream().anyMatch(d -> mgrId.equals(d.getDelegatorId()));
    }

    /** Attendance edit: HR/SUPER_ADMIN only until Sprint 5 adds regularization flow for managers. */
    public boolean canEditAttendance(String sessionRole) {
        return Roles.isAnyHrOrSuper(sessionRole);
    }

    /** Does `candidateRootId` sit anywhere above `employeeId` in the manager chain? */
    public boolean isInManagerTree(Long candidateRootId, Long employeeId) {
        Long cursor = employeeId;
        Set<Long> seen = new HashSet<>();
        for (int depth = 0; depth < MAX_TREE_WALK && cursor != null; depth++) {
            if (!seen.add(cursor)) return false; // cycle — treat as not-in-tree
            Optional<Employee> e = employeeRepository.findById(cursor);
            if (e.isEmpty()) return false;
            Long parent = e.get().getManagerEmployeeId();
            if (parent == null) return false;
            if (parent.equals(candidateRootId)) return true;
            cursor = parent;
        }
        return false;
    }
}
