package com.mentis.hrms.pm.service;

import com.mentis.hrms.pm.dto.request.CreateProjectRequest;
import com.mentis.hrms.pm.dto.request.UpdateProjectRequest;
import com.mentis.hrms.pm.dto.response.PageResponse;
import com.mentis.hrms.pm.dto.response.ProjectResponse;
import com.mentis.hrms.pm.dto.response.ProjectSummaryResponse;
import com.mentis.hrms.pm.enums.ProjectStatus;
import org.springframework.data.domain.Pageable;

public interface ProjectService {

    ProjectResponse createProject(CreateProjectRequest req, Long currentUserId);

    ProjectResponse updateProject(Long id, UpdateProjectRequest req, Long currentUserId);

    ProjectResponse getProject(Long id);
    ProjectResponse saveBaseline(Long id, Long currentUserId);
    PageResponse<ProjectSummaryResponse> listProjects(String search,
                                                      ProjectStatus status,
                                                      Long ownerId,
                                                      Pageable pageable);

    ProjectResponse archiveProject(Long id, Long currentUserId);

    void deleteProject(Long id, Long currentUserId);
    PageResponse<ProjectSummaryResponse> listVisibleProjects(String search,
                                                             ProjectStatus status,
                                                             Long ownerId,
                                                             Long viewerId,
                                                             Pageable pageable);

}