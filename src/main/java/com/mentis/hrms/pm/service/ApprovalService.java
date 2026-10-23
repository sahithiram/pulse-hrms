package com.mentis.hrms.pm.service;

import com.mentis.hrms.pm.dto.request.CreateApprovalRequest;
import com.mentis.hrms.pm.dto.request.DecisionRequest;
import com.mentis.hrms.pm.dto.response.ApprovalResponse;

import java.util.List;

public interface ApprovalService {
    ApprovalResponse create(CreateApprovalRequest req, Long userId);
    ApprovalResponse get(Long id);
    List<ApprovalResponse> listMine(Long userId);           // pending for me
    List<ApprovalResponse> listRequested(Long userId);      // pending I raised
    List<ApprovalResponse> listAll(String status);
    ApprovalResponse decide(Long id, DecisionRequest req, Long userId);
    void cancel(Long id, Long userId);
}