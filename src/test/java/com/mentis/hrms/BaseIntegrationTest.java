package com.mentis.hrms;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Locale;

/**
 * Base for the DB-backed integration tests shipped in Sprint 1/2.
 *
 * SAFETY: truncateAll() REFUSES to run against anything that does not look like a local/test
 * database (URL must contain "_test"/"testdb", point at localhost, or you must set
 * HRMS_ALLOW_DB_TESTS=1). It can NEVER wipe the production Clever Cloud database.
 *
 * By default tests run on an in-memory H2 database (see application-test.properties).
 * To use real MySQL instead, create the test database once (schema = a copy of your dev database):
 *   mysqldump -u root -p mentis_hrms > /tmp/hrms-schema.sql
 *   mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS mentis_hrms_test"
 *   mysql -u root -p mentis_hrms_test < /tmp/hrms-schema.sql
 * Flyway is intentionally NOT used by tests (see application-test.properties).
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    @Autowired protected JdbcTemplate jdbcTemplate;
    @Autowired private Environment environment;

    protected void truncateAll() {
        guardAgainstNonTestDatabase();

        boolean h2 = getUrl().contains(":h2:");
        String fkOff = h2 ? "SET REFERENTIAL_INTEGRITY FALSE" : "SET FOREIGN_KEY_CHECKS = 0";
        String fkOn  = h2 ? "SET REFERENTIAL_INTEGRITY TRUE"  : "SET FOREIGN_KEY_CHECKS = 1";

        jdbcTemplate.execute(fkOff);
        try {
            for (String table : List.of(
                    "employee_job_history", "employee_salary_history", "employee_delegate",
                    "employees", "access_permission")) {
                try {
                    jdbcTemplate.execute("TRUNCATE TABLE " + table);
                } catch (Exception ex) {
                    // table may not exist yet on an older schema - safe to skip in tests
                }
            }
        } finally {
            jdbcTemplate.execute(fkOn);
        }
    }

    private String getUrl() {
        return environment.getProperty("spring.datasource.url", "").toLowerCase(Locale.ROOT);
    }

    private void guardAgainstNonTestDatabase() {
        String url = getUrl();
        boolean looksTest = url.contains("_test") || url.contains("testdb")
                || url.contains("localhost") || url.contains("127.0.0.1")
                || "true".equalsIgnoreCase(System.getenv("HRMS_ALLOW_DB_TESTS"));
        if (!looksTest) {
            throw new IllegalStateException(
                    "Refusing to run integration tests against datasource: " + url
                            + " - point spring.datasource.url at a LOCAL/TEST database "
                            + "(or set HRMS_ALLOW_DB_TESTS=1 if you really mean it).");
        }
    }
}