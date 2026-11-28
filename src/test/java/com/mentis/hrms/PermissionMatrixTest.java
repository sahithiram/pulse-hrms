package com.mentis.hrms;

import com.mentis.hrms.model.AccessPermission;
import com.mentis.hrms.repository.AccessPermissionRepository;
import com.mentis.hrms.service.PermissionMatrix;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

/** Self-contained: does not depend on V4 seed rows (Flyway is off in this test slice). */
class PermissionMatrixTest extends BaseIntegrationTest {

    @Autowired PermissionMatrix matrix;
    @Autowired AccessPermissionRepository repo;

    @BeforeEach void clean() { truncateAll(); }

    private void allow(String role, String module, String action) {
        AccessPermission p = new AccessPermission();
        p.setRole(role); p.setModule(module); p.setAction(action); p.setAllowed(true);
        repo.saveAndFlush(p);
    }

    @Test
    void superAdmin_bypasses_everything() {
        assertTrue(matrix.isAllowed("SUPER_ADMIN", "leave", "DELETE"));
        assertTrue(matrix.isAllowed("super_admin", "salary", "MANAGE"));
    }

    @Test
    void unknownModule_isDenied() {
        matrix.refresh();
        assertFalse(matrix.isAllowed("EMPLOYEE", "quantum_core", "VIEW"));
    }

    @Test
    void explicitAllow_and_defaultDeny() {
        allow("EMPLOYEE", "leave", "VIEW");
        allow("EMPLOYEE", "leave", "CREATE");
        allow("HR_MANAGER", "salary", "MANAGE");
        matrix.refresh();

        assertTrue(matrix.isAllowed("EMPLOYEE", "leave", "VIEW"));
        assertTrue(matrix.isAllowed("EMPLOYEE", "leave", "CREATE"));
        assertFalse(matrix.isAllowed("EMPLOYEE", "salary", "MANAGE"));
        assertFalse(matrix.isAllowed("HR", "salary", "MANAGE"));
        assertTrue(matrix.isAllowed("HR_MANAGER", "salary", "MANAGE"));
        assertTrue(matrix.isAllowed("HR_MANAGER", "salary", "DELETE"), "MANAGE implies other actions");
    }

    @Test
    void disabledRow_isDenied() {
        allow("HR", "report", "EXPORT");
        AccessPermission deny = repo.findByRoleAndModuleAndAction("HR", "report", "EXPORT").orElseThrow();
        deny.setAllowed(false);
        repo.saveAndFlush(deny);
        matrix.refresh();
        assertFalse(matrix.isAllowed("HR", "report", "EXPORT"));
    }
}
