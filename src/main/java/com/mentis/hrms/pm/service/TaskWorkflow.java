package com.mentis.hrms.pm.service;

import com.mentis.hrms.pm.enums.TaskStatus;

import java.util.*;

/**
 * The one place that defines which task status moves are allowed.
 *
 * STEP 4: review is now decided per project. The base table below is the "no review
 * required" flow. When a project has reviewRequired = true, a person who is NOT a reviewer
 * (the "review gate" is active for them) cannot move a task to DONE from IN_PROGRESS or
 * IN_REVIEW. Reviewers (SA, owner, MANAGER, LEAD role, team lead) are never gated.
 */
public final class TaskWorkflow {

    private TaskWorkflow() { }

    /**
     * Legacy default for projects with reviewRequired = false: IN_PROGRESS -> DONE is allowed.
     * Projects that require review are handled by the reviewGate argument, not this constant.
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
        FLOW.put(TaskStatus.DONE, EnumSet.of(TaskStatus.IN_PROGRESS)); // reopen only
    }

    /** Old signature, kept so existing callers compile. Same as "no review gate". */
    public static boolean allowed(TaskStatus from, TaskStatus to) {
        return allowed(from, to, false);
    }

    /**
     * @param reviewGate true when the project requires review AND the person moving the task
     *                   is not a reviewer.
     */
    public static boolean allowed(TaskStatus from, TaskStatus to, boolean reviewGate) {
        if (from == null || to == null || from == to) return true;
        if (reviewGate && to == TaskStatus.DONE
                && (from == TaskStatus.IN_PROGRESS || from == TaskStatus.IN_REVIEW)) {
            return false;
        }
        return FLOW.getOrDefault(from, Set.of()).contains(to);
    }

    /** {"TODO":["IN_PROGRESS","BLOCKED"], ...} with no review gate. */
    public static Map<String, List<String>> asMap() {
        return asMap(false);
    }

    /** Same map, but for one person on one task: the UI greys out illegal buttons from this. */
    public static Map<String, List<String>> asMap(boolean reviewGate) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (TaskStatus s : TaskStatus.values()) {
            List<String> l = new ArrayList<>();
            for (TaskStatus t : FLOW.getOrDefault(s, Set.of())) {
                if (allowed(s, t, reviewGate)) l.add(t.name());
            }
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

    public static String hint(TaskStatus from, TaskStatus to) {
        return hint(from, to, false);
    }

    /** Short advice appended to the error message. */
    public static String hint(TaskStatus from, TaskStatus to, boolean reviewGate) {
        if (reviewGate && to == TaskStatus.DONE && from == TaskStatus.IN_PROGRESS)
            return " This project requires review. Send it to In Review first.";
        if (reviewGate && to == TaskStatus.DONE && from == TaskStatus.IN_REVIEW)
            return " Only a reviewer (project owner, manager, lead or team lead) can mark it Done.";
        if (from == TaskStatus.TODO) return " Start the task first (move it to In Progress).";
        if (from == TaskStatus.BLOCKED) return " Unblock it first (move it to In Progress).";
        if (from == TaskStatus.DONE) return " Reopen it to In Progress first.";
        return "";
    }
}