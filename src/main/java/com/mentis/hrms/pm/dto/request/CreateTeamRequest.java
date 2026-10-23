package com.mentis.hrms.pm.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CreateTeamRequest {

    @NotBlank @Size(max = 150)
    private String name;

    private String icon;
    private String department;
    private String description;
    private Long leadEmployeeId;
    private String status;
    private Integer weeklyHours;
    private String timezone;

    private List<Long> memberIds;
}