package com.mentis.hrms.pm.service;

import com.mentis.hrms.pm.repository.ProjectMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;

/**
 * PM-010 — the one place that answers "what does an hour of this person cost on this project".
 *
 * The rate lives on pm_project_members.hourly_rate (what the manager agreed per project),
 * not on the employee, because the same person can be billed differently per project.
 * When no rate is set the cost is 0 — never a made-up number.
 */
@Component
@RequiredArgsConstructor
public class ProjectMemberRates {

    private final ProjectMemberRepository projectMemberRepository;

    /** the rate of an employee on any of the given projects (highest wins) */
    public BigDecimal hourlyRate(Long employeeId, Collection<Long> projectIds) {
        if (employeeId == null || projectIds == null || projectIds.isEmpty()) return BigDecimal.ZERO;
        BigDecimal best = BigDecimal.ZERO;
        for (Long pid : projectIds) {
            BigDecimal r = projectMemberRepository.findByProjectIdAndEmployeeId(pid, employeeId)
                    .map(m -> m.getHourlyRate() == null ? BigDecimal.ZERO : m.getHourlyRate())
                    .orElse(BigDecimal.ZERO);
            if (r.compareTo(best) > 0) best = r;
        }
        return best;
    }

    /** cost of the given minutes at that rate */
    public BigDecimal cost(Long employeeId, Collection<Long> projectIds, Integer minutes) {
        int m = minutes == null ? 0 : minutes;
        return hourlyRate(employeeId, projectIds)
                .multiply(BigDecimal.valueOf(m))
                .divide(BigDecimal.valueOf(60), 2, java.math.RoundingMode.HALF_UP);
    }
}
