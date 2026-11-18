package com.mentis.hrms.pm.config;

import com.mentis.hrms.pm.entity.Project;
import com.mentis.hrms.pm.entity.ProjectTeam;
import com.mentis.hrms.pm.repository.ProjectRepository;
import com.mentis.hrms.pm.repository.ProjectTeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * One-time, idempotent backfill of pm_project_teams from pm_projects.team_id.
 * <p>
 * {@code ddl-auto=update} creates the table but does not copy data. This runner
 * copies legacy single-team values so the new "projects where I'm on a team" query
 * in {@code searchVisible} sees projects created before multi-team existed.
 * <p>
 * Safe to run on every boot: it inserts only (project_id, team_id) pairs that are
 * not already present. Projects with a null team_id are skipped.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PmTeamBackfillRunner implements ApplicationRunner {

    private final ProjectRepository projectRepository;
    private final ProjectTeamRepository projectTeamRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Project> projects = projectRepository.findAll();
        if (projects.isEmpty()) {
            log.info("[pm-team-backfill] no projects; nothing to backfill");
            return;
        }

        // Snapshot of what is already linked, so the loop only inserts missing rows.
        Set<String> existing = new HashSet<>();
        for (ProjectTeam pt : projectTeamRepository.findByProjectIdIn(
                projects.stream().map(Project::getId).toList())) {
            existing.add(pt.getProjectId() + ":" + pt.getTeamId());
        }

        int inserted = 0;
        for (Project p : projects) {
            Long tid = p.getTeamId();
            if (tid == null) continue;
            String key = p.getId() + ":" + tid;
            if (existing.contains(key)) continue;
            projectTeamRepository.save(ProjectTeam.builder()
                    .projectId(p.getId())
                    .teamId(tid)
                    .build());
            existing.add(key);
            inserted++;
        }

        if (inserted > 0) {
            log.info("[pm-team-backfill] linked {} legacy project→team row(s)", inserted);
        } else {
            log.info("[pm-team-backfill] nothing to do (already consistent)");
        }
    }
}