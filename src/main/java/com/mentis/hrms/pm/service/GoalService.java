package com.mentis.hrms.pm.service;

import com.mentis.hrms.pm.dto.request.CreateGoalRequest;
import com.mentis.hrms.pm.dto.request.CreateKeyResultRequest;
import com.mentis.hrms.pm.dto.response.GoalResponse;
import com.mentis.hrms.pm.dto.response.KeyResultResponse;

import java.util.List;

public interface GoalService {
    GoalResponse create(CreateGoalRequest req, Long userId);
    GoalResponse get(Long id);
    List<GoalResponse> listByProject(Long projectId);
    List<GoalResponse> listByQuarter(String quarter, Integer year);
    GoalResponse update(Long id, CreateGoalRequest req, Long userId);
    void delete(Long id, Long userId);

    KeyResultResponse addKeyResult(Long goalId, CreateKeyResultRequest req, Long userId);
    KeyResultResponse updateKeyResult(Long krId, CreateKeyResultRequest req, Long userId);
    void deleteKeyResult(Long krId, Long userId);
}