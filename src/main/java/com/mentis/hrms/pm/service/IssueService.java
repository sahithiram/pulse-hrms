package com.mentis.hrms.pm.service;

import com.mentis.hrms.pm.dto.request.CreateIssueRequest;
import com.mentis.hrms.pm.dto.response.IssueResponse;

import java.util.List;

public interface IssueService {
    IssueResponse create(CreateIssueRequest req, Long userId);
    IssueResponse get(Long id);
    List<IssueResponse> listByProject(Long projectId);
    List<IssueResponse> listByTask(Long taskId);
    IssueResponse updateStatus(Long id, String status, Long userId);
    IssueResponse assign(Long id, Long assigneeId, Long userId);
    void delete(Long id, Long userId);
}