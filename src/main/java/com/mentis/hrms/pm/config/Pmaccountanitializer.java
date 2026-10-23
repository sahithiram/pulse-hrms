package com.mentis.hrms.config;

import com.mentis.hrms.model.Employee;
import com.mentis.hrms.model.enums.UserRole;
import com.mentis.hrms.repository.EmployeeRepository;
import com.mentis.hrms.util.PasswordUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Runs on every startup against the database the app is connected to
 * (Clever Cloud). Makes sure exactly one working PM001 account exists.
 *
 * Turn it off later with:  pm.seed.enabled=false
 * Stop it from resetting the password on each start with:  pm.seed.reset-password=false
 */
@Component
@Order(100)
public class Pmaccountanitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(Pmaccountanitializer.class);

    private static final String PM_ID = "PM001";

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private PasswordUtil passwordUtil;

    @Value("${pm.seed.enabled:true}")
    private boolean enabled;

    @Value("${pm.seed.password:Pm@12345}")
    private String defaultPassword;

    @Value("${pm.seed.reset-password:true}")
    private boolean resetPassword;

    @Override
    public void run(String... args) {
        if (!enabled) {
            return;
        }
        try {
            // findAll + filter (not findByEmployeeId) so duplicates / trailing spaces cannot crash this
            List<Employee> matches = employeeRepository.findAll().stream()
                    .filter(e -> e.getEmployeeId() != null && PM_ID.equalsIgnoreCase(e.getEmployeeId().trim()))
                    .sorted(Comparator.comparing(Employee::getId))
                    .collect(Collectors.toList());

            Employee pm;
            if (matches.isEmpty()) {
                pm = new Employee();
                pm.setFirstName("Project");
                pm.setLastName("Manager");
                pm.setEmployeeName("Project Manager");
                pm.setEmail("pm001@example.com");
                pm.setPersonalEmail("pm001@example.com");
                pm.setPhone("9999999999");
                pm.setDesignation("Project Manager");
                pm.setCreatedDate(LocalDateTime.now());
                logger.info("[PM-SEED] PM001 not found - creating it");
            } else {
                pm = matches.get(0); // keep the oldest row
                for (int i = 1; i < matches.size(); i++) {
                    try {
                        employeeRepository.delete(matches.get(i));
                        logger.warn("[PM-SEED] Removed duplicate PM001 row id={}", matches.get(i).getId());
                    } catch (Exception ex) {
                        logger.error("[PM-SEED] Could not remove duplicate id={}: {}",
                                matches.get(i).getId(), ex.getMessage());
                    }
                }
            }

            pm.setEmployeeId(PM_ID);
            pm.setRole(UserRole.PM);
            pm.setActive(true);
            pm.setIsActive(true);
            pm.setCredentialsCreated(true);
            pm.setStatus("Active");
            pm.setUpdatedDate(LocalDateTime.now());

            if (pm.getPassword() == null || pm.getPassword().isEmpty() || resetPassword) {
                pm.setPassword(passwordUtil.encodePassword(defaultPassword));
            }

            employeeRepository.saveAndFlush(pm);
            logger.info("[PM-SEED] PM001 is ready (id={}, role=PM, active=true)", pm.getId());

        } catch (Exception e) {
            logger.error("[PM-SEED] Failed to prepare PM001: {}", e.getMessage(), e);
        }
    }
}