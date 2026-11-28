package com.mentis.hrms;

import com.mentis.hrms.model.Employee;
import com.mentis.hrms.model.enums.EmploymentStatus;
import com.mentis.hrms.repository.EmployeeJobHistoryRepository;
import com.mentis.hrms.repository.EmployeeRepository;
import com.mentis.hrms.service.EmployeeOrgService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class EmployeeOrgServiceTest extends BaseIntegrationTest {

    @Autowired EmployeeOrgService orgService;
    @Autowired EmployeeRepository employeeRepository;
    @Autowired EmployeeJobHistoryRepository jobHistoryRepository;
    @Autowired TestDataFactory factory;

    @BeforeEach void clean() { truncateAll(); }

    // ---------- Lifecycle matrix ----------
    @Test
    void lifecycle_allowedTransitions_pass() {
        assertDoesNotThrow(() -> orgService.assertTransitionAllowed(EmploymentStatus.ONBOARDING, EmploymentStatus.PROBATION));
        assertDoesNotThrow(() -> orgService.assertTransitionAllowed(EmploymentStatus.PROBATION, EmploymentStatus.CONFIRMED));
        assertDoesNotThrow(() -> orgService.assertTransitionAllowed(EmploymentStatus.PROBATION, EmploymentStatus.NOTICE));
        assertDoesNotThrow(() -> orgService.assertTransitionAllowed(EmploymentStatus.CONFIRMED, EmploymentStatus.NOTICE));
        assertDoesNotThrow(() -> orgService.assertTransitionAllowed(EmploymentStatus.NOTICE, EmploymentStatus.EXITED));
    }

    @Test
    void lifecycle_forbiddenTransitions_rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> orgService.assertTransitionAllowed(EmploymentStatus.ONBOARDING, EmploymentStatus.CONFIRMED));
        assertThrows(IllegalArgumentException.class,
                () -> orgService.assertTransitionAllowed(EmploymentStatus.CONFIRMED, EmploymentStatus.PROBATION));
        assertThrows(IllegalArgumentException.class,
                () -> orgService.assertTransitionAllowed(EmploymentStatus.EXITED, EmploymentStatus.PROBATION));
        assertThrows(IllegalArgumentException.class,
                () -> orgService.assertTransitionAllowed(EmploymentStatus.NOTICE, EmploymentStatus.CONFIRMED));
    }

    // ---------- Self manager ----------
    @Test
    void selfManager_rejected() {
        Employee e = factory.newEmployee();
        assertThrows(IllegalArgumentException.class,
                () -> orgService.assertAssignableManager(e, e));
    }

    // ---------- Cycle detection at depth 1 (A↔B), 2 (A→B→C→A), 5 (loop in middle) ----------
    @Test
    void reportingCycle_depth1_rejected() {
        Employee a = factory.newEmployee();
        Employee b = factory.newEmployee();
        b.setManagerEmployeeId(a.getId());
        employeeRepository.save(b);
        // a under b now creates A→B→A
        assertThrows(IllegalArgumentException.class,
                () -> orgService.assertAssignableManager(a, b));
    }

    @Test
    void reportingCycle_depth2_rejected() {
        Employee a = factory.newEmployee();
        Employee b = factory.newEmployee();
        Employee c = factory.newEmployee();
        b.setManagerEmployeeId(a.getId());
        c.setManagerEmployeeId(b.getId());
        employeeRepository.saveAll(java.util.List.of(b, c));
        // a under c creates A→C→B→A (depth 3 walk)
        assertThrows(IllegalArgumentException.class,
                () -> orgService.assertAssignableManager(a, c));
    }

    @Test
    void reportingChain_depth5_validTree_accepted() {
        Employee r = factory.newEmployee();
        Employee[] chain = new Employee[5];
        Long parent = r.getId();
        for (int i = 0; i < 5; i++) {
            chain[i] = factory.newEmployee();
            chain[i].setManagerEmployeeId(parent);
            chain[i] = employeeRepository.save(chain[i]);
            parent = chain[i].getId();
        }
        Employee leaf = factory.newEmployee();
        Employee newManager = chain[4];
        assertDoesNotThrow(() -> orgService.assertAssignableManager(leaf, newManager));
    }

    // ---------- Inactive manager rejected ----------
    @Test
    void exitedManager_rejected() {
        Employee e = factory.newEmployee();
        Employee exitedDraft = factory.newEmployee();
        exitedDraft.setEmploymentStatus("EXITED");
        exitedDraft.setActive(false);
        // FIX: assign once into a final variable so the lambda below can capture it
        final Employee ex = employeeRepository.save(exitedDraft);
        assertThrows(IllegalArgumentException.class,
                () -> orgService.assertAssignableManager(e, ex));
    }

    // ---------- History written on change ----------
    @Test
    void changeJobInfo_closesPreviousHistoryAndOpensNew() {
        Employee e = factory.newEmployee();
        Employee mgr = factory.newEmployee();
        Long changedById = mgr.getId();

        // seed opening row via service
        orgService.changeJobInfo(e.getId(), null, null, null, null,
                "FULL_TIME", "OFFICE", java.time.LocalDate.now().minusYears(1),
                "initial", changedById, false);

        orgService.changeJobInfo(e.getId(), null, null, null, mgr.getId(),
                "FULL_TIME", "HYBRID", java.time.LocalDate.now(),
                "reorg", changedById, false);

        var rows = jobHistoryRepository.findByEmployeeIdOrderByEffectiveFromDesc(e.getId());
        assertEquals(2, rows.size(), "opening + new row expected");
        var current = jobHistoryRepository.findFirstByEmployeeIdAndEffectiveToIsNull(e.getId()).orElseThrow();
        assertEquals("reorg", current.getChangeReason());
        assertEquals(mgr.getId(), current.getManagerEmployeeId());
        assertNotNull(rows.get(1).getEffectiveTo(), "previous row must be closed");
    }
}