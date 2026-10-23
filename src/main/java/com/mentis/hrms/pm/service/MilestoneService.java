package com.mentis.hrms.pm.service;

import com.mentis.hrms.pm.dto.request.CreateMilestoneRequest;
import com.mentis.hrms.pm.dto.response.MilestoneResponse;
import com.mentis.hrms.pm.dto.response.TaskSummaryResponse;

import java.util.List;

public interface MilestoneService {

    MilestoneResponse create(Long projectId, CreateMilestoneRequest req, Long userId);

    MilestoneResponse get(Long id);

    List<MilestoneResponse> listByProject(Long projectId);

    MilestoneResponse update(Long id, CreateMilestoneRequest req, Long userId);

    MilestoneResponse complete(Long id, Long userId);

    void delete(Long id, Long userId);

    List<TaskSummaryResponse> milestoneTasks(Long milestoneId);

    void addTaskToMilestone(Long milestoneId, Long taskId, Long userId);

    void removeTaskFromMilestone(Long milestoneId, Long taskId, Long userId);
}