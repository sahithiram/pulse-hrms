package com.mentis.hrms.pm.service;

import com.mentis.hrms.pm.dto.request.ConvertRetroItemRequest;
import com.mentis.hrms.pm.dto.request.CreateTaskRequest;
import com.mentis.hrms.pm.dto.request.RetroItemRequest;
import com.mentis.hrms.pm.dto.response.RetroResponse;
import com.mentis.hrms.pm.dto.response.RetroSummaryResponse;
import com.mentis.hrms.pm.dto.response.TaskResponse;
import com.mentis.hrms.pm.entity.*;
import com.mentis.hrms.pm.enums.RetroCategory;
import com.mentis.hrms.pm.enums.SprintStatus;
import com.mentis.hrms.pm.enums.TaskPriority;
import com.mentis.hrms.pm.enums.TaskType;
import com.mentis.hrms.pm.exception.BusinessRuleException;
import com.mentis.hrms.pm.exception.PmAccessDeniedException;
import com.mentis.hrms.pm.exception.ResourceNotFoundException;
import com.mentis.hrms.pm.repository.*;
import com.mentis.hrms.pm.security.PmAccessService;
import com.mentis.hrms.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RetroService {

    private final SprintRepository sprintRepo;
    private final SprintRetroRepository retroRepo;
    private final SprintRetroItemRepository itemRepo;
    private final ProjectMemberRepository memberRepo;
    private final EmployeeRepository employeeRepo;
    private final ActivityLogRepository activityRepo;
    private final TaskService taskService;
    private final PmAccessService access;

    /* ───────── permissions ───────── */

    private Sprint sprint(Long sprintId) {
        return sprintRepo.findById(sprintId)
                .orElseThrow(() -> new ResourceNotFoundException("Sprint not found: " + sprintId));
    }

    private boolean isManager(Long userId, Long projectId) {
        return access.canAdministerProject(userId, projectId);
    }

    private boolean isSprintMember(Long userId, Long projectId) {
        return memberRepo.findByProjectIdAndEmployeeId(projectId, userId)
                .map(m -> !"VIEWER".equals(m.getRole().name())).orElse(false);
    }

    private boolean started(Sprint s) { return s.getStatus() != SprintStatus.PLANNED; }

    private boolean canEdit(Long userId, Sprint s, SprintRetro r) {
        if (access.isProjectLocked(s.getProjectId())) return false;
        if (r != null && r.getClosedAt() != null) return false;
        if (!started(s)) return false;
        return isManager(userId, s.getProjectId()) || isSprintMember(userId, s.getProjectId());
    }

    private void requireEdit(Long userId, Sprint s, SprintRetro r) {
        access.requireCanViewProject(userId, s.getProjectId());
        if (!canEdit(userId, s, r)) {
            if (r != null && r.getClosedAt() != null) throw new BusinessRuleException("This retrospective is closed (read-only).");
            if (!started(s)) throw new BusinessRuleException("The retrospective opens once the sprint has started.");
            if (access.isProjectLocked(s.getProjectId())) throw new BusinessRuleException("This project is completed or archived.");
            throw new PmAccessDeniedException("Only sprint members and managers can edit the retrospective.");
        }
    }

    private void requireManager(Long userId, Sprint s) {
        access.requireCanViewProject(userId, s.getProjectId());
        if (!isManager(userId, s.getProjectId()))
            throw new PmAccessDeniedException("Only a manager can do this.");
    }

    /* ───────── read ───────── */

    @Transactional(readOnly = true)
    public RetroResponse get(Long sprintId, Long userId) {
        Sprint s = sprint(sprintId);
        access.requireCanViewProject(userId, s.getProjectId());
        SprintRetro r = retroRepo.findBySprintId(sprintId).orElse(null);
        boolean manager = isManager(userId, s.getProjectId());
        boolean edit = canEdit(userId, s, r);
        List<SprintRetroItem> items = r == null ? List.of() : itemRepo.findByRetroIdOrderBySortOrderAscIdAsc(r.getId());
        return RetroResponse.builder()
                .sprintId(s.getId()).sprintName(s.getName()).sprintStatus(s.getStatus().name())
                .exists(r != null).closed(r != null && r.getClosedAt() != null).closedAt(r == null ? null : r.getClosedAt())
                .canEdit(edit).canManage(manager).legacyNotes(s.getRetroNotes())
                .items(items.stream().map(i -> toItem(i, userId, edit, manager)).collect(Collectors.toList()))
                .build();
    }

    @Transactional(readOnly = true)
    public List<RetroSummaryResponse> history(Long projectId, Long userId) {
        access.requireCanViewProject(userId, projectId);
        List<Sprint> sprints = sprintRepo.findAll().stream()
                .filter(s -> projectId.equals(s.getProjectId()))
                .sorted(Comparator.comparing(Sprint::getStartDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
        Map<Long, SprintRetro> byS = retroRepo.findBySprintIdIn(sprints.stream().map(Sprint::getId).collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(SprintRetro::getSprintId, x -> x));
        Map<Long, List<SprintRetroItem>> items = byS.isEmpty() ? Map.of()
                : itemRepo.findByRetroIdIn(byS.values().stream().map(SprintRetro::getId).collect(Collectors.toList()))
                .stream().collect(Collectors.groupingBy(SprintRetroItem::getRetroId));
        List<RetroSummaryResponse> out = new ArrayList<>();
        for (Sprint s : sprints) {
            SprintRetro r = byS.get(s.getId());
            List<SprintRetroItem> li = r == null ? List.of() : items.getOrDefault(r.getId(), List.of());
            out.add(RetroSummaryResponse.builder()
                    .sprintId(s.getId()).sprintName(s.getName()).sprintStatus(s.getStatus().name())
                    .startDate(s.getStartDate()).endDate(s.getEndDate())
                    .hasRetro(r != null).closed(r != null && r.getClosedAt() != null)
                    .wentWell((int) li.stream().filter(i -> i.getCategory() == RetroCategory.WENT_WELL).count())
                    .toImprove((int) li.stream().filter(i -> i.getCategory() == RetroCategory.TO_IMPROVE).count())
                    .actions((int) li.stream().filter(i -> i.getCategory() == RetroCategory.ACTION).count())
                    .converted((int) li.stream().filter(i -> i.getConvertedTaskId() != null).count())
                    .build());
        }
        return out;
    }

    /* ───────── write ───────── */

    @Transactional
    public RetroResponse.Item addItem(Long sprintId, RetroItemRequest req, Long userId) {
        Sprint s = sprint(sprintId);
        SprintRetro r = retroRepo.findBySprintId(sprintId).orElse(null);
        requireEdit(userId, s, r);
        if (req.getCategory() == null) throw new BusinessRuleException("category is required");
        if (r == null) r = retroRepo.save(SprintRetro.builder().sprintId(sprintId).createdBy(userId).build());
        int next = itemRepo.findByRetroIdOrderBySortOrderAscIdAsc(r.getId()).size();
        SprintRetroItem i = SprintRetroItem.builder()
                .retroId(r.getId()).category(req.getCategory()).text(req.getText().trim())
                .sortOrder(next).createdBy(userId).build();
        if (req.getCategory() == RetroCategory.ACTION) { i.setAssigneeId(req.getAssigneeId()); i.setDueDate(req.getDueDate()); }
        i = itemRepo.save(i);
        log(s.getProjectId(), "RETRO_ITEM_ADDED", userId, req.getCategory() + ": " + i.getText());
        return toItem(i, userId, true, isManager(userId, s.getProjectId()));
    }

    @Transactional
    public RetroResponse.Item updateItem(Long sprintId, Long itemId, RetroItemRequest req, Long userId) {
        Sprint s = sprint(sprintId);
        SprintRetro r = retroOf(sprintId);
        requireEdit(userId, s, r);
        SprintRetroItem i = item(r, itemId);
        if (i.getConvertedTaskId() != null) throw new BusinessRuleException("This action item was already converted to a task.");
        i.setText(req.getText().trim());
        if (i.getCategory() == RetroCategory.ACTION) { i.setAssigneeId(req.getAssigneeId()); i.setDueDate(req.getDueDate()); }
        return toItem(itemRepo.save(i), userId, true, isManager(userId, s.getProjectId()));
    }

    @Transactional
    public void deleteItem(Long sprintId, Long itemId, Long userId) {
        Sprint s = sprint(sprintId);
        SprintRetro r = retroOf(sprintId);
        requireEdit(userId, s, r);
        SprintRetroItem i = item(r, itemId);
        if (!userId.equals(i.getCreatedBy()) && !isManager(userId, s.getProjectId()))
            throw new PmAccessDeniedException("You can only delete your own items.");
        itemRepo.delete(i);                       // a converted task is never deleted with the item
        log(s.getProjectId(), "RETRO_ITEM_DELETED", userId, i.getText());
    }

    /** One click: action item -> real task through TaskService. */
    @Transactional
    public TaskResponse convert(Long sprintId, Long itemId, ConvertRetroItemRequest req, Long userId) {
        Sprint s = sprint(sprintId);
        SprintRetro r = retroOf(sprintId);
        requireEdit(userId, s, r);
        SprintRetroItem i = item(r, itemId);
        if (i.getCategory() != RetroCategory.ACTION) throw new BusinessRuleException("Only action items can become tasks.");
        if (i.getConvertedTaskId() != null) throw new BusinessRuleException("Already converted to task #" + i.getConvertedTaskId());

        Long assignee = req != null && req.getAssigneeId() != null ? req.getAssigneeId() : i.getAssigneeId();
        var due = req != null && req.getDueDate() != null ? req.getDueDate() : i.getDueDate();
        Long targetSprint = req == null ? null : req.getSprintId();
        if (targetSprint != null) {
            Sprint ts = sprint(targetSprint);
            if (!s.getProjectId().equals(ts.getProjectId()))
                throw new BusinessRuleException("That sprint belongs to another project.");
            if (ts.getStatus() != SprintStatus.PLANNED && ts.getStatus() != SprintStatus.ACTIVE)
                throw new BusinessRuleException("Pick a planned or active sprint (or leave empty for the backlog).");
        }
        String title = i.getText().length() > 300 ? i.getText().substring(0, 299) + "…" : i.getText();
        TaskResponse t = taskService.createTask(CreateTaskRequest.builder()
                .projectId(s.getProjectId()).sprintId(targetSprint)
                .title(title).description("From retrospective of " + s.getName())
                .priority(TaskPriority.MEDIUM).type(TaskType.TASK).dueDate(due)
                .assigneeIds(assignee == null ? null : List.of(assignee))
                .build(), userId);
        i.setConvertedTaskId(t.getId());
        itemRepo.save(i);
        log(s.getProjectId(), "RETRO_ITEM_CONVERTED", userId, i.getText() + " → task #" + t.getId());
        return t;
    }

    @Transactional
    public RetroResponse close(Long sprintId, Long userId) {
        Sprint s = sprint(sprintId);
        requireManager(userId, s);
        SprintRetro r = retroRepo.findBySprintId(sprintId)
                .orElseGet(() -> retroRepo.save(SprintRetro.builder().sprintId(sprintId).createdBy(userId).build()));
        r.setClosedAt(LocalDateTime.now()); r.setClosedBy(userId);
        retroRepo.save(r);
        log(s.getProjectId(), "RETRO_CLOSED", userId, s.getName());
        return get(sprintId, userId);
    }

    @Transactional
    public RetroResponse reopen(Long sprintId, Long userId) {
        Sprint s = sprint(sprintId);
        requireManager(userId, s);
        if (access.isProjectLocked(s.getProjectId())) throw new BusinessRuleException("This project is completed or archived.");
        SprintRetro r = retroOf(sprintId);
        r.setClosedAt(null); r.setClosedBy(null);
        retroRepo.save(r);
        log(s.getProjectId(), "RETRO_REOPENED", userId, s.getName());
        return get(sprintId, userId);
    }

    /* ───────── helpers ───────── */

    private SprintRetro retroOf(Long sprintId) {
        return retroRepo.findBySprintId(sprintId).orElseThrow(() -> new ResourceNotFoundException("No retrospective for this sprint yet."));
    }

    private SprintRetroItem item(SprintRetro r, Long itemId) {
        SprintRetroItem i = itemRepo.findById(itemId).orElseThrow(() -> new ResourceNotFoundException("Item not found: " + itemId));
        if (!r.getId().equals(i.getRetroId())) throw new ResourceNotFoundException("Item not found: " + itemId);
        return i;
    }

    private RetroResponse.Item toItem(SprintRetroItem i, Long userId, boolean canEdit, boolean manager) {
        return RetroResponse.Item.builder()
                .id(i.getId()).category(i.getCategory()).text(i.getText())
                .assigneeId(i.getAssigneeId()).assigneeName(i.getAssigneeId() == null ? null : nameOf(i.getAssigneeId()))
                .dueDate(i.getDueDate()).convertedTaskId(i.getConvertedTaskId())
                .createdBy(i.getCreatedBy()).createdByName(i.getCreatedBy() == null ? null : nameOf(i.getCreatedBy()))
                .createdAt(i.getCreatedAt())
                .canDelete(canEdit && (manager || userId.equals(i.getCreatedBy())))
                .canConvert(canEdit && i.getCategory() == RetroCategory.ACTION && i.getConvertedTaskId() == null)
                .build();
    }

    private String nameOf(Long pk) {
        return employeeRepo.findById(pk).map(e -> {
            String full = ((e.getFirstName() == null ? "" : e.getFirstName()) + " " + (e.getLastName() == null ? "" : e.getLastName())).trim();
            return full.isEmpty() ? "Employee #" + pk : full;
        }).orElse("Employee #" + pk);
    }

    private void log(Long projectId, String action, Long userId, String msg) {
        try {
            activityRepo.save(ActivityLog.builder().entityType("PROJECT").entityId(projectId).action(action)
                    .actorId(userId).actorName(nameOf(userId)).newValue(msg)
                    .message(action.replace('_', ' ').toLowerCase() + ": " + msg).build());
        } catch (Exception e) { log.warn("retro activity log failed: {}", e.getMessage()); }
    }
}