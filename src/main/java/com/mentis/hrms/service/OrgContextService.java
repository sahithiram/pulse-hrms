package com.mentis.hrms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mentis.hrms.service.ai.QueryIntentClassifier;
import com.mentis.hrms.service.ai.QueryIntentClassifier.Domain;

// TODO: replace with your real repository package/names -- these are
// placeholders until you confirm the actual entities/repos.
import com.mentis.hrms.repository.EmployeeRepository;
import com.mentis.hrms.repository.LeaveRequestRepository;
import com.mentis.hrms.repository.JobRepository;
import com.mentis.hrms.repository.JobApplicationRepository;
import com.mentis.hrms.repository.LocationRepository;
import com.mentis.hrms.repository.AnnouncementRepository;
import com.mentis.hrms.repository.HolidayRepository;
import com.mentis.hrms.repository.RolePermissionRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Year;
import java.util.List;
import java.util.Set;

/**
 * Builds the "ORGANIZATION-WIDE DATA" context string that gets passed into
 * AdminAIService.processAdminChat(employeeId, message, context, ragContext).
 *
 * This is a SEPARATE class from AdminAIService on purpose -- your real
 * AdminAIService only knows how to talk to Groq; it doesn't hold any
 * repositories. This class owns the repositories and decides, per message,
 * which sections of live data are actually relevant, instead of dumping
 * every table every time (which is what was blowing your Groq TPM limit).
 *
 * WIRING (in whatever currently builds the full context -- controller or
 * another service):
 *   String context = orgContextService.buildScopedOrgContext(message);
 *   String ragContext = ...; // unchanged
 *   AdminAIResult result = adminAIService.processAdminChat(employeeId, message, context, ragContext);
 */
@Service
public class OrgContextService {

    private static final Logger log = LoggerFactory.getLogger(OrgContextService.class);

    // Keep the *context* string comfortably under Groq's TPM ceiling for
    // your model (12,000 for llama-3.3-70b-versatile on the free tier).
    // This budget is for context alone -- leaves room for the system
    // prompt, RAG policy block, tool definitions, and chat history, since
    // Groq's TPM counts input + output together.
    private static final int MAX_CONTEXT_TOKENS = 4000;
    private static final int MAX_RECORDS_PER_SECTION = 30;

    private final QueryIntentClassifier intentClassifier = new QueryIntentClassifier();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final JobRepository jobRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final LocationRepository locationRepository;
    private final AnnouncementRepository announcementRepository;
    private final HolidayRepository holidayRepository;
    private final RolePermissionRepository rolePermissionRepository;

    public OrgContextService(EmployeeRepository employeeRepository,
                             LeaveRequestRepository leaveRequestRepository,
                             JobRepository jobRepository,
                             JobApplicationRepository jobApplicationRepository,
                             LocationRepository locationRepository,
                             AnnouncementRepository announcementRepository,
                             HolidayRepository holidayRepository,
                             RolePermissionRepository rolePermissionRepository) {
        this.employeeRepository = employeeRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.jobRepository = jobRepository;
        this.jobApplicationRepository = jobApplicationRepository;
        this.locationRepository = locationRepository;
        this.announcementRepository = announcementRepository;
        this.holidayRepository = holidayRepository;
        this.rolePermissionRepository = rolePermissionRepository;
    }

    /**
     * Only pulls the sections relevant to what the admin actually asked,
     * instead of every table every time.
     */
    public String buildScopedOrgContext(String userMessage) {
        Set<Domain> domains = intentClassifier.classify(userMessage);

        StringBuilder ctx = new StringBuilder(buildOrgSummary());

        if (domains.isEmpty()) {
            // e.g. "hii", "thanks" -- summary only, no detail needed.
            return ctx.toString();
        }

        if (domains.contains(Domain.EMPLOYEES)) {
            appendSection(ctx, "EMPLOYEES", employeeRepository.count(),
                    employeeRepository.findAll(PageRequest.of(0, MAX_RECORDS_PER_SECTION)).getContent());
        }
        if (domains.contains(Domain.LEAVE)) {
            appendSection(ctx, "LEAVE REQUESTS", leaveRequestRepository.count(),
                    leaveRequestRepository.findAll(PageRequest.of(0, MAX_RECORDS_PER_SECTION)).getContent());
        }
        if (domains.contains(Domain.RECRUITMENT)) {
            appendSection(ctx, "OPEN JOBS", jobRepository.count(),
                    jobRepository.findAll(PageRequest.of(0, MAX_RECORDS_PER_SECTION)).getContent());
            appendSection(ctx, "RECENT APPLICATIONS", jobApplicationRepository.count(),
                    jobApplicationRepository.findAll(PageRequest.of(0, MAX_RECORDS_PER_SECTION)).getContent());
        }
        if (domains.contains(Domain.LOCATIONS)) {
            appendSection(ctx, "LOCATIONS", locationRepository.count(), locationRepository.findAll());
        }
        if (domains.contains(Domain.ANNOUNCEMENTS)) {
            appendSection(ctx, "ANNOUNCEMENTS", announcementRepository.count(),
                    announcementRepository.findAll(PageRequest.of(0, MAX_RECORDS_PER_SECTION)).getContent());
        }
        if (domains.contains(Domain.HOLIDAYS)) {
            appendSection(ctx, "HOLIDAYS " + Year.now(), holidayRepository.count(), holidayRepository.findAll());
        }
        if (domains.contains(Domain.ROLE_PERMISSIONS)) {
            appendSection(ctx, "ROLES & PERMISSIONS", rolePermissionRepository.count(), rolePermissionRepository.findAll());
        }

        return enforceTokenBudget(ctx.toString());
    }

    private String buildOrgSummary() {
        return """
                ORG SUMMARY:
                - Employees: %d
                - Leave requests: %d
                - Open jobs: %d
                - Locations: %d
                """.formatted(
                employeeRepository.count(),
                leaveRequestRepository.count(),
                jobRepository.count(),
                locationRepository.count());
    }

    private void appendSection(StringBuilder ctx, String label, long totalCount, List<?> records) {
        ctx.append("\n").append(label).append(" (showing ").append(records.size())
                .append(" of ").append(totalCount).append("):\n");
        try {
            // Jackson respects any @JsonIgnore already on your entities --
            // make sure sensitive fields (salary, SSN/tax IDs, password
            // hashes) are marked @JsonIgnore, otherwise they get sent to
            // Groq as part of this context.
            ctx.append(objectMapper.writeValueAsString(records)).append("\n");
        } catch (Exception e) {
            log.warn("Failed to serialize {} section for AI context", label, e);
        }
    }

    /**
     * Safety net: even scoped, one very "chatty" domain could still be big
     * (e.g. thousands of leave requests). If still over budget after
     * scoping, fall back to summary-only instead of risking another 413/429.
     */
    private String enforceTokenBudget(String context) {
        int estimatedTokens = estimateTokens(context);
        if (estimatedTokens <= MAX_CONTEXT_TOKENS) {
            return context;
        }
        log.warn("Scoped context still too large ({} est. tokens) -- falling back to summary only", estimatedTokens);
        return buildOrgSummary()
                + "\n(Full records omitted -- too many to include safely. Ask a more "
                + "specific question, e.g. 'pending leave requests for Engineering', to narrow it down.)";
    }

    private int estimateTokens(String text) {
        // ~4 characters per token is a reasonable rule of thumb for English.
        // It's a safety-check estimate, not an exact tokenizer count.
        return text == null ? 0 : (int) Math.ceil(text.length() / 4.0);
    }
}
