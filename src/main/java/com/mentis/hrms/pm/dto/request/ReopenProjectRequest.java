package com.mentis.hrms.pm.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

/** STEP 8: body of POST /api/pm/projects/{id}/reopen (Super Admin only). */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ReopenProjectRequest {

    /** Why the project is being reopened. The service also checks for at least 10 characters. */
    @NotBlank(message = "Please say why you are reopening this project")
    @Size(max = 500, message = "The reason is too long (max 500 characters)")
    private String reason;
}