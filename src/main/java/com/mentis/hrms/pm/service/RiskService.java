package com.mentis.hrms.pm.service;

import com.mentis.hrms.pm.dto.request.CreateRiskRequest;
import com.mentis.hrms.pm.dto.response.RiskResponse;

import java.util.List;

public interface RiskService {
    RiskResponse create(CreateRiskRequest req, Long userId);
    RiskResponse get(Long id);
    List<RiskResponse> listByProject(Long projectId);
    RiskResponse updateStatus(Long id, String status, Long userId);
    RiskResponse updateMitigation(Long id, String mitigation, Long userId);
    void delete(Long id, Long userId);
}