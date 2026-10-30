package com.mentis.hrms.pm.dto.request;

import com.mentis.hrms.pm.enums.RetroCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RetroItemRequest {
    /** required when adding; ignored on update */
    private RetroCategory category;

    @NotBlank @Size(max = 1000)
    private String text;

    /** only used by ACTION items; checked again when the item is converted to a task */
    private Long assigneeId;
    private LocalDate dueDate;
}