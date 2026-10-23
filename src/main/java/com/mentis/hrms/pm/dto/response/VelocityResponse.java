package com.mentis.hrms.pm.dto.response;

import lombok.*;

import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class VelocityResponse {
    private int sprintsCounted;            // up to 3
    private double averageVelocity;
    private int suggestedCommitment;       // rounded average
    private List<Integer> recentVelocities;
}