package com.mentis.hrms.pm.dto.request;

import jakarta.validation.constraints.Size;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ClosureItemRequest {
    @Size(max = 300, message = "Title is too long (max 300 characters)")
    private String title;   // POST: required. PUT: optional
    private Boolean done;   // PUT only
}