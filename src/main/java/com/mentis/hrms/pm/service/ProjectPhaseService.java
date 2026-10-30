package com.mentis.hrms.pm.service;

import com.mentis.hrms.pm.dto.request.CreatePhaseRequest;
import com.mentis.hrms.pm.dto.response.ProjectPhaseResponse;

import java.util.List;

public interface ProjectPhaseService {

    List<ProjectPhaseResponse> listByProject(Long projectId);

    ProjectPhaseResponse create(Long projectId, CreatePhaseRequest req, Long userId);

    ProjectPhaseResponse get(Long id);

    ProjectPhaseResponse update(Long id, CreatePhaseRequest req, Long userId);

    void delete(Long id, Long userId);
}
