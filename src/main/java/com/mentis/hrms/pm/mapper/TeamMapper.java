package com.mentis.hrms.pm.mapper;

import com.mentis.hrms.pm.dto.request.CreateTeamRequest;
import com.mentis.hrms.pm.dto.response.TeamResponse;
import com.mentis.hrms.pm.dto.response.TeamSummaryResponse;
import com.mentis.hrms.pm.entity.Team;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TeamMapper {

    public Team toEntity(CreateTeamRequest req, Long createdBy) {
        return Team.builder()
                .name(req.getName())
                .icon(req.getIcon())
                .department(req.getDepartment())
                .description(req.getDescription())
                .leadEmployeeId(req.getLeadEmployeeId())
                .status(req.getStatus())
                .weeklyHours(req.getWeeklyHours())
                .timezone(req.getTimezone())
                .createdBy(createdBy)
                .build();
    }

    public TeamResponse toResponse(Team t, List<Long> memberIds) {
        return TeamResponse.builder()
                .id(t.getId()).name(t.getName()).icon(t.getIcon())
                .department(t.getDepartment()).description(t.getDescription())
                .leadEmployeeId(t.getLeadEmployeeId()).status(t.getStatus())
                .weeklyHours(t.getWeeklyHours()).timezone(t.getTimezone())
                .memberCount((long) (memberIds == null ? 0 : memberIds.size()))
                .memberIds(memberIds)
                .createdAt(t.getCreatedAt()).updatedAt(t.getUpdatedAt())
                .build();
    }

    public TeamSummaryResponse toSummary(Team t, Long memberCount, String leadName) {
        return TeamSummaryResponse.builder()
                .id(t.getId()).name(t.getName()).icon(t.getIcon())
                .department(t.getDepartment()).status(t.getStatus())
                .memberCount(memberCount)
                .leadEmployeeId(t.getLeadEmployeeId())
                .leadName(leadName)
                .weeklyHours(t.getWeeklyHours())
                .build();
    }

    /** Back-compat */
    public TeamSummaryResponse toSummary(Team t, Long memberCount) {
        return toSummary(t, memberCount, null);
    }
}