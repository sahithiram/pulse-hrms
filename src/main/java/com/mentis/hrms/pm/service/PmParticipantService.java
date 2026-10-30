package com.mentis.hrms.pm.service;

import com.mentis.hrms.pm.entity.Project;
import com.mentis.hrms.pm.entity.Task;
import com.mentis.hrms.pm.enums.ProjectRole;
import com.mentis.hrms.pm.repository.ProjectMemberRepository;
import com.mentis.hrms.pm.repository.ProjectRepository;
import com.mentis.hrms.pm.repository.TeamMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Step 0 of PART C: the single definition of "may this person be assigned to this task".
 * Moved verbatim out of HandoverServiceImpl.isParticipant so handover, auto-balance and
 * (later) recurring tasks / retro action items all use the same rule.
 *
 * Rule: an explicit project member counts unless they are a VIEWER; otherwise the person
 * must belong to the task's team (or, if the task has none, the project's team).
 * Never calls PmAccessService, so it is safe from schedulers.
 */
@Component
@RequiredArgsConstructor
public class PmParticipantService {

    private final ProjectMemberRepository memberRepo;
    private final ProjectRepository projectRepo;
    private final TeamMemberRepository teamMemberRepository;

    public boolean isParticipant(Task t, Long empPk) {
        var pm = memberRepo.findByProjectIdAndEmployeeId(t.getProjectId(), empPk);
        if (pm.isPresent()) return pm.get().getRole() != ProjectRole.VIEWER;
        Long teamId = t.getTeamId() != null ? t.getTeamId()
                : projectRepo.findById(t.getProjectId()).map(Project::getTeamId).orElse(null);
        return teamId != null && teamMemberRepository.findByTeamId(teamId).stream()
                .anyMatch(tm -> empPk.equals(tm.getEmployeeId()));
    }
}