package com.mentis.hrms.pm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.mentis.hrms.pm.dto.request.ChangeRequestPayload;
import com.mentis.hrms.pm.entity.Project;
import com.mentis.hrms.pm.entity.ProjectBaselineHistory;
import com.mentis.hrms.pm.entity.Team;
import com.mentis.hrms.pm.enums.ProjectStatus;
import com.mentis.hrms.pm.exception.BusinessRuleException;
import com.mentis.hrms.pm.exception.ResourceNotFoundException;
import com.mentis.hrms.pm.repository.ProjectBaselineHistoryRepository;
import com.mentis.hrms.pm.repository.ProjectRepository;
import com.mentis.hrms.pm.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Scope/schedule change control: validates a CHANGE_REQUEST, applies it to the project,
 * archives the replaced baseline and records the new one.
 * Never calls PmAccessService (the caller checks permissions).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChangeRequestService {

    private static final Set<ProjectStatus> CLOSED = EnumSet.of(ProjectStatus.COMPLETED, ProjectStatus.ARCHIVED);

    private final ProjectRepository projectRepository;
    private final TeamRepository teamRepository;
    private final ProjectService projectService;
    private final ProjectBaselineHistoryRepository historyRepository;

    /** Payload shape + rules against the current state of the project. Throws BusinessRuleException. */
    @Transactional(readOnly = true)
    public void validate(Long projectId, JsonNode payload) {
        Project pr = find(projectId);
        check(pr, ChangeRequestPayload.from(payload));
    }

    /**
     * Apply an approved change request. Runs inside the caller's transaction (decide()).
     * @return human summary "end date a → b; budget x → y" for the activity log
     */
    @Transactional
    public String apply(Long projectId, JsonNode payload, Long approvalId, Long actorId) {
        Project pr = find(projectId);
        ChangeRequestPayload p = ChangeRequestPayload.from(payload);
        check(pr, p); // re-check: the project may have changed since the request was filed

        // 1) keep the baseline that is about to be replaced
        if (pr.getBaselinedAt() != null) {
            historyRepository.save(ProjectBaselineHistory.builder()
                    .projectId(projectId)
                    .versionNo((int) historyRepository.countByProjectId(projectId) + 1)
                    .baselineStart(pr.getBaselineStart())
                    .baselineEnd(pr.getBaselineEnd())
                    .baselineBudget(pr.getBaselineBudget())
                    .baselinedAt(pr.getBaselinedAt())
                    .baselinedBy(pr.getBaselinedBy())
                    .supersededAt(LocalDateTime.now())
                    .changeRequestId(approvalId)
                    .reason(p.getReason())
                    .build());
        }

        // 2) change the live plan
        LocalDate oldEnd = pr.getEndDate();
        BigDecimal oldBudget = pr.getBudgetTotal();
        StringBuilder summary = new StringBuilder();
        if (p.getNewEndDate() != null) {
            pr.setEndDate(p.getNewEndDate());
            summary.append("end date ").append(oldEnd).append(" → ").append(p.getNewEndDate());
        }
        if (p.getNewBudget() != null) {
            pr.setBudgetTotal(p.getNewBudget());
            if (summary.length() > 0) summary.append("; ");
            summary.append("budget ").append(oldBudget == null ? null : oldBudget.toPlainString())
                    .append(" → ").append(p.getNewBudget().toPlainString());
        }
        projectRepository.save(pr);

        // 3) record the new baseline (same method as the "Save baseline" button)
        if (pr.getStartDate() != null && pr.getEndDate() != null) {
            projectService.saveBaseline(projectId, actorId);
        } else {
            log.warn("Change request {} applied but project {} has no start/end date: baseline not re-saved",
                    approvalId, projectId);
        }
        return summary + " (" + p.getReason() + ")";
    }

    /** Project owner + lead of the project's team: the people to keep informed. */
    @Transactional(readOnly = true)
    public Set<Long> stakeholders(Long projectId) {
        Set<Long> pks = new LinkedHashSet<>();
        projectRepository.findById(projectId).ifPresent(p -> {
            if (p.getOwnerId() != null) pks.add(p.getOwnerId());
            if (p.getTeamId() != null) {
                teamRepository.findById(p.getTeamId()).map(Team::getLeadEmployeeId).ifPresent(pks::add);
            }
        });
        return pks;
    }

    // ───────────────────────── helpers ─────────────────────────

    private void check(Project pr, ChangeRequestPayload p) {
        if (CLOSED.contains(pr.getStatus()))
            throw new BusinessRuleException("Completed or archived projects cannot take change requests");

        boolean changesEnd = p.getNewEndDate() != null && !p.getNewEndDate().equals(pr.getEndDate());
        boolean changesBudget = p.getNewBudget() != null
                && (pr.getBudgetTotal() == null || p.getNewBudget().compareTo(pr.getBudgetTotal()) != 0);
        if (!changesEnd && !changesBudget)
            throw new BusinessRuleException(
                    "This change request does not change anything: the end date and budget already have those values");

        if (p.getNewEndDate() != null && pr.getStartDate() != null && p.getNewEndDate().isBefore(pr.getStartDate()))
            throw new BusinessRuleException(
                    "The new end date cannot be before the project start date (" + pr.getStartDate() + ")");
    }

    private Project find(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + id));
    }
}