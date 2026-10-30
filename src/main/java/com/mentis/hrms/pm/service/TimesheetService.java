package com.mentis.hrms.pm.service;

import com.mentis.hrms.pm.dto.response.TimesheetResponse;

import java.time.LocalDate;
import java.util.List;

public interface TimesheetService {
    TimesheetResponse forEmployee(Long employeePk, LocalDate weekStart);
    List<TimesheetResponse> forTeam(Long teamId, LocalDate weekStart);
    List<TimesheetResponse> forProject(Long projectId, LocalDate weekStart);
    String exportCsv(Long teamId, LocalDate weekStart);

    /** Employee submits a week for approval (locks it). */
    TimesheetResponse submit(Long userId, LocalDate weekStart);

    /** Admin reopens an APPROVED / REJECTED week so it can be corrected. */
    TimesheetResponse reopen(Long employeePk, LocalDate weekStart, Long actorPk);

    TimesheetResponse lock(Long weekId, Long actorPk);
    TimesheetResponse unlock(Long weekId, Long actorPk);
}