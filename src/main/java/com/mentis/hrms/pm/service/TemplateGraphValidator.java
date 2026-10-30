package com.mentis.hrms.pm.service;

import com.mentis.hrms.pm.dto.request.TaskPart;
import com.mentis.hrms.pm.exception.BusinessRuleException;
import java.util.*;

public final class TemplateGraphValidator {

    static final int MAX_TASKS = 500, MAX_CHECKLIST = 50, MAX_ITEM_LEN = 300, MAX_DEPS = 20, MAX_KEY_LEN = 40;

    private TemplateGraphValidator() {}

    private static <T> List<T> nz(List<T> l) { return l == null ? List.of() : l; }

    /** Cleans checklists in place and throws BusinessRuleException on the first problem. */
    public static void validate(List<TaskPart> parts) {
        if (parts.size() > MAX_TASKS)
            throw new BusinessRuleException("A template can hold at most " + MAX_TASKS + " tasks.");

        Set<String> keys = new HashSet<>();
        for (TaskPart t : parts) {
            if (t.getKey() != null) {
                String k = t.getKey().trim();
                if (k.isEmpty() || k.length() > MAX_KEY_LEN)
                    throw new BusinessRuleException("A task key must be 1-" + MAX_KEY_LEN + " characters.");
                if (!keys.add(k)) throw new BusinessRuleException("Duplicate task key: " + k);
                t.setKey(k);
            }
            List<String> clean = new ArrayList<>();
            for (String item : nz(t.getChecklist())) {
                String s = item == null ? "" : item.trim();
                if (s.isEmpty()) continue;
                if (s.length() > MAX_ITEM_LEN)
                    throw new BusinessRuleException("A checklist item is longer than " + MAX_ITEM_LEN + " characters.");
                clean.add(s);
            }
            if (clean.size() > MAX_CHECKLIST)
                throw new BusinessRuleException("A task can have at most " + MAX_CHECKLIST + " checklist items.");
            t.setChecklist(clean);
        }

        Map<String, List<String>> graph = new HashMap<>();
        for (TaskPart t : parts) {
            List<String> deps = nz(t.getDependsOn());
            if (deps.isEmpty()) { if (t.getKey() != null) graph.put(t.getKey(), List.of()); continue; }
            if (t.getKey() == null) throw new BusinessRuleException("A task with dependencies needs a key.");
            if (deps.size() > MAX_DEPS) throw new BusinessRuleException("A task can depend on at most " + MAX_DEPS + " others.");
            Set<String> seen = new LinkedHashSet<>();
            for (String d : deps) {
                if (d == null || !keys.contains(d.trim())) throw new BusinessRuleException("Unknown dependency: " + d);
                if (d.trim().equals(t.getKey())) throw new BusinessRuleException("A task cannot depend on itself: " + d);
                seen.add(d.trim());
            }
            t.setDependsOn(new ArrayList<>(seen));
            graph.put(t.getKey(), t.getDependsOn());
        }

        // cycle check: DFS, state 0 = new, 1 = on the current path, 2 = finished
        Map<String, Integer> state = new HashMap<>();
        for (String k : graph.keySet()) visit(k, graph, state);
    }

    private static void visit(String k, Map<String, List<String>> g, Map<String, Integer> state) {
        int s = state.getOrDefault(k, 0);
        if (s == 2) return;
        if (s == 1) throw new BusinessRuleException("Dependency cycle through task " + k);
        state.put(k, 1);
        for (String d : g.getOrDefault(k, List.of())) visit(d, g, state);
        state.put(k, 2);
    }
}