package com.mentis.hrms.pm.dto.request;

import lombok.*;

import java.time.LocalDate;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ProjectRequestDecision {
    private Long ownerId;        // approve: default = requester
    private Long templateId;     // approve: optional
    private LocalDate startDate; // approve: default = the requester's wish, else today (template) / empty
    private LocalDate endDate;   // approve: default = the requester's wish
    private String note;         // reject: required
}