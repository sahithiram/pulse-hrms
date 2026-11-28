package com.mentis.hrms.service;

import com.mentis.hrms.dto.BackfillReportDTO;
import com.mentis.hrms.model.*;
import com.mentis.hrms.model.enums.MigrationIssueStatus;
import com.mentis.hrms.repository.*;
import com.mentis.hrms.util.SalaryParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REQ-1-07/08 backfill. Idempotent (checksum-based issue punch-list).<br>
 * Run on a RESTORED BACKUP first. Never against prod without a maintenance window.
 * Usage: set property hrms.backfill.execute=true (picked up by BackfillRunner in dev profile)
 * or call runFullBackfill() from a data-fix script.\n */
@Service
public class BackfillService {

    private static final Logger logger = LoggerFactory.getLogger(BackfillService.class);

    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private DesignationRepository designationRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private DataMigrationIssueRepository issueRepository;
    @Autowired private EmployeeJobHistoryRepository jobHistoryRepository;
    @Autowired private EmployeeSalaryHistoryRepository salaryHistoryRepository;

    @Transactional
    public BackfillReportDTO runFullBackfill() {
        List<Employee> employees = employeeRepository.findAll();
        int dept = 0, desig = 0, loc = 0, mgr = 0, sal = 0, issues = 0;
        List<String> issueSummaries = new ArrayList<>();

        Map<String, Department> departments = departmentRepository.findAll().stream()
                .collect(Collectors.toMap(d -> norm(d.getName()), d -> d, (a, b) -> a, HashMap::new));
        Map<String, Designation> designations = designationRepository.findAll().stream()
                .collect(Collectors.toMap(d -> norm(d.getName()), d -> d, (a, b) -> a, HashMap::new));
        Map<String, Location> locations = locationRepository.findAll().stream()
                .collect(Collectors.toMap(l -> norm(l.getName()), l -> l, (a, b) -> a, HashMap::new));
        Map<String, Employee> byBusinessId = employeeRepository.findAll().stream()
                .collect(Collectors.toMap(Employee::getEmployeeId, e -> e, (a, b) -> a, HashMap::new));

        for (Employee e : employees) {
            // Department
            if (isBlank(e.getDepartmentId()) && !isBlank(e.getDepartment())) {
                Department d = departments.get(norm(e.getDepartment()));
                if (d != null) { e.setDepartmentId(d.getId()); dept++; }
                else issues += recordIssue("DEPARTMENT", e.getEmployeeId(), "department", e.getDepartment(),
                        "No department with that (trimmed, case-insensitive) name", issueSummaries);
            }
            // Designation
            if (isBlank(e.getDesignationId()) && !isBlank(e.getDesignation())) {
                Designation d = designations.get(norm(e.getDesignation()));
                if (d != null) { e.setDesignationId(d.getId()); desig++; }
                else issues += recordIssue("DESIGNATION", e.getEmployeeId(), "designation", e.getDesignation(),
                        "No designation with that (trimmed, case-insensitive) name", issueSummaries);
            }
            // Location
            if (isBlank(e.getLocationId()) && !isBlank(e.getWorkLocation())) {
                Location l = locations.get(norm(e.getWorkLocation()));
                if (l != null) { e.setLocationId(l.getId()); loc++; }
                else issues += recordIssue("LOCATION", e.getEmployeeId(), "work_location", e.getWorkLocation(),
                        "No location with that (trimmed, case-insensitive) name", issueSummaries);
            }
            // Manager — match by employeeId, else unique (firstName lastName)
            if (e.getManagerEmployeeId() == null && !isBlank(e.getManager())) {
                Long managerId = resolveManager(e.getManager(), byBusinessId);
                if (managerId != null && !managerId.equals(e.getId())) { e.setManagerEmployeeId(managerId); mgr++; }
                else issues += recordIssue("MANAGER", e.getEmployeeId(), "manager", e.getManager(),
                        managerId == null ? "Cannot uniquely resolve manager text" : "Resolved to self — rejected", issueSummaries);
            }
            // Salary
            if (e.getSalaryAmount() == null && !isBlank(e.getSalary())) {
                Optional<BigDecimal> parsed = SalaryParser.parse(e.getSalary());
                if (parsed.isPresent()) { e.setSalaryAmount(parsed.get()); sal++; }
                else issues += recordIssue("SALARY", e.getEmployeeId(), "salary", e.getSalary(),
                        "Salary text not parseable as DECIMAL(15,2)", issueSummaries);
            }
            // Locate zero-history employees: create the opening job-history row so future diffs work.
            if (jobHistoryRepository.findFirstByEmployeeIdAndEffectiveToIsNull(e.getId()).isEmpty()) {
                EmployeeJobHistory open = new EmployeeJobHistory();
                open.setEmployeeId(e.getId());
                open.setDepartmentId(e.getDepartmentId());
                open.setDesignationId(e.getDesignationId());
                open.setLocationId(e.getLocationId());
                open.setManagerEmployeeId(e.getManagerEmployeeId());
                open.setEffectiveFrom(e.getStartDate() != null ? e.getStartDate() : LocalDate.of(1970, 1, 1));
                open.setEffectiveTo(null);
                open.setChangeReason("BACKFILL_BASELINE");
                open.setChangedBy(null);
                jobHistoryRepository.save(open);
            }
        }

        employeeRepository.saveAll(employees);
        // Salary history opening rows (only when we actually parsed a salary this run or one existed)
        for (Employee e : employees) {
            if (e.getSalaryAmount() != null
                    && salaryHistoryRepository.findByEmployeeIdOrderByEffectiveFromDesc(e.getId()).isEmpty()) {
                EmployeeSalaryHistory sh = new EmployeeSalaryHistory();
                sh.setEmployeeId(e.getId());
                sh.setAmount(e.getSalaryAmount());
                sh.setCurrency(normalizeCurrency(e.getCurrency()));
                sh.setEffectiveFrom(e.getStartDate() != null ? e.getStartDate() : LocalDate.of(1970, 1, 1));
                sh.setEffectiveTo(null);
                sh.setReason("BACKFILL_BASELINE");
                sh.setApprovedBy(null);
                salaryHistoryRepository.save(sh);
            }
        }

        BackfillReportDTO report = new BackfillReportDTO(
                employees.size(), dept, desig, loc, mgr, sal, issues, List.copyOf(issueSummaries));
        logger.info("Backfill complete: {} (issues={})", report, issues);
        return report;
    }

