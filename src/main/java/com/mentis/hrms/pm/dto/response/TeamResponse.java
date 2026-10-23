package com.mentis.hrms.pm.dto.response;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TeamResponse {
    private Long id;
    private String name;
    private String icon;
    private String department;
    private String description;
    private Long leadEmployeeId;
    private String status;
    private Integer weeklyHours;
    private String timezone;
    private Long memberCount;
    private List<Long> memberIds;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}