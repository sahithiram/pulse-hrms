package com.mentis.hrms.pm.security;

import com.mentis.hrms.pm.dto.response.PageResponse;
import com.mentis.hrms.pm.dto.response.ProjectResponse;
import com.mentis.hrms.pm.dto.response.ProjectSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * STEP 1b: hides budget numbers from anyone who cannot ADMINISTER the project
 * (Super Admin, owner, project MANAGER see them; everybody else gets nulls and
 * financeVisible=false so the UI can show a dash instead of a misleading 0).
 *
 * Only call this on responses going OUT to a viewer. Never mask inside services:
 * guardBaselinedChanges and the finance service need the real numbers.
 */
@Component
@RequiredArgsConstructor
public class ProjectFinanceMasker {

    private final PmAccessService access;

    /** One project (GET /projects/{id}). */
    public ProjectResponse mask(ProjectResponse r, Long userId) {
        if (r == null) return null;
        if (access.canViewFinance(userId, r.getId())) {
            r.setFinanceVisible(true);
            return r;
        }
        r.setBudgetTotal(null);
        r.setBudgetSpent(null);
        r.setBaselineBudget(null);
        r.setFinanceVisible(false);
        return r;
    }

    /** A list of summaries. Computes the viewer's managed projects once, not per row. */
    public List<ProjectSummaryResponse> maskAll(List<ProjectSummaryResponse> rows, Long userId) {
        if (rows == null || rows.isEmpty()) return rows;
        boolean superAdmin = access.isSuperAdmin(userId);
        Set<Long> managed = superAdmin ? Set.of() : access.managedProjectIds(userId);
        for (ProjectSummaryResponse s : rows) {
            boolean visible = superAdmin
                    || managed.contains(s.getId())
                    || (userId != null && Objects.equals(userId, s.getOwnerId()));
            if (visible) {
                s.setFinanceVisible(true);
            } else {
                s.setBudgetTotal(null);
                s.setBudgetSpent(null);
                s.setFinanceVisible(false);
            }
        }
        return rows;
    }

    /** A page of summaries (GET /projects). */
    public PageResponse<ProjectSummaryResponse> maskPage(PageResponse<ProjectSummaryResponse> page, Long userId) {
        if (page != null) maskAll(page.getContent(), userId);
        return page;
    }
}