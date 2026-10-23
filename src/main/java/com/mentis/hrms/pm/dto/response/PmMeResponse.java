package com.mentis.hrms.pm.dto.response;

import lombok.*;

import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PmMeResponse {
    private Long id;
    private String employeeId;
    private String name;
    private String email;
    private String avatarInitials;
    private String hrmsRole;
    private List<Long> ownerOfProjectIds;
    private List<Long> leadOfTeamIds;
    private List<Long> memberOfTeamIds;
    private boolean admin;
    private boolean canCreateProject;
}