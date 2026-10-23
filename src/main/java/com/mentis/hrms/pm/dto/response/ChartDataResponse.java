package com.mentis.hrms.pm.dto.response;

import lombok.*;

import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ChartDataResponse {
    private List<String> labels;
    private List<Long> data;
}