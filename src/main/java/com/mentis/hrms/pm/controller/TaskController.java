package com.mentis.hrms.pm.controller;

import com.mentis.hrms.pm.dto.request.AddChecklistRequest;
import com.mentis.hrms.pm.dto.request.AddDependencyRequest;
import com.mentis.hrms.pm.dto.request.AddWatcherRequest;
import com.mentis.hrms.pm.dto.request.AssignTaskRequest;
import com.mentis.hrms.pm.dto.request.CreateCommentRequest;
import com.mentis.hrms.pm.dto.request.CreateTaskRequest;
import com.mentis.hrms.pm.dto.request.TaskStatusChangeRequest;
import com.mentis.hrms.pm.dto.request.UpdateTaskRequest;
import com.mentis.hrms.pm.dto.response.ActivityResponse;
import com.mentis.hrms.pm.dto.response.ApiResponse;
import com.mentis.hrms.pm.dto.response.ChecklistItemResponse;
import com.mentis.hrms.pm.dto.response.CommentResponse;
import com.mentis.hrms.pm.dto.response.PageResponse;
import com.mentis.hrms.pm.dto.response.TaskBoardMetaResponse;
import com.mentis.hrms.pm.dto.response.TaskResponse;
import com.mentis.hrms.pm.dto.response.TaskSummaryResponse;
import com.mentis.hrms.pm.entity.TaskDependency;
import com.mentis.hrms.pm.enums.TaskStatus;
import com.mentis.hrms.pm.security.CurrentUserId;
import com.mentis.hrms.pm.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.mentis.hrms.pm.security.PmAccessService;
import com.mentis.hrms.pm.service.MyWorkService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController("pmTaskController")
@RequestMapping("/api/pm/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;
    private final PmAccessService pmAccessService;
    private final MyWorkService myWorkService;
    // ══════════════════════════════════════════════════════════
    // CORE CRUD
    // ══════════════════════════════════════════════════════════

    @PostMapping
    public ResponseEntity<ApiResponse<TaskResponse>> create(
            @Valid @RequestBody CreateTaskRequest req,
            @CurrentUserId Long userId) {
        pmAccessService.requireCanCreate(userId, req.getProjectId(), req.getTeamId());
        TaskResponse res = taskService.createTask(req, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Task created", res));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TaskResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTaskRequest req,
            @CurrentUserId Long userId) {
        pmAccessService.requireCanEdit(userId, id);
        return ResponseEntity.ok(ApiResponse.ok(taskService.updateTask(id, req, userId)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<TaskResponse>> changeStatus(
            @PathVariable Long id,
            @Valid @RequestBody TaskStatusChangeRequest req,
            @CurrentUserId Long userId) {
        pmAccessService.requireCanEdit(userId, id);
        return ResponseEntity.ok(ApiResponse.ok("Status updated",
                taskService.changeStatus(id, req, userId)));
    }

    @PatchMapping("/{id}/assign")
    public ResponseEntity<ApiResponse<TaskResponse>> assign(
            @PathVariable Long id,
            @Valid @RequestBody AssignTaskRequest req,
            @CurrentUserId Long userId) {
        pmAccessService.requireCanAssign(userId, id);
        return ResponseEntity.ok(ApiResponse.ok("Assigned",
                taskService.assignTask(id, req, userId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TaskResponse>> get(
            @PathVariable Long id,
            @CurrentUserId Long userId) {
        pmAccessService.requireCanView(userId, id);
        return ResponseEntity.ok(ApiResponse.ok(taskService.getTask(id)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<TaskSummaryResponse>>> list(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long teamId,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction) {
        Sort sort = Sort.by(Sort.Direction.fromString(direction), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);
        if (teamId != null) {
            return ResponseEntity.ok(ApiResponse.ok(
                    taskService.listTasksByTeam(teamId, status, search, pageable)));
        }
        return ResponseEntity.ok(ApiResponse.ok(
                taskService.listTasks(projectId, status, search, pageable)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long id,
            @CurrentUserId Long userId) {
        pmAccessService.requireCanDelete(userId, id);
        taskService.deleteTask(id, userId);
        return ResponseEntity.ok(ApiResponse.ok("Deleted", null));
    }

    // ══════════════════════════════════════════════════════════
    // SUBTASKS (NEW)
    // ══════════════════════════════════════════════════════════

    @GetMapping("/{id}/subtasks")
    public ResponseEntity<ApiResponse<List<TaskSummaryResponse>>> listSubtasks(
            @PathVariable Long id,
            @CurrentUserId Long userId) {
        pmAccessService.requireCanView(userId, id);
        return ResponseEntity.ok(ApiResponse.ok(taskService.listSubtasks(id)));
    }

    @PostMapping("/{id}/subtasks")
    public ResponseEntity<ApiResponse<TaskResponse>> createSubtask(
            @PathVariable Long id,
            @Valid @RequestBody CreateTaskRequest req,
            @CurrentUserId Long userId) {
        pmAccessService.requireCanEdit(userId, id);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Subtask created",
                        taskService.createSubtask(id, req, userId)));
    }

    // ══════════════════════════════════════════════════════════
    // BOARD META (subtask + checklist + blocker counts)
    // ══════════════════════════════════════════════════════════

    @GetMapping("/{id}/board-meta")
    public ResponseEntity<ApiResponse<TaskBoardMetaResponse>> boardMeta(
            @PathVariable Long id,
            @CurrentUserId Long userId) {
        pmAccessService.requireCanView(userId, id);
        return ResponseEntity.ok(ApiResponse.ok(taskService.getBoardMeta(id)));
    }

    // ══════════════════════════════════════════════════════════
    // COMMENTS
    // ══════════════════════════════════════════════════════════

    @PostMapping("/{taskId}/comments")
    public ResponseEntity<ApiResponse<CommentResponse>> addComment(
            @PathVariable Long taskId,
            @Valid @RequestBody CreateCommentRequest req,
            @CurrentUserId Long userId) {
        pmAccessService.requireCanView(userId, taskId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Comment added", taskService.addComment(taskId, req, userId)));
    }

    @GetMapping("/{taskId}/comments")
    public ResponseEntity<ApiResponse<List<CommentResponse>>> listComments(
            @PathVariable Long taskId,
            @CurrentUserId Long userId) {
        pmAccessService.requireCanView(userId, taskId);
        return ResponseEntity.ok(ApiResponse.ok(taskService.listComments(taskId)));
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<ApiResponse<Void>> deleteComment(
            @PathVariable Long commentId,
            @CurrentUserId Long userId) {
        pmAccessService.requireCanDeleteComment(userId, commentId);
        taskService.deleteComment(commentId, userId);
        return ResponseEntity.ok(ApiResponse.ok("Deleted", null));
    }

    // ══════════════════════════════════════════════════════════
    // CHECKLIST
    // ══════════════════════════════════════════════════════════

    @GetMapping("/{taskId}/checklist")
    public ResponseEntity<ApiResponse<List<ChecklistItemResponse>>> listChecklist(
            @PathVariable Long taskId,
            @CurrentUserId Long userId) {
        pmAccessService.requireCanView(userId, taskId);
        return ResponseEntity.ok(ApiResponse.ok(taskService.listChecklist(taskId)));
    }

    @PostMapping("/{taskId}/checklist")
    public ResponseEntity<ApiResponse<ChecklistItemResponse>> addChecklist(
            @PathVariable Long taskId,
            @Valid @RequestBody AddChecklistRequest req,
            @CurrentUserId Long userId) {
        pmAccessService.requireCanEdit(userId, taskId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Item added", taskService.addChecklistItem(taskId, req, userId)));
    }

    @PatchMapping("/checklist/{itemId}/toggle")
    public ResponseEntity<ApiResponse<ChecklistItemResponse>> toggleChecklist(
            @PathVariable Long itemId,
            @CurrentUserId Long userId) {
        pmAccessService.requireCanEditChecklistItem(userId, itemId);
        return ResponseEntity.ok(ApiResponse.ok("Toggled", taskService.toggleChecklistItem(itemId, userId)));
    }

    @DeleteMapping("/checklist/{itemId}")
    public ResponseEntity<ApiResponse<Void>> deleteChecklist(
            @PathVariable Long itemId,
            @CurrentUserId Long userId) {
        pmAccessService.requireCanEditChecklistItem(userId, itemId);
        taskService.deleteChecklistItem(itemId, userId);
        return ResponseEntity.ok(ApiResponse.ok("Deleted", null));
    }

    // ══════════════════════════════════════════════════════════
    // WATCHERS
    // ══════════════════════════════════════════════════════════

    @PostMapping("/{taskId}/watchers")
    public ResponseEntity<ApiResponse<Void>> addWatcher(
            @PathVariable Long taskId,
            @Valid @RequestBody AddWatcherRequest req,
            @CurrentUserId Long userId) {
        if (userId != null && userId.equals(req.getEmployeeId()))
            pmAccessService.requireCanView(userId, taskId);   // watch it myself
        else
            pmAccessService.requireCanEdit(userId, taskId);   // add someone else
        taskService.addWatcher(taskId, req.getEmployeeId());
        return ResponseEntity.ok(ApiResponse.ok("Watcher added", null));
    }

    @DeleteMapping("/{taskId}/watchers/{employeeId}")
    public ResponseEntity<ApiResponse<Void>> removeWatcher(
            @PathVariable Long taskId,
            @PathVariable Long employeeId,
            @CurrentUserId Long userId) {
        if (userId != null && userId.equals(employeeId))
            pmAccessService.requireCanView(userId, taskId);
        else
            pmAccessService.requireCanEdit(userId, taskId);
        taskService.removeWatcher(taskId, employeeId);
        return ResponseEntity.ok(ApiResponse.ok("Watcher removed", null));
    }

    @GetMapping("/{taskId}/watchers")
    public ResponseEntity<ApiResponse<List<Long>>> listWatchers(
            @PathVariable Long taskId,
            @CurrentUserId Long userId) {
        pmAccessService.requireCanView(userId, taskId);
        return ResponseEntity.ok(ApiResponse.ok(taskService.listWatchers(taskId)));
    }

    // ══════════════════════════════════════════════════════════
    // DEPENDENCIES
    // ══════════════════════════════════════════════════════════

    @PostMapping("/{taskId}/dependencies")
    public ResponseEntity<ApiResponse<Void>> addDependency(
            @PathVariable Long taskId,
            @Valid @RequestBody AddDependencyRequest req,
            @CurrentUserId Long userId) {
        pmAccessService.requireCanEdit(userId, taskId);
        pmAccessService.requireCanView(userId, req.getDependsOnTaskId());
        taskService.addDependency(taskId, req);
        return ResponseEntity.ok(ApiResponse.ok("Dependency added", null));
    }

    @DeleteMapping("/dependencies/{depId}")
    public ResponseEntity<ApiResponse<Void>> removeDependency(
            @PathVariable Long depId,
            @CurrentUserId Long userId) {
        pmAccessService.requireCanEditDependency(userId, depId);
        taskService.removeDependency(depId);
        return ResponseEntity.ok(ApiResponse.ok("Dependency removed", null));
    }

    @GetMapping("/{taskId}/dependencies")
    public ResponseEntity<ApiResponse<List<TaskDependency>>> listDependencies(
            @PathVariable Long taskId,
            @CurrentUserId Long userId) {
        pmAccessService.requireCanView(userId, taskId);
        return ResponseEntity.ok(ApiResponse.ok(taskService.listDependencies(taskId)));
    }

    /** NEW: rich dependency graph (blocks + blockedBy + openBlockerCount) */
    @GetMapping("/{taskId}/dependencies/graph")
    public ResponseEntity<ApiResponse<Map<String, Object>>> dependencyGraph(
            @PathVariable Long taskId,
            @CurrentUserId Long userId) {
        pmAccessService.requireCanView(userId, taskId);
        TaskService.DependencyGraph g = taskService.getDependencyGraph(taskId);

        List<Map<String, Object>> blocks = g.blocks().stream()
                .map(e -> toEdgeMap(e))
                .toList();
        List<Map<String, Object>> blockedBy = g.blockedBy().stream()
                .map(e -> toEdgeMap(e))
                .toList();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("blocks", blocks);
        body.put("blockedBy", blockedBy);
        body.put("openBlockerCount", g.openBlockerCount());

        return ResponseEntity.ok(ApiResponse.ok(body));
    }

    private Map<String, Object> toEdgeMap(TaskService.DependencyEdge e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("taskId", e.taskId());
        m.put("title", e.title());
        m.put("status", e.status());
        m.put("priority", e.priority());
        m.put("type", e.type());
        return m;
    }

    // ══════════════════════════════════════════════════════════
    // HISTORY
    // ══════════════════════════════════════════════════════════

    @GetMapping("/{taskId}/history")
    public ResponseEntity<ApiResponse<List<ActivityResponse>>> history(
            @PathVariable Long taskId,
            @CurrentUserId Long userId) {
        pmAccessService.requireCanView(userId, taskId);
        return ResponseEntity.ok(ApiResponse.ok(taskService.taskHistory(taskId)));
    }
}