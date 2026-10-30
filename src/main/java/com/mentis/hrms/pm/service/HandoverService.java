package com.mentis.hrms.pm.service;

import com.mentis.hrms.pm.dto.request.HandoverRequest;
import com.mentis.hrms.pm.dto.response.HandoverResponse;
import java.util.List;

public interface HandoverService {
    List<HandoverResponse.Row> openTasks(Long employeePk, Long projectId);
    HandoverResponse reassign(HandoverRequest req, Long actorPk);
    void replaceTeamLead(Long teamId, Long newLeadPk, Long actorPk);

    /**
     * Moves ONE open task from one assignee to another through the same path as reassign():
     * assignment update + ActivityLog, with the project-lock and participant checks.
     * Sends no notification (the caller sends its own summary bell).
     *
     * @return null when the task was moved, otherwise a one-line reason it was skipped.
     */
    String moveOne(Long taskId, Long fromEmployeeId, Long toEmployeeId, Long actorPk, String reason);

}