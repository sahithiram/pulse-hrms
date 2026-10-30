package com.mentis.hrms.pm.service.impl;

import com.mentis.hrms.pm.dto.request.CreatePhaseRequest;
import com.mentis.hrms.pm.dto.response.ProjectPhaseResponse;
import com.mentis.hrms.pm.entity.ProjectPhase;
import com.mentis.hrms.pm.enums.ProjectPhaseStatus;
import com.mentis.hrms.pm.exception.BusinessRuleException;
import com.mentis.hrms.pm.exception.ResourceNotFoundException;
import com.mentis.hrms.pm.realtime.PmEventPublisher;
import com.mentis.hrms.pm.repository.ProjectPhaseRepository;
import com.mentis.hrms.pm.repository.ProjectRepository;
import com.mentis.hrms.pm.service.ProjectPhaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectPhaseServiceImpl implements ProjectPhaseService {

    private final ProjectPhaseRepository repo;
    private final ProjectRepository projectRepository;
    private final PmEventPublisher publisher;

    @Override
    @Transactional(readOnly = true)
    public List<ProjectPhaseResponse> listByProject(Long projectId) {
        return repo.findByProjectIdAndStatusNotOrderByStartDateAsc(projectId, ProjectPhaseStatus.CANCELLED)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ProjectPhaseResponse create(Long projectId, CreatePhaseRequest req, Long userId) {
        if (!projectRepository.existsById(projectId))
            throw new ResourceNotFoundException("Project not found: " + projectId);
        assertDates(req.getStartDate(), req.getEndDate());
        ProjectPhase saved = repo.save(ProjectPhase.builder()
                .projectId(projectId)
                .name(req.getName())
                .startDate(req.getStartDate())
                .endDate(req.getEndDate())
                .status(req.getStatus() == null ? ProjectPhaseStatus.PLANNED : req.getStatus())
                .createdBy(userId)
                .build());
        publisher.publish("POST", "/api/pm/projects/" + projectId + "/phases", String.valueOf(userId));
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectPhaseResponse get(Long id) {
        return toResponse(find(id));
    }

    @Override
    @Transactional
    public ProjectPhaseResponse update(Long id, CreatePhaseRequest req, Long userId) {
        ProjectPhase m = find(id);
        if (req.getName() != null) m.setName(req.getName());
        if (req.getStartDate() != null) m.setStartDate(req.getStartDate());
        if (req.getEndDate() != null) m.setEndDate(req.getEndDate());
        if (req.getStatus() != null) m.setStatus(req.getStatus());
        assertDates(m.getStartDate(), m.getEndDate());
        ProjectPhase saved = repo.save(m);
        publisher.publish("PUT", "/api/pm/phases/" + id, String.valueOf(userId));
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(Long id, Long userId) {
        ProjectPhase m = find(id);
        m.setStatus(ProjectPhaseStatus.CANCELLED);
        repo.save(m);
        publisher.publish("DELETE", "/api/pm/phases/" + id, String.valueOf(userId));
    }

    private ProjectPhase find(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Phase not found: " + id));
    }

    private void assertDates(java.time.LocalDate start, java.time.LocalDate end) {
        if (start != null && end != null && end.isBefore(start))
            throw new BusinessRuleException("End date cannot be before the start date");
    }

    private ProjectPhaseResponse toResponse(ProjectPhase m) {
        return ProjectPhaseResponse.builder()
                .id(m.getId()).projectId(m.getProjectId()).name(m.getName())
                .startDate(m.getStartDate()).endDate(m.getEndDate())
                .status(m.getStatus())
                .createdAt(m.getCreatedAt()).updatedAt(m.getUpdatedAt())
                .build();
    }
}
