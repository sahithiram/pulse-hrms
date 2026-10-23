package com.mentis.hrms.pm.service;

import com.mentis.hrms.pm.dto.request.CreateTeamRequest;
import com.mentis.hrms.pm.dto.response.PageResponse;
import com.mentis.hrms.pm.dto.response.TaskSummaryResponse;
import com.mentis.hrms.pm.dto.response.TeamResponse;
import com.mentis.hrms.pm.dto.response.TeamSummaryResponse;
import org.springframework.data.domain.Pageable;
import com.mentis.hrms.pm.dto.request.AddTeamMemberRequest;
import com.mentis.hrms.pm.dto.request.UpdateTeamMemberRequest;
import com.mentis.hrms.pm.dto.response.TeamMemberResponse;
import java.util.List;

public interface TeamService {
    TeamResponse createTeam(CreateTeamRequest req, Long userId);
    TeamResponse getTeam(Long id);
    PageResponse<TeamSummaryResponse> listTeams(String search, Pageable pageable);
    List<TeamSummaryResponse> listAllForCards();
    void deleteTeam(Long id);

    // NEW ─ team drawer + task board filter
    List<Long> listTeamMemberIds(Long teamId);
    TeamTasksResult listTeamTasks(Long teamId, int limit);
    List<TeamSummaryResponse> listForDropdown();
    List<TeamMemberResponse> listTeamMembers(Long teamId);
    TeamMemberResponse addMember(Long teamId, AddTeamMemberRequest req, Long userId);
    TeamMemberResponse updateMember(Long teamId, Long employeeId, UpdateTeamMemberRequest req);
    void removeMember(Long teamId, Long employeeId);
    /** Rich result: what tasks belong to this team and why */
    record TeamTasksResult(
            Long teamId,
            String teamName,
            Long teamLeadId,
            String teamLeadName,
            List<Long> memberIds,
            long openTasks,
            long doneTasks,
            List<TaskSummaryResponse> tasks
    ) {}
}