package com.mentis.hrms.pm.service;

import com.mentis.hrms.pm.dto.request.CreateProjectRequest;
import com.mentis.hrms.pm.dto.request.UpdateProjectRequest;
import com.mentis.hrms.pm.dto.response.BaselineHistoryResponse;
import com.mentis.hrms.pm.dto.response.PageResponse;
import com.mentis.hrms.pm.dto.response.ProjectResponse;
import com.mentis.hrms.pm.dto.response.ProjectSummaryResponse;
import com.mentis.hrms.pm.enums.ProjectStatus;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ProjectService {

    ProjectResponse createProject(CreateProjectRequest req, Long currentUserId);

    /** The next free project code (PRJ-1001, PRJ-1002, ...). Used to pre-fill the create form. */
    String nextProjectCode();

    ProjectResponse updateProject(Long id, UpdateProjectRequest req, Long currentUserId);
    ProjectResponse rebaseline(Long id, String reason, Long currentUserId);
    ProjectResponse getProject(Long id);
    ProjectResponse saveBaseline(Long id, Long currentUserId);

    /** Same as {@link #saveBaseline(Long, Long)}; {@code reason} is required when a baseline already exists. */
    ProjectResponse saveBaseline(Long id, Long currentUserId, String reason);

    /** PM-026: hand the project over to another owner (SA always, current owner otherwise). */
    ProjectResponse transferOwner(Long id, Long newOwnerId, String reason,
                                  boolean keepPreviousAsManager, Long currentUserId);

    /** PM-025: add/promote MANAGER rows for the given employees. */
    void applyManagers(Long projectId, Long ownerId, java.util.List<Long> managerIds, Long actorId);
    PageResponse<ProjectSummaryResponse> listProjects(String search,
                                                      ProjectStatus status,
                                                      Long ownerId,
                                                      Pageable pageable);

    ProjectResponse archiveProject(Long id, Long currentUserId);
    ProjectResponse reopenProject(Long id, String reason, Long currentUserId);
    void deleteProject(Long id, Long currentUserId);

    PageResponse<ProjectSummaryResponse> listVisibleProjects(String search,
                                                             ProjectStatus status,
                                                             Long ownerId,
                                                             Long viewerId,
                                                             Pageable pageable);

    /** Version history of every baseline / re-baseline for the project, newest first. */
    List<BaselineHistoryResponse> baselineHistory(Long id);
}