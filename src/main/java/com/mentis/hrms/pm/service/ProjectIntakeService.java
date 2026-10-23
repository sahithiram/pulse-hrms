package com.mentis.hrms.pm.service;

import com.mentis.hrms.dto.NotificationRequest;
import com.mentis.hrms.model.Employee;
import com.mentis.hrms.pm.dto.request.CreateProjectRequest;
import com.mentis.hrms.pm.dto.request.FromTemplateRequest;
import com.mentis.hrms.pm.dto.request.ProjectRequestDecision;
import com.mentis.hrms.pm.dto.request.SubmitProjectRequest;
import com.mentis.hrms.pm.dto.response.ProjectRequestResponse;
import com.mentis.hrms.pm.dto.response.ProjectResponse;
import com.mentis.hrms.pm.entity.ProjectRequest;
import com.mentis.hrms.pm.enums.ProjectPriority;
import com.mentis.hrms.pm.enums.ProjectRequestStatus;
import com.mentis.hrms.pm.exception.BusinessRuleException;
import com.mentis.hrms.pm.exception.PmAccessDeniedException;
import com.mentis.hrms.pm.exception.ResourceNotFoundException;
import com.mentis.hrms.pm.repository.ProjectRequestRepository;
import com.mentis.hrms.pm.repository.ProjectTemplateRepository;
import com.mentis.hrms.repository.EmployeeRepository;
import com.mentis.hrms.service.NotificationEmitter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/** Project intake: anyone submits an idea, an admin approves (creating the project) or rejects. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectIntakeService {

    private final ProjectRequestRepository repo;
    private final ProjectTemplateRepository templateRepository;
    private final ProjectTemplateService templateService;
    private final ProjectService projectService;
    private final EmployeeRepository employeeRepository;
    private final NotificationEmitter notificationEmitter;

    /** impact 1-5, urgency 1-5, effort 1-5 (5 = biggest)  ->  20..100. */
    public static int score(int impact, int urgency, int effort) {
        return (impact * 2 + urgency * 2 + (6 - effort)) * 4;
    }

    public static ProjectPriority priorityFromScore(Integer score) {
        int s = score == null ? 0 : score;
        if (s >= 80) return ProjectPriority.CRITICAL;
        if (s >= 60) return ProjectPriority.HIGH;
        if (s >= 40) return ProjectPriority.MEDIUM;
        return ProjectPriority.LOW;
    }

    @Transactional
    public ProjectRequestResponse submit(SubmitProjectRequest req, Long userId) {
        String title = text(req.getTitle(), "Title", 200, 3);
        String problem = text(req.getProblem(), "Problem", 5000, 10);
        String outcome = text(req.getExpectedOutcome(), "Expected outcome", 5000, 10);
        int impact = scale(req.getImpact(), "Impact");
        int urgency = scale(req.getUrgency(), "Urgency");
        int effort = scale(req.getEffort(), "Effort");

        ProjectRequest saved = repo.save(ProjectRequest.builder()
                .title(title).problem(problem).expectedOutcome(outcome)
                .impact(impact).urgency(urgency).effort(effort)
                .priorityScore(score(impact, urgency, effort))
                .status(ProjectRequestStatus.SUBMITTED).createdBy(userId).build());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ProjectRequestResponse> list(Long userId, boolean admin) {
        List<ProjectRequest> rows = admin ? repo.findAllByOrderByIdDesc() : repo.findByCreatedByOrderByIdDesc(userId);
        return rows.stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProjectRequestResponse get(Long id, Long userId, boolean admin) {
        ProjectRequest r = find(id);
        if (!admin && !r.getCreatedBy().equals(userId))
            throw new PmAccessDeniedException("You can only see your own project requests.");
        return toResponse(r);
    }

    /** Admin only (checked by the controller). Creates the project in the same transaction. */
    @Transactional
    public ProjectRequestResponse approve(Long id, ProjectRequestDecision d, Long adminId) {
        ProjectRequest r = find(id);
        if (r.getStatus() != ProjectRequestStatus.SUBMITTED)
            throw new BusinessRuleException("This request has already been " + r.getStatus().name().toLowerCase());

        Long ownerId = d != null && d.getOwnerId() != null ? d.getOwnerId() : r.getCreatedBy();
        if (!employeeRepository.existsById(ownerId))
            throw new IllegalArgumentException("Owner not found: " + ownerId);
        Long templateId = d == null ? null : d.getTemplateId();
        if (templateId != null && !templateRepository.existsById(templateId))
            throw new ResourceNotFoundException("Template not found: " + templateId);

        ProjectPriority priority = priorityFromScore(r.getPriorityScore());
        String description = r.getProblem() + "\n\nExpected outcome: " + r.getExpectedOutcome();

        ProjectResponse project = templateId != null
                ? templateService.instantiate(templateId, FromTemplateRequest.builder()
                .name(r.getTitle()).description(description).ownerId(ownerId)
                .priority(priority).startDate(LocalDate.now()).build(), adminId)
                : projectService.createProject(CreateProjectRequest.builder()
                .name(r.getTitle()).description(description).ownerId(ownerId)
                .priority(priority).build(), adminId);

        r.setStatus(ProjectRequestStatus.APPROVED);
        r.setDecidedBy(adminId);
        r.setDecidedAt(LocalDateTime.now());
        r.setProjectId(project.getId());
        r.setTemplateId(templateId);
        r.setNote(d != null && d.getNote() != null && !d.getNote().isBlank() ? cut(d.getNote().trim(), 1000) : null);
        ProjectRequest saved = repo.save(r);

        notifyRequester(saved, adminId, true, "Your request became the project \"" + project.getName() + "\"");
        return toResponse(saved);
    }

    @Transactional
    public ProjectRequestResponse reject(Long id, ProjectRequestDecision d, Long adminId) {
        ProjectRequest r = find(id);
        if (r.getStatus() != ProjectRequestStatus.SUBMITTED)
            throw new BusinessRuleException("This request has already been " + r.getStatus().name().toLowerCase());
        String note = d == null || d.getNote() == null ? "" : d.getNote().trim();
        if (note.isEmpty()) throw new IllegalArgumentException("A reason is required when rejecting a request");

        r.setStatus(ProjectRequestStatus.REJECTED);
        r.setDecidedBy(adminId);
        r.setDecidedAt(LocalDateTime.now());
        r.setNote(cut(note, 1000));
        ProjectRequest saved = repo.save(r);

        notifyRequester(saved, adminId, false, saved.getNote());
        return toResponse(saved);
    }

    // ───────────────────────── helpers ─────────────────────────

    private ProjectRequest find(Long id) {
        return repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Project request not found: " + id));
    }

    private static String text(String v, String label, int max, int min) {
        String t = v == null ? "" : v.trim();
        if (t.isEmpty()) throw new IllegalArgumentException(label + " is required");
        if (t.length() < min) throw new IllegalArgumentException(label + " is too short (at least " + min + " characters)");
        if (t.length() > max) throw new IllegalArgumentException(label + " is too long (max " + max + " characters)");
        return t;
    }

    private static int scale(Integer v, String label) {
        if (v == null || v < 1 || v > 5) throw new IllegalArgumentException(label + " must be between 1 and 5");
        return v;
    }

    private static String cut(String s, int max) { return s.length() > max ? s.substring(0, max) : s; }

    private void notifyRequester(ProjectRequest r, Long actorId, boolean approved, String message) {
        if (r.getCreatedBy().equals(actorId)) return;
        try {
            String code = employeeRepository.findById(r.getCreatedBy()).map(Employee::getEmployeeId).orElse(null);
            if (code == null) return;
            String type = approved ? "PROJECT_REQUEST_APPROVED" : "PROJECT_REQUEST_REJECTED";
            notificationEmitter.emit(NotificationRequest.builder()
                    .recipientId(code).recipientType("EMPLOYEE").type(type).action(type)
                    .title("Project request " + (approved ? "approved" : "rejected") + ": " + r.getTitle())
                    .message(message).projectId(r.getProjectId())
                    .actorId(actorId).actorName(nameOf(actorId)).priority("NORMAL")
                    .deepLink(approved ? "/pm/04-projects" : "/pm/04-projects#requests").build());
        } catch (Exception e) {
            log.warn("project request notify failed: {}", e.getMessage());
        }
    }

    private String nameOf(Long pk) {
        if (pk == null) return null;
        return employeeRepository.findById(pk).map(e -> {
            String f = e.getFirstName() == null ? "" : e.getFirstName();
            String l = e.getLastName() == null ? "" : e.getLastName();
            String full = (f + " " + l).trim();
            return full.isEmpty() ? "Employee #" + pk : full;
        }).orElse("Employee #" + pk);
    }

    private ProjectRequestResponse toResponse(ProjectRequest r) {
        return ProjectRequestResponse.builder()
                .id(r.getId()).title(r.getTitle()).problem(r.getProblem()).expectedOutcome(r.getExpectedOutcome())
                .impact(r.getImpact()).urgency(r.getUrgency()).effort(r.getEffort())
                .priorityScore(r.getPriorityScore()).suggestedPriority(priorityFromScore(r.getPriorityScore()).name())
                .status(r.getStatus().name())
                .createdBy(r.getCreatedBy()).createdByName(nameOf(r.getCreatedBy())).createdAt(r.getCreatedAt())
                .decidedBy(r.getDecidedBy()).decidedByName(nameOf(r.getDecidedBy())).decidedAt(r.getDecidedAt())
                .note(r.getNote()).projectId(r.getProjectId()).templateId(r.getTemplateId()).build();
    }
}