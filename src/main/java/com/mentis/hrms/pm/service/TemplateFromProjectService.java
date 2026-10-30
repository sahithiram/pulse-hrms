package com.mentis.hrms.pm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mentis.hrms.pm.dto.request.CreateTemplateRequest;
import com.mentis.hrms.pm.entity.Project;
import com.mentis.hrms.pm.entity.Task;
import com.mentis.hrms.pm.entity.TaskChecklistItem;
import com.mentis.hrms.pm.entity.TaskDependency;
import com.mentis.hrms.pm.enums.TemplateType;
import com.mentis.hrms.pm.exception.BusinessRuleException;
import com.mentis.hrms.pm.exception.PmAccessDeniedException;
import com.mentis.hrms.pm.exception.ResourceNotFoundException;
import com.mentis.hrms.pm.repository.ProjectRepository;
import com.mentis.hrms.pm.repository.TaskChecklistItemRepository;
import com.mentis.hrms.pm.repository.TaskDependencyRepository;
import com.mentis.hrms.pm.repository.TaskRepository;
import com.mentis.hrms.pm.security.PmAccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Save a project as a reusable template.
 *
 * The produced payload is ProjectTemplatePayload-shaped (that is what the
 * pipeline stores and re-validates). Every task is exported with:
 *   • title, priority, type, estimateHours, storyPoints, dueOffsetDays
 *   • key    — stable per-template key ("t1", "t2", …) so dependsOn can reference it
 *   • checklist   — the project's checklist items for that task
 *   • dependsOn   — the project's dependencies expressed as peer task keys
 *
 * Instantiate() rebuilds all of the above. Nothing is dropped on round-trip.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TemplateFromProjectService {

    private final ProjectRepository projectRepo;
    private final TaskRepository taskRepo;
    private final TaskChecklistItemRepository checklistRepo;
    private final TaskDependencyRepository depRepo;
    private final ProjectTemplateService templateService;
    private final PmAccessService access;
    private final ObjectMapper objectMapper;

    @Transactional
    public Object save(Long projectId, String name, String description, Long userId) {
        access.requireCanViewProject(userId, projectId);
        if (!(access.isAdmin(userId) || access.canAdministerProject(userId, projectId)))
            throw new PmAccessDeniedException("Only a manager or admin can save a project as a template.");

        String n = name == null ? "" : name.trim();
        if (n.isEmpty() || n.length() > 150)
            throw new BusinessRuleException("Template name is required (max 150 characters).");

        Project p = projectRepo.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));

        List<Task> tasks = taskRepo.findByProjectId(projectId).stream()
                .sorted(Comparator.comparing(Task::getId))
                .collect(Collectors.toList());
        if (tasks.isEmpty())
            throw new BusinessRuleException("This project has no tasks to save.");

        // Deterministic key per task, in task-id order
        Map<Long, String> keyOf = new LinkedHashMap<>();
        for (int i = 0; i < tasks.size(); i++)
            keyOf.put(tasks.get(i).getId(), "t" + (i + 1));
        List<Long> taskIds = new ArrayList<>(keyOf.keySet());

        // Bulk-load checklists and dependencies for all tasks at once (2 queries, not N)
        Map<Long, List<String>> checklists = new HashMap<>();
        for (TaskChecklistItem c : checklistRepo.findByTaskIdIn(taskIds)) {
            String title = c.getTitle();
            if (title != null && !title.isBlank())
                checklists.computeIfAbsent(c.getTaskId(), k -> new ArrayList<>()).add(title.trim());
        }
        Map<Long, List<String>> deps = new HashMap<>();
        for (TaskDependency d : depRepo.findByTaskIdIn(taskIds)) {
            String preKey = keyOf.get(d.getDependsOnTaskId());
            if (preKey != null)
                deps.computeIfAbsent(d.getTaskId(), k -> new ArrayList<>()).add(preKey);
        }

        // ─── Build the payload (ProjectTemplatePayload JSON shape) ────────────────
        ObjectNode root = objectMapper.createObjectNode();

        ObjectNode projectNode = root.putObject("project");
        projectNode.put("name", p.getName());
        if (p.getPriority() != null) projectNode.put("priority", p.getPriority().name());

        // No milestones / sprints / phases exported by this endpoint — the endpoint's
        // contract is task-only (matching TemplateFromProjectController.Body).
        root.putArray("milestones");
        root.putArray("sprints");
        root.putArray("phases");

        ArrayNode tasksNode = root.putArray("tasks");
        for (Task t : tasks) {
            ObjectNode tn = tasksNode.addObject();
            tn.put("key", keyOf.get(t.getId()));
            tn.put("title", t.getTitle() == null ? "" : t.getTitle());
            if (t.getPriority() != null) tn.put("priority", t.getPriority().name());
            if (t.getType() != null) tn.put("type", t.getType().name());
            if (t.getEstimateHours() != null) tn.put("estimateHours", t.getEstimateHours());
            if (t.getStoryPoints() != null) tn.put("storyPoints", t.getStoryPoints());
            if (p.getStartDate() != null && t.getDueDate() != null) {
                long offset = ChronoUnit.DAYS.between(p.getStartDate(), t.getDueDate());
                if (offset >= 0 && offset <= 3650) tn.put("dueOffsetDays", (int) offset);
            }
            ArrayNode chk = tn.putArray("checklist");
            for (String item : checklists.getOrDefault(t.getId(), List.of())) chk.add(item);
            ArrayNode dnp = tn.putArray("dependsOn");
            for (String preKey : deps.getOrDefault(t.getId(), List.of())) dnp.add(preKey);
        }

        // ─── Hand off to the normal create() pipeline (validator + size guard) ────
        CreateTemplateRequest req = new CreateTemplateRequest();
        req.setName(n);
        req.setDescription(description);
        req.setType(TemplateType.HYBRID);   // neutral default for a project-derived template
        req.setPayload(root);

        log.info("Saving project {} as template '{}' ({} task(s), {} with checklist, {} with deps)",
                projectId, n, tasks.size(), checklists.size(), deps.size());
        return templateService.create(req, userId);
    }
}