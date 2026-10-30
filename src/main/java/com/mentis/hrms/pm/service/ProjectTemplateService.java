package com.mentis.hrms.pm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mentis.hrms.pm.dto.request.*;
import com.mentis.hrms.pm.dto.request.ProjectTemplatePayload.MilestonePart;
import com.mentis.hrms.pm.dto.request.ProjectTemplatePayload.SprintPart;
import com.mentis.hrms.pm.dto.request.ProjectTemplatePayload.TaskPart;
import com.mentis.hrms.pm.dto.response.ProjectResponse;
import com.mentis.hrms.pm.dto.response.ProjectTemplateResponse;
import com.mentis.hrms.pm.dto.response.TaskResponse;
import com.mentis.hrms.pm.entity.ProjectMember;
import com.mentis.hrms.pm.entity.ProjectTemplate;
import com.mentis.hrms.pm.enums.ProjectPriority;
import com.mentis.hrms.pm.enums.ProjectRole;
import com.mentis.hrms.pm.enums.TaskPriority;
import com.mentis.hrms.pm.enums.TaskType;
import com.mentis.hrms.pm.exception.DuplicateResourceException;
import com.mentis.hrms.pm.exception.ResourceNotFoundException;
import com.mentis.hrms.pm.repository.ProjectMemberRepository;
import com.mentis.hrms.pm.repository.ProjectTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.mentis.hrms.pm.dto.request.ProjectTemplatePayload.PhasePart;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Templates + "create a project from a template".
 * Never calls PmAccessService (controllers check). All creation goes through the normal services,
 * so every existing rule (sprint overlap, membership, notifications, realtime) still applies.
 * One transaction: if anything fails, no half-built project is left behind.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectTemplateService {

    private static final int MAX_PAYLOAD_CHARS = 60000; // TEXT column = 65,535 bytes

    private final ProjectTemplateRepository repository;
    private final ObjectMapper objectMapper;
    private final ProjectService projectService;
    private final MilestoneService milestoneService;
    private final SprintService sprintService;
    private final TaskService taskService;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectPhaseService projectPhaseService;
    // ───────────────────────── templates ─────────────────────────

    @Transactional(readOnly = true)
    public List<ProjectTemplateResponse> list() {
        return repository.findAllByOrderByNameAsc().stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProjectTemplateResponse get(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    public ProjectTemplateResponse create(CreateTemplateRequest req, Long userId) {
        String name = req.getName() == null ? "" : req.getName().trim();
        if (name.isEmpty()) throw new IllegalArgumentException("Name is required");
        if (req.getType() == null) throw new IllegalArgumentException("Type is required");
        if (req.getPayload() == null || !req.getPayload().isObject())
            throw new IllegalArgumentException("payload must be a JSON object");
        if (repository.existsByNameIgnoreCase(name))
            throw new DuplicateResourceException("A template named \"" + name + "\" already exists");

        String json = req.getPayload().toString();
        if (json.length() > MAX_PAYLOAD_CHARS) throw new IllegalArgumentException("payload is too large");
        parse(json).validate();

        ProjectTemplate saved = repository.save(ProjectTemplate.builder()
                .name(name).type(req.getType()).description(req.getDescription())
                .payload(json).createdBy(userId).build());
        return toResponse(saved);
    }

    // ───────────────────────── create a project from a template ─────────────────────────

    @Transactional
    public ProjectResponse instantiate(Long templateId, FromTemplateRequest req, Long userId) {
        ProjectTemplate t = find(templateId);
        ProjectTemplatePayload p = parse(t.getPayload());
        p.validate(); // a template edited by hand in SQL is checked again here

        String name = notBlank(req.getName()) ? req.getName().trim() : p.getProject().getName();
        if (!notBlank(name)) throw new IllegalArgumentException("Project name is required");

        LocalDate start = req.getStartDate() != null ? req.getStartDate() : LocalDate.now();
        ProjectPriority priority = req.getPriority() != null ? req.getPriority()
                : notBlank(p.getProject().getPriority())
                ? ProjectPriority.valueOf(p.getProject().getPriority().trim().toUpperCase())
                : ProjectPriority.MEDIUM;
        Long ownerId = req.getOwnerId() != null ? req.getOwnerId() : userId;

        // project end = last milestone / sprint of the plan, unless the caller set one
        LocalDate planEnd = null;
        for (MilestonePart m : p.getMilestones()) planEnd = later(planEnd, start.plusDays(m.getDueOffsetDays()));
        for (SprintPart s : p.getSprints())
            planEnd = later(planEnd, start.plusDays(s.getStartOffsetDays() + s.getDurationDays() - 1L));
        for (PhasePart ph : p.getPhases())
            planEnd = later(planEnd, start.plusDays(ph.getStartOffsetDays() + ph.getDurationDays() - 1L));
        LocalDate end = req.getEndDate() != null ? req.getEndDate() : planEnd;

        ProjectResponse project = projectService.createProject(CreateProjectRequest.builder()
                .name(name).code(req.getCode()).description(req.getDescription())
                .ownerId(ownerId).departmentId(req.getDepartmentId()).clientId(req.getClientId())
                .teamId(req.getTeamId()).priority(priority)
                .startDate(start).endDate(end).budgetTotal(req.getBudgetTotal())
                .memberIds(req.getMemberIds()).build(), userId);
        Long pid = project.getId();

        List<Long> milestoneIds = new ArrayList<>();
        List<LocalDate> milestoneDue = new ArrayList<>();
        for (MilestonePart m : p.getMilestones()) {
            LocalDate due = start.plusDays(m.getDueOffsetDays());
            milestoneIds.add(milestoneService.create(pid, CreateMilestoneRequest.builder()
                    .name(m.getName().trim()).dueDate(due).ownerId(ownerId).build(), userId).getId());
            milestoneDue.add(due);
        }

        List<Long> sprintIds = new ArrayList<>();
        List<LocalDate> sprintEnd = new ArrayList<>();
        for (SprintPart s : p.getSprints()) {
            LocalDate sStart = start.plusDays(s.getStartOffsetDays());
            LocalDate sEnd = sStart.plusDays(s.getDurationDays() - 1L);
            sprintIds.add(sprintService.create(pid, CreateSprintRequest.builder()
                    .name(s.getName().trim()).goal(s.getGoal()).startDate(sStart).endDate(sEnd).build(), userId).getId());
            sprintEnd.add(sEnd);
        }
        for (PhasePart ph : p.getPhases()) {
            LocalDate phStart = start.plusDays(ph.getStartOffsetDays());
            projectPhaseService.create(pid, CreatePhaseRequest.builder()
                    .name(ph.getName().trim())
                    .startDate(phStart)
                    .endDate(phStart.plusDays(ph.getDurationDays() - 1L))
                    .build(), userId);
        }
        // people: MANAGER -> the owner; MEMBER -> round-robin over the project's MEMBER-role members
        List<Long> memberPool = projectMemberRepository.findByProjectId(pid).stream()
                .filter(m -> m.getRole() == ProjectRole.MEMBER)
                .map(ProjectMember::getEmployeeId)
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());
        int[] rr = {0};

        // ─── Create tasks, remembering key → taskId so checklists and dependencies can wire up ───
        Map<String, Long> createdTaskIdByKey = new HashMap<>();
        for (TaskPart tp : p.getTasks()) {
            Long assignee = assignee(tp.getAssignToRole(), ownerId, memberPool, rr);
            LocalDate due = tp.getDueOffsetDays() != null ? start.plusDays(tp.getDueOffsetDays())
                    : tp.getSprintIndex() != null ? sprintEnd.get(tp.getSprintIndex())
                    : tp.getMilestoneIndex() != null ? milestoneDue.get(tp.getMilestoneIndex())
                    : null;
            TaskResponse created = taskService.createTask(CreateTaskRequest.builder()
                    .projectId(pid).title(tp.getTitle().trim())
                    .priority(notBlank(tp.getPriority()) ? TaskPriority.valueOf(tp.getPriority().trim().toUpperCase()) : TaskPriority.MEDIUM)
                    .type(notBlank(tp.getType()) ? TaskType.valueOf(tp.getType().trim().toUpperCase()) : TaskType.TASK)
                    .estimateHours(tp.getEstimateHours()).storyPoints(tp.getStoryPoints()).dueDate(due)
                    .sprintId(tp.getSprintIndex() != null ? sprintIds.get(tp.getSprintIndex()) : null)
                    .milestoneId(tp.getMilestoneIndex() != null ? milestoneIds.get(tp.getMilestoneIndex()) : null)
                    .assigneeIds(assignee == null ? null : List.of(assignee))
                    .build(), userId);
            if (tp.getKey() != null && !tp.getKey().isBlank())
                createdTaskIdByKey.put(tp.getKey(), created.getId());
        }

        // ─── Recreate checklists now that every task has an id ───
        for (TaskPart tp : p.getTasks()) {
            Long taskId = createdTaskIdByKey.get(tp.getKey());
            if (taskId == null || tp.getChecklist() == null) continue;
            for (String item : tp.getChecklist()) {
                taskService.addChecklistItem(taskId,
                        AddChecklistRequest.builder().title(item).build(), userId);
            }
        }

        // ─── Recreate cross-task dependencies now that every task has an id ───
        for (TaskPart tp : p.getTasks()) {
            Long taskId = createdTaskIdByKey.get(tp.getKey());
            if (taskId == null || tp.getDependsOn() == null) continue;
            for (String preKey : tp.getDependsOn()) {
                Long preId = createdTaskIdByKey.get(preKey);
                if (preId == null) continue;   // validator already rejected unknown keys
                taskService.addDependency(taskId,
                        AddDependencyRequest.builder().dependsOnTaskId(preId).type("BLOCKED_BY").build());
            }
        }

        log.info("Project {} created from template {} ({} milestones, {} sprints, {} tasks, {} with checklist, {} with deps)",
                pid, templateId, milestoneIds.size(), sprintIds.size(), p.getTasks().size(),
                createdTaskIdByKey.size(),
                p.getTasks().stream().filter(x -> x.getDependsOn() != null && !x.getDependsOn().isEmpty()).count());
        // Templates create PLANNING projects. Task baselines are captured on first
        // activation in ProjectServiceImpl. If a future path instantiates as ACTIVE, capture now.
        ProjectResponse created = projectService.getProject(pid);
        if (created.getStatus() == com.mentis.hrms.pm.enums.ProjectStatus.ACTIVE
                && created.getBaselinedAt() == null
                && created.getStartDate() != null && created.getEndDate() != null) {
            created = projectService.saveBaseline(pid, userId);
        }
        return created;
    }

    // ───────────────────────── helpers ─────────────────────────

    private Long assignee(String role, Long ownerId, List<Long> pool, int[] rr) {
        if (!notBlank(role)) return null;
        return switch (role.trim().toUpperCase()) {
            case "MANAGER" -> ownerId;
            case "MEMBER" -> pool.isEmpty() ? null : pool.get(rr[0]++ % pool.size());
            default -> null;
        };
    }

    private ProjectTemplatePayload parse(String json) {
        try {
            return objectMapper.readValue(json, ProjectTemplatePayload.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("payload is not a valid template: " + e.getOriginalMessage());
        }
    }

    private ProjectTemplate find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Template not found: " + id));
    }

    private ProjectTemplateResponse toResponse(ProjectTemplate t) {
        int ms = 0, sp = 0, tk = 0;
        com.fasterxml.jackson.databind.JsonNode node = null;
        try {
            node = objectMapper.readTree(t.getPayload());
            ProjectTemplatePayload p = objectMapper.treeToValue(node, ProjectTemplatePayload.class);
            ms = p.getMilestones() == null ? 0 : p.getMilestones().size();
            sp = p.getSprints() == null ? 0 : p.getSprints().size();
            tk = p.getTasks() == null ? 0 : p.getTasks().size();
        } catch (Exception e) {
            log.warn("template {} has an unreadable payload: {}", t.getId(), e.getMessage());
        }
        return ProjectTemplateResponse.builder()
                .id(t.getId()).name(t.getName()).type(t.getType().name()).description(t.getDescription())
                .milestoneCount(ms).sprintCount(sp).taskCount(tk).payload(node)
                .createdBy(t.getCreatedBy()).createdAt(t.getCreatedAt()).build();
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }

    private static LocalDate later(LocalDate a, LocalDate b) {
        return a == null || b.isAfter(a) ? b : a;
    }
}