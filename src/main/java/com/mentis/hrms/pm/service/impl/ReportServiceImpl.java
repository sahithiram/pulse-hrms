package com.mentis.hrms.pm.service.impl;

import com.mentis.hrms.model.Employee;
import com.mentis.hrms.pm.dto.response.ReportDtos.*;
import com.mentis.hrms.pm.dto.response.WorkloadResponse;
import com.mentis.hrms.pm.entity.Issue;
import com.mentis.hrms.pm.entity.Project;
import com.mentis.hrms.pm.entity.Risk;
import com.mentis.hrms.pm.entity.Sprint;
import com.mentis.hrms.pm.entity.Task;
import com.mentis.hrms.pm.enums.TaskStatus;
import com.mentis.hrms.pm.repository.*;
import com.mentis.hrms.pm.service.ReportService;
import com.mentis.hrms.pm.service.WorkloadService;
import com.mentis.hrms.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportServiceImpl implements ReportService {

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final SprintRepository sprintRepository;
    private final IssueRepository issueRepository;
    private final RiskRepository riskRepository;
    private final EmployeeRepository employeeRepository;
    private final WorkloadService workloadService;

    // ── BUDGET ──
    @Override
    @Transactional(readOnly = true)
    public List<BudgetRow> budget(Long projectId) {
        List<Project> projects = (projectId == null)
                ? projectRepository.findAll()
                : List.of(projectRepository.findById(projectId).orElse(null));
        List<BudgetRow> out = new ArrayList<>();
        for (Project p : projects) {
            if (p == null) continue;
            double total = p.getBudgetTotal() != null ? p.getBudgetTotal().doubleValue() : 0.0;
            double spent = p.getBudgetSpent() != null ? p.getBudgetSpent().doubleValue() : 0.0;
            double remaining = total - spent;
            double spentPct = total > 0 ? spent * 100.0 / total : 0.0;
            String status = spentPct > 100 ? "OVER" : spentPct > 85 ? "WARN" : "OK";
            out.add(BudgetRow.builder()
                    .projectId(p.getId()).projectName(p.getName())
                    .budgetTotal(round2(total)).budgetSpent(round2(spent)).remaining(round2(remaining))
                    .spentPct(round1(spentPct)).status(status)
                    .build());
        }
        return out;
    }

    // ── VELOCITY ──
    @Override
    @Transactional(readOnly = true)
    public List<VelocityRow> velocity(Long projectId, int lastSprints) {
        List<Sprint> sprints = sprintRepository.findByProjectIdOrderByStartDateDesc(projectId);
        if (sprints.size() > lastSprints) sprints = sprints.subList(0, lastSprints);
        List<VelocityRow> out = new ArrayList<>();
        for (Sprint s : sprints) {
            List<Task> tasks = taskRepository.findBySprintId(s.getId());
            long committed = tasks.stream().mapToLong(t -> t.getStoryPoints() != null ? t.getStoryPoints() : 0).sum();
            long completed = tasks.stream()
                    .filter(t -> t.getStatus() == TaskStatus.DONE)
                    .mapToLong(t -> t.getStoryPoints() != null ? t.getStoryPoints() : 0).sum();
            double pct = committed > 0 ? completed * 100.0 / committed : 0.0;
            out.add(VelocityRow.builder()
                    .sprintId(s.getId()).sprintName(s.getName())
                    .startDate(s.getStartDate()).endDate(s.getEndDate())
                    .committedPoints(committed).completedPoints(completed)
                    .completionPct(round1(pct))
                    .build());
        }
        Collections.reverse(out);   // oldest → newest
        return out;
    }

    // ── QUALITY ──
    @Override
    @Transactional(readOnly = true)
    public QualityReport quality(Long projectId) {
        return projectId == null
                ? buildQuality(issueRepository.findAll(), null)
                : buildQuality(issueRepository.findByProjectIdOrderByCreatedAtDesc(projectId), List.of(projectId));
    }

    @Override
    @Transactional(readOnly = true)
    public QualityReport qualityForProjects(Collection<Long> projectIds) {
        List<Issue> issues = new ArrayList<>();
        for (Long id : projectIds) {
            issues.addAll(issueRepository.findByProjectIdOrderByCreatedAtDesc(id));
        }
        return buildQuality(issues, projectIds);
    }

    private QualityReport buildQuality(List<Issue> issues, Collection<Long> scope) {
        long open = issues.stream().filter(i ->
                !"RESOLVED".equalsIgnoreCase(i.getStatus()) && !"CLOSED".equalsIgnoreCase(i.getStatus())).count();
        long critical = issues.stream()
                .filter(i -> "CRITICAL".equalsIgnoreCase(i.getSeverity()))
                .filter(i -> !"RESOLVED".equalsIgnoreCase(i.getStatus()) && !"CLOSED".equalsIgnoreCase(i.getStatus()))
                .count();

        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        List<Issue> resolvedLast30 = issueRepository.findResolvedSince(cutoff).stream()
                .filter(i -> scope == null || scope.contains(i.getProjectId()))
                .collect(Collectors.toList());

        long totalResolutionMinutes = 0;
        long countResolved = 0;
        for (Issue i : resolvedLast30) {
            if (i.getResolvedAt() != null && i.getCreatedAt() != null) {
                totalResolutionMinutes += Duration.between(i.getCreatedAt(), i.getResolvedAt()).toMinutes();
                countResolved++;
            }
        }
        double avgDays = countResolved > 0 ? (totalResolutionMinutes / 60.0 / 24.0) / countResolved : 0.0;

        Map<String, Long> sevMap = issues.stream()
                .collect(Collectors.groupingBy(i -> i.getSeverity() == null ? "MEDIUM" : i.getSeverity(), Collectors.counting()));
        Map<String, Long> typeMap = issues.stream()
                .collect(Collectors.groupingBy(i -> i.getType() == null ? "BUG" : i.getType(), Collectors.counting()));

        return QualityReport.builder()
                .openIssues(open).criticalIssues(critical)
                .resolvedLast30d(resolvedLast30.size())
                .avgResolutionDays(round1(avgDays))
                .bySeverity(sevMap.entrySet().stream()
                        .map(e -> new SeverityCount(e.getKey(), e.getValue()))
                        .collect(Collectors.toList()))
                .byType(typeMap.entrySet().stream()
                        .map(e -> new TypeCount(e.getKey(), e.getValue()))
                        .collect(Collectors.toList()))
                .build();
    }

    // ── RISK ──
    @Override
    @Transactional(readOnly = true)
    public RiskReport risk(Long projectId) {
        List<Risk> risks = (projectId == null)
                ? riskRepository.findAll()
                : riskRepository.findByProjectIdOrderByScoreDesc(projectId);
        return buildRisk(risks);
    }

    @Override
    @Transactional(readOnly = true)
    public RiskReport riskForProjects(Collection<Long> projectIds) {
        List<Risk> risks = new ArrayList<>();
        for (Long id : projectIds) {
            risks.addAll(riskRepository.findByProjectIdOrderByScoreDesc(id));
        }
        return buildRisk(risks);
    }

    private RiskReport buildRisk(List<Risk> risks) {
        long high = risks.stream().filter(r -> r.getScore() != null && r.getScore() >= 15).count();
        long open = risks.stream().filter(r -> "OPEN".equalsIgnoreCase(r.getStatus())).count();
        long mitigating = risks.stream().filter(r -> "MITIGATING".equalsIgnoreCase(r.getStatus())).count();
        Map<String, Long> sev = new LinkedHashMap<>();
        sev.put("HIGH", risks.stream().filter(r -> r.getScore() >= 15).count());
        sev.put("MEDIUM", risks.stream().filter(r -> r.getScore() >= 9 && r.getScore() < 15).count());
        sev.put("LOW", risks.stream().filter(r -> r.getScore() < 9).count());
        return RiskReport.builder()
                .totalRisks(risks.size()).highSeverity(high).open(open).mitigating(mitigating)
                .bySeverity(sev.entrySet().stream()
                        .map(e -> new SeverityCount(e.getKey(), e.getValue()))
                        .collect(Collectors.toList()))
                .build();
    }

    // ── UTILIZATION ──
    @Override
    @Transactional(readOnly = true)
    public List<UtilizationRow> utilization(LocalDate from, LocalDate to) {
        List<Employee> emps = employeeRepository.findAll();
        List<Long> pks = emps.stream().map(Employee::getId).collect(Collectors.toList());
        List<WorkloadResponse> rows = workloadService.forEmployees(pks, from, to);
        return rows.stream().map(w -> UtilizationRow.builder()
                .employeePk(w.getEmployeePk()).employeeId(w.getEmployeeId())
                .employeeName(w.getEmployeeName()).department(w.getDepartment())
                .capacityHours(w.getCapacityHours())
                .allocatedHours(w.getAllocatedHours())
                .loggedHours(w.getLoggedHours())
                .utilizationPct(w.getUtilizationPct())
                .status(w.getStatus())
                .build()).collect(Collectors.toList());
    }

    // ── CSV ──
    @Override public String budgetCsv(Long projectId) {
        StringBuilder b = new StringBuilder();
        b.append("Project,Budget,Spent,Remaining,SpentPct,Status\n");
        for (BudgetRow r : budget(projectId)) {
            b.append(csv(r.getProjectName())).append(',')
                    .append(r.getBudgetTotal()).append(',')
                    .append(r.getBudgetSpent()).append(',')
                    .append(r.getRemaining()).append(',')
                    .append(r.getSpentPct()).append(',')
                    .append(r.getStatus()).append('\n');
        }
        return b.toString();
    }

    @Override public String velocityCsv(Long projectId, int lastSprints) {
        StringBuilder b = new StringBuilder();
        b.append("Sprint,Start,End,Committed,Completed,CompletionPct\n");
        for (VelocityRow r : velocity(projectId, lastSprints)) {
            b.append(csv(r.getSprintName())).append(',')
                    .append(r.getStartDate()).append(',')
                    .append(r.getEndDate()).append(',')
                    .append(r.getCommittedPoints()).append(',')
                    .append(r.getCompletedPoints()).append(',')
                    .append(r.getCompletionPct()).append('\n');
        }
        return b.toString();
    }

    @Override public String qualityCsv(Long projectId) {
        QualityReport r = quality(projectId);
        StringBuilder b = new StringBuilder();
        b.append("Metric,Value\n");
        b.append("OpenIssues,").append(r.getOpenIssues()).append('\n');
        b.append("CriticalIssues,").append(r.getCriticalIssues()).append('\n');
        b.append("ResolvedLast30d,").append(r.getResolvedLast30d()).append('\n');
        b.append("AvgResolutionDays,").append(r.getAvgResolutionDays()).append('\n');
        for (SeverityCount s : r.getBySeverity()) {
            b.append("Severity:").append(s.getSeverity()).append(',').append(s.getCount()).append('\n');
        }
        for (TypeCount t : r.getByType()) {
            b.append("Type:").append(t.getType()).append(',').append(t.getCount()).append('\n');
        }
        return b.toString();
    }

    @Override public String riskCsv(Long projectId) {
        RiskReport r = risk(projectId);
        StringBuilder b = new StringBuilder();
        b.append("Metric,Value\n");
        b.append("TotalRisks,").append(r.getTotalRisks()).append('\n');
        b.append("HighSeverity,").append(r.getHighSeverity()).append('\n');
        b.append("Open,").append(r.getOpen()).append('\n');
        b.append("Mitigating,").append(r.getMitigating()).append('\n');
        for (SeverityCount s : r.getBySeverity()) {
            b.append("Severity:").append(s.getSeverity()).append(',').append(s.getCount()).append('\n');
        }
        return b.toString();
    }

    @Override public String utilizationCsv(LocalDate from, LocalDate to) {
        StringBuilder b = new StringBuilder();
        b.append("EmployeeId,Employee,Department,CapacityH,AllocatedH,LoggedH,UtilizationPct,Status\n");
        for (UtilizationRow r : utilization(from, to)) {
            b.append(csv(r.getEmployeeId())).append(',')
                    .append(csv(r.getEmployeeName())).append(',')
                    .append(csv(r.getDepartment())).append(',')
                    .append(r.getCapacityHours()).append(',')
                    .append(r.getAllocatedHours()).append(',')
                    .append(r.getLoggedHours()).append(',')
                    .append(r.getUtilizationPct()).append(',')
                    .append(r.getStatus()).append('\n');
        }
        return b.toString();
    }

    private String csv(String s) { return s == null ? "" : "\"" + s.replace("\"", "\"\"") + "\""; }
    private double round1(double x) { return Math.round(x * 10.0) / 10.0; }
    private double round2(double x) { return Math.round(x * 100.0) / 100.0; }
}