package com.mentis.hrms.pm.dto.response;

import com.mentis.hrms.pm.enums.RetroCategory;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RetroResponse {

    private Long sprintId;
    private String sprintName;
    private String sprintStatus;
    private boolean exists;          // false until the first item is added
    private boolean closed;
    private LocalDateTime closedAt;
    private boolean canEdit;         // sprint member or manager, retro not closed, sprint started
    private boolean canManage;       // manager: may close / reopen / delete anyone's item
    private String legacyNotes;      // the old single-text retro notes, shown read-only
    private List<Item> items;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Item {
        private Long id;
        private RetroCategory category;
        private String text;
        private Long assigneeId;
        private String assigneeName;
        private LocalDate dueDate;
        private Long convertedTaskId;
        private Long createdBy;
        private String createdByName;
        private LocalDateTime createdAt;
        private boolean canDelete;     // computed for the caller
        private boolean canConvert;    // ACTION, not yet converted, caller can edit
    }
}