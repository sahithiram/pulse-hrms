package com.mentis.hrms.pm.controller;

import com.mentis.hrms.pm.dto.request.AssignTaskRequest;
import com.mentis.hrms.pm.dto.request.BulkTaskRequest;
import com.mentis.hrms.pm.dto.request.TaskFilterRequest;
import com.mentis.hrms.pm.dto.request.TaskStatusChangeRequest;
import com.mentis.hrms.pm.dto.response.ApiResponse;
import com.mentis.hrms.pm.dto.response.PageResponse;
import com.mentis.hrms.pm.dto.response.TaskResponse;
import com.mentis.hrms.pm.dto.response.TaskSummaryResponse;
import com.mentis.hrms.pm.exception.BusinessRuleException;
import com.mentis.hrms.pm.exception.PmAccessDeniedException;
import com.mentis.hrms.pm.security.CurrentUserId;
import com.mentis.hrms.pm.security.PmAccessService;
import com.mentis.hrms.pm.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.mentis.hrms.pm.dto.request.UpdateTaskRequest;
import java.util.*;

@RestController("pmTaskBulkController")
@RequestMapping("/api/pm/tasks")
@RequiredArgsConstructor
public class TaskBulkController {

    private final TaskService taskService;
    private final PmAccessService access;

    @PostMapping("/filter")
    public ResponseEntity<ApiResponse<PageResponse<TaskSummaryResponse>>> filter(
            @RequestBody TaskFilterRequest f, @CurrentUserId Long userId) {
        if (f.getProjectId() == null) throw new BusinessRuleException("projectId is required");
        access.requireCanViewProject(userId, f.getProjectId());
        return ResponseEntity.ok(ApiResponse.ok(taskService.filterTasks(f)));
    }

    /**
     * Not one big transaction: each task goes through the normal changeStatus / assignTask
     * (workflow rules, bells, audit log) on its own, so one bad task never blocks the rest.
     */
    @PostMapping("/bulk")
    public ResponseEntity<ApiResponse<Map<String, Object>>> bulk(
            @Valid @RequestBody BulkTaskRequest req, @CurrentUserId Long userId) {

        String action = req.getAction().trim().toUpperCase();
        Set<String> ACTIONS = Set.of("STATUS", "ASSIGN", "ADD_ASSIGNEE", "PRIORITY", "SPRINT", "DELETE");
        if (!ACTIONS.contains(action)) throw new BusinessRuleException("Unknown action: " + action);
        if (action.equals("STATUS") && req.getStatus() == null)
            throw new BusinessRuleException("status is required for the STATUS action");
        if ((action.equals("ASSIGN") || action.equals("ADD_ASSIGNEE"))
                && (req.getEmployeeIds() == null || req.getEmployeeIds().isEmpty()))
            throw new BusinessRuleException("employeeIds is required for assigning");
        if (action.equals("PRIORITY") && req.getPriority() == null)
            throw new BusinessRuleException("priority is required");                       // ADAPT: add to BulkTaskRequest
// SPRINT: sprintId may be null = move to backlog

        int ok = 0;
        List<String> failed = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (Long id : new LinkedHashSet<>(req.getTaskIds())) {
            try {
                TaskResponse cur = taskService.getTask(id);
                if (access.isProjectLocked(cur.getProjectId()))
                    throw new BusinessRuleException("Project is completed or archived, so its tasks are locked");
                switch (action) {
                    case "STATUS" -> {
                        if (!access.canEditTaskAccess(userId, id)) throw new PmAccessDeniedException("You can't change this task");
                        TaskStatusChangeRequest r = new TaskStatusChangeRequest();
                        r.setStatus(req.getStatus()); r.setReason(req.getReason());
                        taskService.changeStatus(id, r, userId);
                    }
                    case "PRIORITY", "SPRINT" -> {
                        if (!access.canEditTaskAccess(userId, id)) throw new PmAccessDeniedException("You can't change this task");
                        UpdateTaskRequest u = new UpdateTaskRequest();                     // ADAPT setter names
                        if (action.equals("PRIORITY")) u.setPriority(req.getPriority());
                        else u.setSprintId(req.getSprintId() == null ? 0L : req.getSprintId());   // ADAPT: 0 = clear, if your update uses that rule
                        taskService.updateTask(id, u, userId);
                    }
                    case "DELETE" -> {
                        if (!access.canEditTaskAccess(userId, id)) throw new PmAccessDeniedException("You can't delete this task");
                        taskService.deleteTask(id, userId);
                    }
                    default -> {
                        if (!access.canAssignTask(userId, id)) throw new PmAccessDeniedException("You can't assign this task");
                        LinkedHashSet<Long> ids = new LinkedHashSet<>();
                        if (action.equals("ADD_ASSIGNEE") && cur.getAssigneeIds() != null) ids.addAll(cur.getAssigneeIds());
                        ids.addAll(req.getEmployeeIds());
                        AssignTaskRequest r = new AssignTaskRequest();
                        r.setEmployeeIds(new ArrayList<>(ids));
                        TaskResponse res = taskService.assignTask(id, r, userId);
                        if (res.getAssigneeIds() != null && res.getAssignmentWarnings() != null)
                            warnings.addAll(res.getAssignmentWarnings());
                    }
                }
                ok++;
            } catch (Exception e) {
                failed.add("Task #" + id + ": " + e.getMessage());
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", ok);
        out.put("failed", failed);
        out.put("warnings", new LinkedHashSet<>(warnings));
        return ResponseEntity.ok(ApiResponse.ok("Bulk action finished", out));
    }
}