    private Long resolveManager(String raw, Map<String, Employee> byBusinessId) {
        String t = raw.trim();
        // 1) direct employee_id match
        Employee byId = byBusinessId.get(t);
        if (byId != null) return byId.getId();
        if (byId == null) byId = byBusinessId.get(t.toUpperCase());
        if (byId != null) return byId.getId();
        // 2) unique full-name match
        String normTarget = norm(t);
        List<Employee> matches = byBusinessId.values().stream()
                .filter(x -> norm(((x.getFirstName() == null ? "" : x.getFirstName()) + " "
                        + (x.getLastName() == null ? "" : x.getLastName())).trim()).equals(normTarget))
                .collect(Collectors.toList());
        return matches.size() == 1 ? matches.get(0).getId() : null;
    }

    private int recordIssue(String entityType, String ref, String field, String rawValue, String issue, List<String> summaries) {
        String checksum = sha256(entityType + "|" + ref + "|" + field + "|" + nullToEmpty(rawValue) + "|" + issue);
        if (issueRepository.existsByChecksum(checksum)) return 0; // idempotent rerun
        DataMigrationIssue i = new DataMigrationIssue();
        i.setEntityType(entityType);
        i.setEmployeeRef(ref);
        i.setFieldName(field);
        i.setRawValue(rawValue);
        i.setIssue(issue);
        i.setStatus(MigrationIssueStatus.OPEN);
        i.setChecksum(checksum);
        issueRepository.save(i);
        summaries.add(entityType + " " + ref + " " + field + "=\"" + rawValue + "\" → " + issue);
        return 1;
    }

    private static boolean isBlank(Long o) { return o == null; }
    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }
    private static String norm(String s) { return s == null ? "" : s.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " "); }
    private static String nullToEmpty(String s) { return s == null ? "" : s; }
    private static String normalizeCurrency(String c) {
        if (c == null || c.trim().isEmpty()) return "INR";
        String t = c.trim().toUpperCase();
        if (t.length() > 3) t = switch (t) { case "RS", "₹", "RUPEE", "RUPEES" -> "INR"; case "DOLLAR", "DOLLARS", "$" -> "USD"; default -> t.substring(0, 3); };
        return t;
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
