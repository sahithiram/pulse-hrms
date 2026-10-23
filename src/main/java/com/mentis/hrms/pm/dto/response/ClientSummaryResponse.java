package com.mentis.hrms.pm.dto.response;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ClientSummaryResponse {
    private Long id;
    private String name;
    private String industry;
    private String status;
}