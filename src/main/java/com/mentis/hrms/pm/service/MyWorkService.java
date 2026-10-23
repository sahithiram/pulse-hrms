package com.mentis.hrms.pm.service;

import com.mentis.hrms.pm.dto.response.ProjectSummaryResponse;
import com.mentis.hrms.pm.dto.response.TaskSummaryResponse;

import java.util.List;

/**
 * Phase 3 — scoped "My Work" for the logged-in employee.
 * Every method here is called with the *resolved* userId from session,
 * so access control happens by construction (we only query rows
 * the user is on).
 */
public interface MyWorkService {

    /** Tasks where currentUserId is assignee OR watcher, top-level only. */
    List<TaskSummaryResponse> listMyTasks(Long currentUserId);

    /** Projects where currentUserId is owner, team member, or task assignee. */
    List<ProjectSummaryResponse> listMyProjects(Long currentUserId);

    /** KPI numbers for the dashboard widget. */
    MyWorkKpi getKpi(Long currentUserId);

    record MyWorkKpi(
            long openTasks,
            long dueToday,
            long overdue,
            long doneThisWeek,
            long hoursLogged
    ) {}
}