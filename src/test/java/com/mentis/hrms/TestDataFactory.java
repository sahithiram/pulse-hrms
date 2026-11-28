package com.mentis.hrms;

import com.mentis.hrms.model.Employee;
import com.mentis.hrms.model.enums.UserRole;
import com.mentis.hrms.repository.EmployeeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Test bean creating minimal, valid employees (employee_id + email are the only NOT NULL,
 * uniqueness-constrained columns). employmentStatus defaults to CONFIRMED so managers are
 * assignable; tests that need EXITED set it explicitly. Unique suffix => safe across tests in one JVM.
 */
@Component
public class TestDataFactory {

    private static final AtomicLong SEQ = new AtomicLong(System.currentTimeMillis() % 100000);

    @Autowired private EmployeeRepository employeeRepository;

    public Employee newEmployee() {
        long n = SEQ.incrementAndGet();
        String unique = n + "-" + UUID.randomUUID().toString().substring(0, 8);
        Employee e = new Employee();
        e.setEmployeeId("TST-" + unique);
        e.setFirstName("Test");
        e.setLastName("User" + n);
        e.setEmployeeName("Test User" + n);
        e.setEmail("test-" + unique + "@example.test");
        e.setEmploymentStatus("CONFIRMED");   // assignable manager; tests override to EXITED/INACTIVE when needed
        e.setEmploymentMode("FULL_TIME");
        e.setWorkMode("OFFICE");
        e.setStatus("Active");
        e.setActive(true);
        e.setRole(UserRole.EMPLOYEE);
        return employeeRepository.save(e);
    }
}
