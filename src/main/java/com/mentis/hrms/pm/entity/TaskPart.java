package com.mentis.hrms.pm.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Top-level TaskPart used by TemplateGraphValidator for graph-shaped templates
 * (tasks with a key, checklist items, and dependencies).
 *
 * This is DISTINCT from ProjectTemplatePayload.TaskPart, which is the linear
 * template-part shape (title/priority/type/assignToRole/...). Keep both.
 */
@Getter @Setter @NoArgsConstructor
public class TaskPart {
    private String key;
    private String title;
    private List<String> checklist;
    private List<String> dependsOn;
}