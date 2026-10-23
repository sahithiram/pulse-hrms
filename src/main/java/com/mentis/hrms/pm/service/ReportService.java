package com.mentis.hrms.pm.service;

import com.mentis.hrms.pm.dto.response.ReportDtos.*;

import java.time.LocalDate;
import java.util.List;

public interface ReportService {
    List<BudgetRow> budget(Long projectId);
    List<VelocityRow> velocity(Long projectId, int lastSprints);
    QualityReport quality(Long projectId);
    RiskReport risk(Long projectId);
    List<UtilizationRow> utilization(LocalDate from, LocalDate to);

    String budgetCsv(Long projectId);
    String velocityCsv(Long projectId, int lastSprints);
    String qualityCsv(Long projectId);
    String riskCsv(Long projectId);
    String utilizationCsv(LocalDate from, LocalDate to);
}