package com.mentis.hrms.service;

import com.mentis.hrms.model.Employee;
import com.mentis.hrms.model.JobApplication;
import com.mentis.hrms.model.OfferLetter;
import com.mentis.hrms.repository.HrmsSeqRepository;
import com.mentis.hrms.repository.OfferLetterRepository;
import com.mentis.hrms.util.PipelineTransitionRules;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * REQ-7-06 — accepting an offer creates ONE Employee in ONBOARDING, links the hired application,
 * copies data over in the SAME transaction. ANY missing mandatory field → exception → FULL ROLLBACK
 * (no employee, no HIRED stage, offer stays SENT). Nothing retyped by humans.
 */
@Service
public class OfferHandoffService {

    @Autowired private OfferLetterRepository offerLetterRepository;

    @Lazy
    @Autowired private PipelineService pipelineService;

    @Autowired private HrmsSeqRepository seqRepository;

    @Autowired(required = false)
    private HrmsNotificationService notificationService;

    @PersistenceContext
    private EntityManager em;

    @Value("${hrms.probation.days:90}")
    private int probationDays;

    public record HandoffResult(Long employeePk, String employeeCode, LocalDate probationEndDate) {}

    /** Pure validation — unit-tested. */
    public static List<String> missingMandatoryFields(String candidateName, String candidateEmail,
                                                      String designation, String department,
                                                      String annualSalary, String joiningDateRaw,
                                                      String employmentType, String workLocation) {
        List<String> missing = new ArrayList<>();
        if (isBlank(candidateName)) missing.add("candidateName");
        if (isBlank(candidateEmail)) missing.add("candidateEmail");
        if (isBlank(designation)) missing.add("designation");
        if (isBlank(department)) missing.add("department");
        if (isBlank(annualSalary)) missing.add("annualSalary");
        if (isBlank(joiningDateRaw)) missing.add("joiningDate");
        if (isBlank(employmentType)) missing.add("employmentType");
        if (isBlank(workLocation)) missing.add("workLocation");
        return missing;
    }

