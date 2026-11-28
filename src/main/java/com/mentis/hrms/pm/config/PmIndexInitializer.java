package com.mentis.hrms.pm.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Creates the extra indexes the Project Management queries need, only if they are missing.
 *
 * Why a runner and not @Index on the entities: it works for every developer's database
 * (local or shared) without touching the entity files, and it is safe to run on every start.
 * The indexes that the entities already declare (idx_pm_task_project, idx_pm_ta_task, ...) are not repeated here.
 *
 * Turn off with: pm.indexes.auto-create=false
 */
@Component
@Profile("!test")
@Order(100)
@Slf4j
public class PmIndexInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    @Value("${pm.indexes.auto-create:true}")
    private boolean enabled;

    public PmIndexInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** {table, indexName, columns} */
    private static final String[][] INDEXES = {
            // pm_tasks: filters used by sprint / milestone boards, overdue sweeps and "my tasks"
            {"pm_tasks", "idx_pm_task_sprint", "sprint_id"},
            {"pm_tasks", "idx_pm_task_milestone", "milestone_id"},
            {"pm_tasks", "idx_pm_task_due", "due_date"},
            {"pm_tasks", "idx_pm_task_creator", "created_by"},
            {"pm_tasks", "idx_pm_task_proj_parent", "project_id, parent_task_id"},
            // "blocked by" lookups (the entity only indexes task_id)
            {"pm_task_dependencies", "idx_pm_dep_dependson", "depends_on_task_id"},
            // permission checks run EXISTS(task, employee) / EXISTS(team, employee) on every request
            {"pm_task_assignments", "idx_pm_ta_task_emp", "task_id, employee_id"},
            {"pm_team_members", "idx_pm_tm_team_emp", "team_id, employee_id"},
            {"pm_task_watchers", "idx_pm_wt_task_emp", "task_id, employee_id"},
    };

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) return;
        int created = 0;
        int failed = 0;
        for (String[] ix : INDEXES) {
            String table = ix[0], name = ix[1], cols = ix[2];
            try {
                if (!tableExists(table) || indexExists(table, name)) continue;
                jdbc.execute("CREATE INDEX " + name + " ON " + table + " (" + cols + ")");
                created++;
                log.info("[pm-index] created {} on {}({})", name, table, cols);
            } catch (Exception e) {
                // never block startup because of an index
                failed++;
                log.warn("[pm-index] could not create {} on {}: {}", name, table, e.getMessage());
            }
        }
        if (failed > 0) {
            log.warn("[pm-index] {} index(es) could not be created, see warnings above", failed);
        } else if (created == 0) {
            log.info("[pm-index] all PM indexes already present");
        }
    }

    private boolean tableExists(String table) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?",
                Integer.class, table);
        return n != null && n > 0;
    }

    private boolean indexExists(String table, String index) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?",
                Integer.class, table, index);
        return n != null && n > 0;
    }
}