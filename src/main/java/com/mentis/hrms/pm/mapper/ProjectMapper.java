package com.mentis.hrms.pm.mapper;

import com.mentis.hrms.pm.dto.request.CreateProjectRequest;
import com.mentis.hrms.pm.dto.response.ProjectResponse;
import com.mentis.hrms.pm.dto.response.ProjectSummaryResponse;
import com.mentis.hrms.pm.entity.Project;
import com.mentis.hrms.pm.enums.ProjectStatus;
import org.springframework.stereotype.Component;

@Component
public class ProjectMapper {

    public Project toEntity(CreateProjectRequest req, Long createdBy) {
        return Project.builder()
                .name(req.getName())
                .code(req.getCode())
                .description(req.getDescription())
                .ownerId(req.getOwnerId())
                .departmentId(req.getDepartmentId())
                .clientId(req.getClientId())
                .priority(req.getPriority())
                .startDate(req.getStartDate())
                .endDate(req.getEndDate())
                .budgetTotal(req.getBudgetTotal())
                .status(ProjectStatus.PLANNING)
                .progressPct(0)
                .teamId(req.getTeamId())
                .reviewRequired(req.getReviewRequired() == null ? Boolean.TRUE : req.getReviewRequired())
                .createdBy(createdBy)
                .build();
    }

    public ProjectResponse toResponse(Project p, String clientName) {
        return ProjectResponse.builder()
                .id(p.getId())
                .code(p.getCode())
                .name(p.getName())
                .description(p.getDescription())
                .ownerId(p.getOwnerId())
                .departmentId(p.getDepartmentId())
                .clientId(p.getClientId())
                .clientName(clientName)
                .status(p.getStatus())
                .health(p.getHealth())
                .priority(p.getPriority())
                .startDate(p.getStartDate())
                .endDate(p.getEndDate())
                .budgetTotal(p.getBudgetTotal())
                .budgetSpent(p.getBudgetSpent())
                .progressPct(p.getProgressPct())
                .createdAt(p.getCreatedAt())
                .teamId(p.getTeamId())
                .updatedAt(p.getUpdatedAt())
                .baselineStart(p.getBaselineStart())
                .baselineEnd(p.getBaselineEnd())
                .baselineBudget(p.getBaselineBudget())
                .baselinedAt(p.getBaselinedAt())
                .healthMode(p.getHealthMode() == null ? "AUTO" : p.getHealthMode().name())
                .healthReason(p.getHealthReason())
                .healthUpdatedAt(p.getHealthUpdatedAt())
                .lessonsLearned(p.getLessonsLearned())
                .reviewRequired(Boolean.TRUE.equals(p.getReviewRequired()))
                .build();
    }

    public ProjectSummaryResponse toSummary(Project p, String clientName) {
        return ProjectSummaryResponse.builder()
                .id(p.getId())
                .code(p.getCode())
                .name(p.getName())
                .status(p.getStatus())
                .health(p.getHealth())
                .priority(p.getPriority())
                .progressPct(p.getProgressPct())
                .clientId(p.getClientId())
                .clientName(clientName)
                .budgetTotal(p.getBudgetTotal())
                .budgetSpent(p.getBudgetSpent())
                .startDate(p.getStartDate())
                .endDate(p.getEndDate())
                // FIX: expose ownerId/teamId so the frontend "Mine" filter and manager column work
                .ownerId(p.getOwnerId())
                .teamId(p.getTeamId())
                .build();
    }
}