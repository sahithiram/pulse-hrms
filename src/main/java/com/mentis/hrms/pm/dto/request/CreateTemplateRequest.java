package com.mentis.hrms.pm.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.mentis.hrms.pm.enums.TemplateType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class CreateTemplateRequest {
    @NotBlank @Size(max = 120) private String name;
    @NotNull private TemplateType type;
    @Size(max = 500) private String description;
    /** A JSON object (not a string). */
    @NotNull private JsonNode payload;
}