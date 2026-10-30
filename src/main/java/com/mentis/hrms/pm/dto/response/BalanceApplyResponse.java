package com.mentis.hrms.pm.dto.response;

import lombok.*;

import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BalanceApplyResponse {

    private int applied;
    private List<Applied> moved;
    private List<String> skipped;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Applied {
        private Long taskId;
        private String title;
        private String fromName;
        private String toName;
    }
}