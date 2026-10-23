package com.mentis.hrms.pm.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mentis.hrms.pm.enums.ProjectPriority;
import com.mentis.hrms.pm.enums.TaskPriority;
import com.mentis.hrms.pm.enums.TaskType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Typed shape of pm_project_templates.payload:
 * { "project": {"name","priority"}, "milestones":[{"name","dueOffsetDays"}],
 *   "sprints":[{"name","goal","startOffsetDays","durationDays"}],
 *   "tasks":[{"title","priority","type","assignToRole","estimateHours","storyPoints",
 *             "dueOffsetDays","sprintIndex","milestoneIndex"}] }
 * Offsets are days from the project start date. Indexes are 0-based positions in the lists above.
 */
@Getter @Setter @NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectTemplatePayload {

    public static final int MAX_MILESTONES = 30, MAX_SPRINTS = 10, MAX_TASKS = 100;

    private ProjectPart project = new ProjectPart();
    private List<MilestonePart> milestones = new ArrayList<>();
    private List<SprintPart> sprints = new ArrayList<>();
    private List<TaskPart> tasks = new ArrayList<>();

    @Getter @Setter @NoArgsConstructor @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ProjectPart { private String name; private String priority; }

    @Getter @Setter @NoArgsConstructor @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MilestonePart { private String name; private Integer dueOffsetDays; }

    @Getter @Setter @NoArgsConstructor @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SprintPart {
        private String name; private String goal; private Integer startOffsetDays; private Integer durationDays;
    }

    @Getter @Setter @NoArgsConstructor @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TaskPart {
        private String title; private String priority; private String type; private String assignToRole;
        private Integer estimateHours; private Integer storyPoints; private Integer dueOffsetDays;
        private Integer sprintIndex; private Integer milestoneIndex;
    }

    /** Throws IllegalArgumentException (400) with a message that says exactly what is wrong. */
    public void validate() {
        if (project == null) project = new ProjectPart();
        if (milestones == null) milestones = new ArrayList<>();
        if (sprints == null) sprints = new ArrayList<>();
        if (tasks == null) tasks = new ArrayList<>();

        if (milestones.size() > MAX_MILESTONES) bad("A template can have at most " + MAX_MILESTONES + " milestones");
        if (sprints.size() > MAX_SPRINTS) bad("A template can have at most " + MAX_SPRINTS + " sprints");
        if (tasks.size() > MAX_TASKS) bad("A template can have at most " + MAX_TASKS + " tasks");

        enumOk(project.getPriority(), ProjectPriority.class, "project.priority");

        for (int i = 0; i < milestones.size(); i++) {
            MilestonePart m = milestones.get(i);
            String f = "milestones[" + i + "]";
            text(m.getName(), f + ".name", 200);
            range(m.getDueOffsetDays(), 0, 3650, f + ".dueOffsetDays", true);
        }

        int prevEndExclusive = 0; // next sprint may start on this offset or later (touching dates overlap)
        for (int i = 0; i < sprints.size(); i++) {
            SprintPart s = sprints.get(i);
            String f = "sprints[" + i + "]";
            text(s.getName(), f + ".name", 200);
            range(s.getStartOffsetDays(), 0, 3650, f + ".startOffsetDays", true);
            range(s.getDurationDays(), 1, 90, f + ".durationDays", true);
            if (s.getStartOffsetDays() < prevEndExclusive)
                bad(f + " overlaps the previous sprint (start at day " + prevEndExclusive + " or later)");
            prevEndExclusive = s.getStartOffsetDays() + s.getDurationDays();
        }

        for (int i = 0; i < tasks.size(); i++) {
            TaskPart t = tasks.get(i);
            String f = "tasks[" + i + "]";
            text(t.getTitle(), f + ".title", 300);
            enumOk(t.getPriority(), TaskPriority.class, f + ".priority");
            enumOk(t.getType(), TaskType.class, f + ".type");
            if (t.getType() != null && "SUBTASK".equalsIgnoreCase(t.getType().trim()))
                bad(f + ".type cannot be SUBTASK (subtasks need a parent task)");
            if (t.getAssignToRole() != null && !t.getAssignToRole().isBlank()) {
                String r = t.getAssignToRole().trim().toUpperCase();
                if (!r.equals("MANAGER") && !r.equals("MEMBER"))
                    bad(f + ".assignToRole must be MANAGER or MEMBER");
            }
            range(t.getEstimateHours(), 0, 1000, f + ".estimateHours", false);
            range(t.getStoryPoints(), 0, 100, f + ".storyPoints", false);
            range(t.getDueOffsetDays(), 0, 3650, f + ".dueOffsetDays", false);
            if (t.getSprintIndex() != null && (t.getSprintIndex() < 0 || t.getSprintIndex() >= sprints.size()))
                bad(f + ".sprintIndex points to a sprint that does not exist");
            if (t.getMilestoneIndex() != null && (t.getMilestoneIndex() < 0 || t.getMilestoneIndex() >= milestones.size()))
                bad(f + ".milestoneIndex points to a milestone that does not exist");
        }
    }

    private static void bad(String m) { throw new IllegalArgumentException(m); }

    private static void text(String v, String field, int max) {
        if (v == null || v.isBlank()) bad(field + " is required");
        if (v.trim().length() > max) bad(field + " is too long (max " + max + " characters)");
    }

    private static void range(Integer v, int min, int max, String field, boolean required) {
        if (v == null) { if (required) bad(field + " is required"); return; }
        if (v < min || v > max) bad(field + " must be between " + min + " and " + max);
    }

    private static <E extends Enum<E>> void enumOk(String v, Class<E> type, String field) {
        if (v == null || v.isBlank()) return;
        try { Enum.valueOf(type, v.trim().toUpperCase()); }
        catch (IllegalArgumentException e) { bad(field + " has an unknown value: " + v); }
    }
}