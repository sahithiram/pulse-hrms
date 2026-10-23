package com.mentis.hrms.pm.service;

import com.mentis.hrms.pm.dto.request.CompleteSprintRequest;
import com.mentis.hrms.pm.dto.request.CreateSprintRequest;
import com.mentis.hrms.pm.dto.response.BurndownResponse;
import com.mentis.hrms.pm.dto.response.SprintResponse;
import com.mentis.hrms.pm.dto.response.TaskSummaryResponse;
import com.mentis.hrms.pm.dto.response.VelocityResponse;

import java.util.List;

public interface SprintService {
    SprintResponse create(Long projectId, CreateSprintRequest req, Long userId);
    SprintResponse get(Long id);
    List<SprintResponse> listByProject(Long projectId);
    SprintResponse update(Long id, CreateSprintRequest req, Long userId);
    SprintResponse start(Long id, Long userId);
    SprintResponse complete(Long id, CompleteSprintRequest req, Long userId);
    SprintResponse updateRetro(Long id, String retroNotes, Long userId);
    VelocityResponse velocity(Long projectId);
    void delete(Long id, Long userId);

    List<TaskSummaryResponse> sprintTasks(Long sprintId);
    void addTaskToSprint(Long sprintId, Long taskId, Long userId);
    void removeTaskFromSprint(Long sprintId, Long taskId, Long userId);
    List<TaskSummaryResponse> backlog(Long projectId);
    BurndownResponse burndown(Long sprintId);
}