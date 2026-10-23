package com.mentis.hrms.pm.dto.request;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ProjectRequestDecision {
    private Long ownerId;      // approve: default = requester
    private Long templateId;   // approve: optional
    private String note;       // reject: required
}