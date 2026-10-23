package com.mentis.hrms.pm.service;

import com.mentis.hrms.pm.enums.TaskStatus;

import java.util.*;

/** The one place that defines which task status moves are allowed. */
public final class TaskWorkflow {

    private TaskWorkflow() { }

    /**
     * true  → a task may go IN_PROGRESS → DONE directly (review is optional).
     * false → it must pass through IN_REVIEW first.
     */
    public static final boolean REVIEW_OPTIONAL = true;

    private static final Map<TaskStatus, Set<TaskStatus>> FLOW = new EnumMap<>(TaskStatus.class);

    static {
        FLOW.put(TaskStatus.TODO, EnumSet.of(TaskStatus.IN_PROGRESS, TaskStatus.BLOCKED));

        Set<TaskStatus> inProgress = EnumSet.of(TaskStatus.TODO, TaskStatus.IN_REVIEW, TaskStatus.BLOCKED);
        if (REVIEW_OPTIONAL) inProgress.add(TaskStatus.DONE);
        FLOW.put(TaskStatus.IN_PROGRESS, inProgress);

        FLOW.put(TaskStatus.IN_REVIEW,
                EnumSet.of(TaskStatus.IN_PROGRESS, TaskStatus.DONE, TaskStatus.BLOCKED));
        FLOW.put(TaskStatus.BLOCKED, EnumSet.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS));
        FLOW.put(TaskStatus.DONE, EnumSet.of(TaskStatus.IN_PROGRESS));      // reopen only
    }

    public static boolean allowed(TaskStatus from, TaskStatus to) {
        if (from == null || to == null || from == to) return true;
        return FLOW.getOrDefault(from, Set.of()).contains(to);
    }

    /** {"TODO":["IN_PROGRESS","BLOCKED"], ...}  — used by the UI to grey out illegal buttons. */
    public static Map<String, List<String>> asMap() {
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (TaskStatus s : TaskStatus.values()) {
            List<String> l = new ArrayList<>();
            for (TaskStatus t : FLOW.getOrDefault(s, Set.of())) l.add(t.name());
            out.put(s.name(), l);
        }
        return out;
    }

    public static String label(TaskStatus s) {
        if (s == null) return "?";
        return switch (s) {
            case TODO -> "To Do";
            case IN_PROGRESS -> "In Progress";
            case IN_REVIEW -> "In Review";
            case BLOCKED -> "Blocked";
            case DONE -> "Done";
        };
    }

    /** Short advice appended to the error message. */
    public static String hint(TaskStatus from, TaskStatus to) {
        if (from == TaskStatus.TODO) return " Start the task first (move it to In Progress).";
        if (from == TaskStatus.BLOCKED) return " Unblock it first (move it to In Progress).";
        if (from == TaskStatus.DONE) return " Reopen it to In Progress first.";
        if (from == TaskStatus.IN_PROGRESS && to == TaskStatus.DONE && !REVIEW_OPTIONAL)
            return " Send it to In Review first.";
        return "";
    }
}