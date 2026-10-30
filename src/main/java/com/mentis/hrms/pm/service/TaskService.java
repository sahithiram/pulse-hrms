package com.mentis.hrms.pm.service;

import com.mentis.hrms.pm.dto.request.*;
import com.mentis.hrms.pm.dto.response.*;
import com.mentis.hrms.pm.entity.TaskDependency;
import com.mentis.hrms.pm.enums.TaskStatus;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface TaskService {

    // ── Core CRUD ──
    TaskResponse createTask(CreateTaskRequest req, Long currentUserId);
    TaskResponse updateTask(Long id, UpdateTaskRequest req, Long currentUserId);
    TaskResponse changeStatus(Long id, TaskStatusChangeRequest req, Long currentUserId);
    TaskResponse assignTask(Long id, AssignTaskRequest req, Long currentUserId);
    TaskResponse getTask(Long id);

    // Existing — project + status + search
    PageResponse<TaskSummaryResponse> listTasks(Long projectId,
                                                TaskStatus status,
                                                String search,
                                                Pageable pageable);

    // NEW — team filter (ClickUp-style chip filter on task board)
    PageResponse<TaskSummaryResponse> listTasksByTeam(Long teamId,
                                                      TaskStatus status,
                                                      String search,
                                                      Pageable pageable);

    void deleteTask(Long id, Long currentUserId);

    // ── Comments, Checklist, Watchers, Dependencies ──
    CommentResponse addComment(Long taskId, CreateCommentRequest req, Long userId);
    List<CommentResponse> listComments(Long taskId);
    void deleteComment(Long commentId, Long userId);

    List<ChecklistItemResponse> listChecklist(Long taskId);
    ChecklistItemResponse addChecklistItem(Long taskId, AddChecklistRequest req, Long userId);
    ChecklistItemResponse toggleChecklistItem(Long itemId, Long userId);
    void deleteChecklistItem(Long itemId, Long userId);

    void addWatcher(Long taskId, Long employeeId);
    void removeWatcher(Long taskId, Long employeeId);
    List<Long> listWatchers(Long taskId);
    PageResponse<TaskSummaryResponse> filterTasks(TaskFilterRequest f);
    void addDependency(Long taskId, AddDependencyRequest req);
    void removeDependency(Long depId);
    List<TaskDependency> listDependencies(Long taskId);
    // ── NEW: Subtasks ──────────────────────────────────────
    List<TaskSummaryResponse> listSubtasks(Long parentTaskId);
    TaskResponse createSubtask(Long parentTaskId, CreateTaskRequest req, Long currentUserId);
    TaskBoardMetaResponse getBoardMeta(Long taskId);

    // ── NEW: Dependencies (rich) ───────────────────────────
    DependencyGraph getDependencyGraph(Long taskId);
    void addDependencyRich(Long taskId, AddDependencyRequest req);

    /** Sub-shape returned from getDependencyGraph */
    record DependencyGraph(
            List<DependencyEdge> blocks,
            List<DependencyEdge> blockedBy,
            long openBlockerCount
    ) {}

    record DependencyEdge(
            Long taskId,
            String title,
            String status,
            String priority,
            String type
    ) {}
    List<ActivityResponse> taskHistory(Long taskId);
}