    @Transactional
    public HandoffResult handoffOnAcceptance(Long offerLetterId, String acceptedBy) {
        OfferLetter offer = offerLetterRepository.findById(offerLetterId)
                .orElseThrow(() -> new IllegalArgumentException("OfferLetter not found: " + offerLetterId));

        String joiningRaw = offer.getJoiningDate();
        LocalDate joining = parseJoiningDate(joiningRaw);
        List<String> missing = missingMandatoryFields(offer.getCandidateName(), offer.getCandidateEmail(),
                offer.getDesignation(), offer.getDepartment(), offer.getAnnualSalary(),
                joiningRaw, offer.getEmploymentType(), offer.getWorkLocation());
        if (joining == null) missing.add("joiningDate (unparseable: '" + joiningRaw + "')");
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Cannot accept offer — missing/invalid mandatory data: "
                    + String.join(", ", missing) + ". No employee was created.");
        }

        String email = offer.getCandidateEmail().trim().toLowerCase(Locale.ROOT);
        Number dup = (Number) em.createNativeQuery("SELECT COUNT(*) FROM employees WHERE lower(email) = lower(?)")
                .setParameter(1, email).getSingleResult();
        if (dup.longValue() > 0) {
            throw new IllegalStateException("Cannot accept offer — an employee with email " + email
                    + " already exists. No employee was created.");
        }

        String fullName = offer.getCandidateName().trim();
        String[] parts = fullName.split("\\s+", 2);
        String code = String.format("EMP-%d-%06d", LocalDate.now().getYear(), seqRepository.next("EMPLOYEE"));
        LocalDate probationEnd = joining.plusDays(probationDays);

        Employee e = new Employee();
        e.setEmployeeId(code);
        e.setEmployeeName(fullName);
        e.setFirstName(parts[0]);
        e.setLastName(parts.length > 1 ? parts[1] : "");
        e.setEmail(email);
        e.setPersonalEmail(email);
        e.setDesignation(offer.getDesignation());
        e.setDepartment(offer.getDepartment());
        e.setWorkLocation(offer.getWorkLocation());
        e.setEmploymentType(offer.getEmploymentType());
        e.setSalary(offer.getAnnualSalary());
        e.setCurrency(offer.getCurrency());
        e.setManager(offer.getReportingManager());
        e.setStartDate(joining);
        e.setStatus("Active");
        e.setActive(true);
        e.setIsActive(true);
        e.setOnboardingStatus("NOT_STARTED");
        e.setOnboardingStartDate(LocalDateTime.now());
        e.setCreatedDate(LocalDateTime.now());
        e.setUpdatedDate(LocalDateTime.now());
        em.persist(e);
        em.flush(); // get PK

        // V3 lifecycle columns — entity does NOT map them (verified); native SQL, column names verified in V3 migration.
        try {
            em.createNativeQuery(
                            "UPDATE employees SET employment_status = 'ONBOARDING', employment_mode = ?, work_mode = 'OFFICE', " +
                                    "probation_end_date = ?, salary_amount = ?, start_date = ? WHERE id = ?")
                    .setParameter(1, employmentMode(offer.getEmploymentType()))
                    .setParameter(2, probationEnd)
                    .setParameter(3, parseSalary(offer.getAnnualSalary()))
                    .setParameter(4, joining)
                    .setParameter(5, e.getId())
                    .executeUpdate();
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Employee created but V3 lifecycle update failed — rolled back entirely. Cause: " + ex.getMessage()
                            + " (Is Sprint 1 V3 applied? employment_status/chk constraints exist?)", ex);
        }

        // Effective-dated history row (V3 table; change_reason NOT NULL).
        em.createNativeQuery(
                        "INSERT INTO employee_job_history (employee_id, effective_from, change_reason, changed_by, created_at) " +
                                "VALUES (?, CURRENT_DATE, 'OFFER_ACCEPTED', ?, NOW())")
                .setParameter(1, e.getId())
                .setParameter(2, parseActorId(acceptedBy))
                .executeUpdate();

        // Application → HIRED in the same transaction (standalone offers have no application — skip, log).
        Long applicationId = null;
        try {
            JobApplication app = offer.getApplication();
            if (app != null) applicationId = app.getId();
        } catch (Exception ignored) {
            // LAZY proxy not resolvable / no session in this path — skip silently, employee already safe.
        }
        if (applicationId != null) {
            String current = pipelineService.currentStage(applicationId);
            if (!PipelineTransitionRules.isTerminal(current)) {
                pipelineService.transition(applicationId, PipelineTransitionRules.HIRED, acceptedBy, "Offer accepted");
            }
        }

        if (notificationService != null) {
            Map<String, String> ph = Map.of(
                    "title", "Offer accepted — " + fullName,
                    "body", fullName + " accepted offer #" + offerLetterId + ". Probation ends " + probationEnd + ".");
            notificationService.notify("HR", "GENERIC", ph, "/employees/" + code, "OFFER-ACCEPT-" + offerLetterId);
        }

        return new HandoffResult(e.getId(), code, probationEnd);
    }

    private static String employmentMode(String employmentType) {
        if (employmentType == null) return "FULL_TIME";
        String t = employmentType.trim().toUpperCase(Locale.ROOT);
        if (t.startsWith("FULL")) return "FULL_TIME";
        if (t.startsWith("PART")) return "PART_TIME";
        if (t.startsWith("CONTRACT") || t.startsWith("CONSULT")) return "CONTRACT";
        if (t.startsWith("INTERN")) return "INTERN";
        return "FULL_TIME";
    }

    /** Keeps the leading numeric token; handles "₹12,00,000", "1200000", "To be discussed" (→ null). */
    public static BigDecimal parseSalary(String raw) {   // FIX: was package-private
        if (raw == null) return null;
        String cleaned = raw.replaceAll("[^0-9.]", "");
        if (cleaned.isEmpty()) return null;
        int firstDot = cleaned.indexOf('.');
        int lastDot = cleaned.lastIndexOf('.');
        if (firstDot != lastDot) { // multiple dots — drop decimals, keep integer part
            cleaned = cleaned.substring(0, firstDot);
        }
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static LocalDate parseJoiningDate(String raw) {   // FIX: was package-private
        if (raw == null) return null;
        String s = raw.trim();
        List<DateTimeFormatter> fmts = List.of(
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        for (DateTimeFormatter fmt : fmts) {
            try {
                return LocalDate.parse(s, fmt);
            } catch (DateTimeParseException ignored) {
                // try next
            }
        }
        if (s.length() >= 10) {
            try {
                return LocalDate.parse(s.substring(0, 10), DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Long parseActorId(String acceptedBy) {
        if (acceptedBy == null) return null;
        try {
            return Long.parseLong(acceptedBy.trim());
        } catch (NumberFormatException e) {
            return null; // change_reason remains the audit trail; actor id optional
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}