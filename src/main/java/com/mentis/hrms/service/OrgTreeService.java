package com.mentis.hrms.service;

import com.mentis.hrms.dto.EmployeeSummaryDTO;
import com.mentis.hrms.dto.HistoryEntryDTO;
import com.mentis.hrms.dto.OrgChartNodeDTO;
import com.mentis.hrms.model.Employee;
import com.mentis.hrms.model.EmployeeJobHistory;
import com.mentis.hrms.model.EmployeeSalaryHistory;
import com.mentis.hrms.repository.EmployeeJobHistoryRepository;
import com.mentis.hrms.repository.EmployeeRepository;
import com.mentis.hrms.repository.EmployeeSalaryHistoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/** Read-side of the org model. Pure reads, no entity leakage (DTOs only, R8). */
@Service
@Transactional(readOnly = true)
public class OrgTreeService {

    private static final int MAX_TEAM_DEPTH = 6;

    private final EmployeeRepository employeeRepository;
    private final EmployeeJobHistoryRepository jobHistoryRepository;
    private final EmployeeSalaryHistoryRepository salaryHistoryRepository;

    @Autowired
    public OrgTreeService(EmployeeRepository employeeRepository,
                          EmployeeJobHistoryRepository jobHistoryRepository,
                          EmployeeSalaryHistoryRepository salaryHistoryRepository) {
        this.employeeRepository = employeeRepository;
        this.jobHistoryRepository = jobHistoryRepository;
        this.salaryHistoryRepository = salaryHistoryRepository;
    }

    public List<EmployeeSummaryDTO> getDirectReports(Long managerId) {
        // Recursive fetch avoided: callers decide depth. Direct reports only here.
        return employeeRepository.findAll().stream()
                .filter(e -> managerId.equals(e.getManagerEmployeeId()))
                .sorted(Comparator.comparing(e -> e.getEmployeeName() == null ? "" : e.getEmployeeName()))
                .map(this::toSummary)
                .collect(Collectors.toList());
    }

    public OrgChartNodeDTO getOrgSubtree(Long rootId, int depth) {
        if (depth > MAX_TEAM_DEPTH) depth = MAX_TEAM_DEPTH;
        Employee root = employeeRepository.findById(rootId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + rootId));
        return buildNode(root, depth);
    }

    public OrgChartNodeDTO getOrgChart() {
        Set<Long> hasManager = employeeRepository.findAll().stream()
                .map(Employee::getManagerEmployeeId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<Employee> roots = employeeRepository.findAll().stream()
                .filter(e -> e.getManagerEmployeeId() == null)
                .collect(Collectors.toList());
        // Forest → return a synthetic root view by building the first-level tree under each root.
        // Simpler contract: return the biggest root (most reports) — HR top.
        Employee top = roots.stream()
                .max(Comparator.comparing(r -> countReports(r.getId(), new HashSet<>(), 0)))
                .orElse(roots.isEmpty() ? null : roots.get(0));
        if (top == null) throw new IllegalStateException("No employees in org");
        return buildNode(top, MAX_TEAM_DEPTH);
    }

    private int countReports(Long id, Set<Long> seen, int depth) {
        if (depth > MAX_TEAM_DEPTH || !seen.add(id)) return 0;
        int n = 0;
        for (Employee e : employeeRepository.findAll()) {
            if (id.equals(e.getManagerEmployeeId())) n += 1 + countReports(e.getId(), seen, depth + 1);
        }
        return n;
    }

    private OrgChartNodeDTO buildNode(Employee e, int depth) {
        List<OrgChartNodeDTO> children = depth <= 0
                ? List.of()
                : employeeRepository.findAll().stream()
                    .filter(x -> e.getId().equals(x.getManagerEmployeeId()))
                    .map(x -> buildNode(x, depth - 1))
                    .collect(Collectors.toList());
        return new OrgChartNodeDTO(
                e.getId(),
                e.getEmployeeId(),
                e.getFullName(),
                e.getDesignation(),   // legacy text — replaced by designation name in Sprint 6 UI pass
                e.getDepartment(),    // legacy text — see note above
                e.getEmploymentStatus(),
                e.isActive() && !Boolean.FALSE.equals(e.getIsActive()),
                children);
    }

    public List<HistoryEntryDTO> getHistory(Long employeeId) {
        List<HistoryEntryDTO> out = new ArrayList<>();
        for (EmployeeJobHistory j : jobHistoryRepository.findByEmployeeIdOrderByEffectiveFromDesc(employeeId)) {
            String detail = "dept=" + j.getDepartmentId() + ", desig=" + j.getDesignationId()
                    + ", loc=" + j.getLocationId() + ", mgr=" + j.getManagerEmployeeId()
                    + ", mode=" + j.getEmploymentMode() + ", work=" + j.getWorkMode();
            out.add(new HistoryEntryDTO("JOB", detail, null, null, j.getEffectiveFrom(), j.getEffectiveTo(),
                    j.getChangeReason(), j.getChangedBy()));
        }
        for (EmployeeSalaryHistory s : salaryHistoryRepository.findByEmployeeIdOrderByEffectiveFromDesc(employeeId)) {
            out.add(new HistoryEntryDTO("SALARY", "salary change", s.getAmount(), s.getCurrency(),
                    s.getEffectiveFrom(), s.getEffectiveTo(), s.getReason(), s.getApprovedBy()));
        }
        out.sort(Comparator.comparing(HistoryEntryDTO::effectiveFrom, Comparator.nullsLast(Comparator.reverseOrder())));
        return out;
    }

    private EmployeeSummaryDTO toSummary(Employee e) {
        return new EmployeeSummaryDTO(e.getId(), e.getEmployeeId(), e.getFullName(), e.getEmail(),
                e.getDesignation(), e.getDepartment(), e.getEmploymentStatus(), e.getWorkMode());
    }
}
