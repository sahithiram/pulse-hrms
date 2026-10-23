package com.mentis.hrms.pm.dto.response;

import lombok.*;
import java.time.LocalDate;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BurndownResponse {
    private Long sprintId;
    private String sprintName;
    private LocalDate startDate;
    private LocalDate endDate;
    private long totalPoints;
    private List<Point> points;      // one per day
    private List<Point> ideal;       // straight line start → end

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Point {
        private LocalDate date;
        private long remainingPoints;
        private long completedPoints;
        private long idealPoints;
    }
}