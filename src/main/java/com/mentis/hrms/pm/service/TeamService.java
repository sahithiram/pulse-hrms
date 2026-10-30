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
import java.util.Set;
public interface TeamService {
    TeamResponse createTeam(CreateTeamRequest req, Long userId);
    TeamResponse getTeam(Long id);
    /** onlyTeamIds == null means "all teams". An empty set means "none". */
    PageResponse<TeamSummaryResponse> listTeams(String search, Pageable pageable, Set<Long> onlyTeamIds);
    List<TeamSummaryResponse> listAllForCards(Set<Long> onlyTeamIds);
    List<TeamSummaryResponse> listForDropdown(Set<Long> onlyTeamIds);

    // Old signatures kept so any other caller still compiles.
    default PageResponse<TeamSummaryResponse> listTeams(String search, Pageable pageable) {
        return listTeams(search, pageable, null);
    }
    default List<TeamSummaryResponse> listAllForCards() { return listAllForCards(null); }
    default List<TeamSummaryResponse> listForDropdown() { return listForDropdown(null); }
    void deleteTeam(Long id);

    // NEW ─ team drawer + task board filter
    List<Long> listTeamMemberIds(Long teamId);
    TeamTasksResult listTeamTasks(Long teamId, int limit, Long viewerId);

    /** No viewer = no masking (schedulers, internal calls). */
    default TeamTasksResult listTeamTasks(Long teamId, int limit) {
        return listTeamTasks(teamId, limit, null);
    }

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