package com.mentis.hrms.pm.service;

import com.mentis.hrms.pm.dto.request.AddMemberRequest;
import com.mentis.hrms.pm.dto.request.UpdateMemberRequest;
import com.mentis.hrms.pm.dto.response.ProjectMemberResponse;

import java.util.List;

public interface ProjectMemberService {

    List<ProjectMemberResponse> list(Long projectId, boolean includeRates);

    ProjectMemberResponse add(Long projectId, AddMemberRequest req, Long actorPk);

    ProjectMemberResponse update(Long projectId, Long memberId, UpdateMemberRequest req, Long actorPk);

    void remove(Long projectId, Long memberId, Long actorPk);
}