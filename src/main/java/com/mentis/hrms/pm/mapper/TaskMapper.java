package com.mentis.hrms.pm.mapper;

import com.mentis.hrms.pm.dto.request.CreateTaskRequest;
import com.mentis.hrms.pm.dto.response.TaskResponse;
import com.mentis.hrms.pm.dto.response.TaskSummaryResponse;
import com.mentis.hrms.pm.entity.Task;
import com.mentis.hrms.pm.enums.TaskStatus;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TaskMapper {

    public Task toEntity(CreateTaskRequest req, Long createdBy) {
        return Task.builder()
                .projectId(req.getProjectId())
                .parentTaskId(req.getParentTaskId())
                .teamId(req.getTeamId())
                .title(req.getTitle())
                .description(req.getDescription())
                .priority(req.getPriority())
                .type(req.getType())
                .startDate(req.getStartDate())
                .dueDate(req.getDueDate())
                .estimateHours(req.getEstimateHours())
                .storyPoints(req.getStoryPoints())
                .status(TaskStatus.TODO)
                .progressPct(0)
                .loggedHours(0)
                .loggedMinutes(0)
                .createdBy(createdBy)
                .build();
    }

    // ═══════════════════════════════════════════════════════════
    // toResponse — 11 args (Phase 5 full signature)
    // ═══════════════════════════════════════════════════════════
    public TaskResponse toResponse(Task t,
                                   Long teamId,
                                   String teamName,
                                   Long projectId,
                                   String projectName,
                                   List<Long> assigneeIds,
                                   List<String> assigneeNames,
                                   Long sprintId,
                                   String sprintName,
                                   Long milestoneId,
                                   String milestoneName) {
        return TaskResponse.builder()
                .id(t.getId())
                .projectId(projectId != null ? projectId : t.getProjectId())
                .projectName(projectName)
                .parentTaskId(t.getParentTaskId())
                .teamId(teamId)
                .teamName(teamName)
                .sprintId(sprintId)
                .sprintName(sprintName)
                .milestoneId(milestoneId)
                .milestoneName(milestoneName)
                .title(t.getTitle())
                .description(t.getDescription())
                .status(t.getStatus())
                .priority(t.getPriority())
                .type(t.getType())
                .startDate(t.getStartDate())
                .dueDate(t.getDueDate())
                .estimateHours(t.getEstimateHours())
                .loggedHours(t.getLoggedHours())
                .loggedMinutes(t.getLoggedMinutes())
                .storyPoints(t.getStoryPoints())
                .progressPct(t.getProgressPct())
                .assigneeIds(assigneeIds)
                .assigneeNames(assigneeNames)
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .build();
    }

    // ═══════════════════════════════════════════════════════════
    // toResponse — 7 args (back-compat — this is the one at
    // TaskMapper.java:62 that was missing)
    // ═══════════════════════════════════════════════════════════
    public TaskResponse toResponse(Task t,
                                   Long teamId,
                                   String teamName,
                                   Long projectId,
                                   String projectName,
                                   List<Long> assigneeIds,
                                   List<String> assigneeNames) {
        return toResponse(t, teamId, teamName, projectId, projectName,
                assigneeIds, assigneeNames,
                null, null, null, null);
    }

    // ═══════════════════════════════════════════════════════════
    // toResponse — 2 args (legacy — some old code may still use)
    // ═══════════════════════════════════════════════════════════
    public TaskResponse toResponse(Task t, List<Long> assigneeIds) {
        return toResponse(t, t.getTeamId(), null, t.getProjectId(), null,
                assigneeIds, null,
                null, null, null, null);
    }

    // ═══════════════════════════════════════════════════════════
    // toSummary — 9 args (Phase 5 full signature — this is what
    // TaskServiceImpl:780 calls)
    // ═══════════════════════════════════════════════════════════
    public TaskSummaryResponse toSummary(Task t,
                                         String projectName,
                                         String teamName,
                                         List<Long> assigneeIds,
                                         List<String> assigneeNames,
                                         Long sprintId,
                                         String sprintName,
                                         Long milestoneId,
                                         String milestoneName) {
        return TaskSummaryResponse.builder()
                .id(t.getId())
                .title(t.getTitle())
                .status(t.getStatus())
                .priority(t.getPriority())
                .dueDate(t.getDueDate())
                .progressPct(t.getProgressPct())
                .projectId(t.getProjectId())
                .projectName(projectName)
                .teamId(t.getTeamId())
                .teamName(teamName)
                .parentTaskId(t.getParentTaskId())
                .assigneeIds(assigneeIds)
                .assigneeNames(assigneeNames)
                .build();
    }

    // ═══════════════════════════════════════════════════════════
    // toSummary — 5 args (back-compat)
    // ═══════════════════════════════════════════════════════════
    public TaskSummaryResponse toSummary(Task t,
                                         String projectName,
                                         String teamName,
                                         List<Long> assigneeIds,
                                         List<String> assigneeNames) {
        return toSummary(t, projectName, teamName, assigneeIds, assigneeNames,
                null, null, null, null);
    }

    // ═══════════════════════════════════════════════════════════
    // toSummary — 1 arg (legacy)
    // ═══════════════════════════════════════════════════════════
    public TaskSummaryResponse toSummary(Task t) {
        return toSummary(t, null, null, null, null,
                null, null, null, null);
    }
}