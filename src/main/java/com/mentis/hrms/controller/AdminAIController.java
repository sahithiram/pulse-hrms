package com.mentis.hrms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mentis.hrms.model.Employee;
import com.mentis.hrms.model.LeaveRequest;
import com.mentis.hrms.model.Location;
import com.mentis.hrms.model.JobApplication;
import com.mentis.hrms.model.OnboardingDocument;
import com.mentis.hrms.repository.LeaveRequestRepository;
import com.mentis.hrms.service.AdminAIService;
import com.mentis.hrms.service.AdminReportService;
import com.mentis.hrms.service.AttendanceService;
import com.mentis.hrms.service.EmployeeService;
import com.mentis.hrms.service.DepartmentService;
import com.mentis.hrms.service.JobService;
import com.mentis.hrms.service.JobApplicationService;
import com.mentis.hrms.service.OfferService;
import com.mentis.hrms.service.DocumentService;
import com.mentis.hrms.service.NotificationService;
import com.mentis.hrms.service.AnnouncementService;
import com.mentis.hrms.service.HolidayService;
import com.mentis.hrms.service.LocationService;
import com.mentis.hrms.service.LeaveBalanceService;
import com.mentis.hrms.service.LeaveActionService;
import com.mentis.hrms.dto.AnnouncementDTO;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.mentis.hrms.repository.RolePermissionRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import com.mentis.hrms.service.ai.QueryIntentClassifier;
import com.mentis.hrms.service.ai.QueryIntentClassifier.Domain;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mentis.hrms.model.Job;
@RestController
@RequestMapping("/api/chat/admin")
public class AdminAIController {

    private static final Logger logger = LoggerFactory.getLogger(AdminAIController.class);

    @Autowired
    private AdminAIService adminAIService;

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Autowired private DepartmentService departmentService;
    @Autowired private JobService jobService;
    @Autowired private JobApplicationService jobApplicationService;
    @Autowired private OfferService offerService;
    @Autowired private DocumentService documentService;
    @Autowired private NotificationService notificationService;
    @Autowired private AnnouncementService announcementService;
    @Autowired private HolidayService holidayService;
    @Autowired private LocationService locationService;
    @Autowired private LeaveBalanceService leaveBalanceService;
    private final QueryIntentClassifier intentClassifier = new QueryIntentClassifier();
    private static final int MAX_GLOBAL_CONTEXT_CHARS = 16000; // ~4000 tokens safety cap
    // ── NEW: needed so the Super Admin can actually DO things, not just read ──
    @Autowired private LeaveActionService leaveActionService;

    // ── NEW: report/download generation ──
    @Autowired private AdminReportService adminReportService;

    // In-memory store for generated report files, keyed by a random token.
    // Simple and fine for a single-instance deployment; if you scale to
    // multiple app instances behind a load balancer, swap this for Redis
    // or a temp-file-on-disk approach instead.
    private static final Map<String, AdminReportService.GeneratedReport> REPORT_STORE = new java.util.concurrent.ConcurrentHashMap<>();

    @Autowired private RolePermissionRepository rolePermissionRepository;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .registerModule(new com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    // Date patterns for detecting user requests
    private static final List<Pattern> DATE_PATTERNS = Arrays.asList(
            Pattern.compile("(\\d{4}-\\d{2}-\\d{2})"),  // 2026-07-03
            Pattern.compile("(\\d{2}/\\d{2}/\\d{4})"),  // 07/03/2026
            Pattern.compile("(\\d{2}-\\d{2}-\\d{4})"),  // 07-03-2026
            Pattern.compile("(\\d{1,2})\\s+(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)\\s+(\\d{4})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)\\s+(\\d{1,2}),?\\s+(\\d{4})", Pattern.CASE_INSENSITIVE)
    );

    // Month patterns for detecting monthly requests
    private static final List<Pattern> MONTH_PATTERNS = Arrays.asList(
            Pattern.compile("(january|february|march|april|may|june|july|august|september|october|november|december)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(\\d{1,2})/(\\d{4})"),
            Pattern.compile("(\\d{1,2})-(\\d{4})")
    );

    // ════════════════════════════════════════════════════════════════════
    // GLOBAL ORG CONTEXT — REBUILT: every section has its OWN try/catch.
    // A failure in one data source (e.g. locations, permissions) can no
    // longer wipe out every other section. This was the root cause of
    // "not mentioned in context" answers for locations / permissions /
    // designations even though departments worked.
    // ════════════════════════════════════════════════════════════════════
    private String buildGlobalOrgContext(String userMessage) {
        java.util.Set<Domain> domains = intentClassifier.classify(userMessage);

        ObjectNode context = objectMapper.createObjectNode();
        List<Employee> allEmployees = Collections.emptyList();

        // --- Always include a lightweight summary, even for greetings ---
        try {
            allEmployees = employeeService.getAllEmployees();
            context.put("totalEmployees", allEmployees.size());
        } catch (Exception e) {
            logger.error("Context section failed: employee count", e);
        }

        if (domains.isEmpty()) {
            // e.g. "hii", "thanks" -- summary only, no need to hit every table.
            try {
                String result = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(context);
                logger.info("Global org context built (summary only), length={}", result.length());
                return result;
            } catch (Exception e) {
                return "{}";
            }
        }

        // --- Employees (full directory) ---
        if (domains.contains(Domain.EMPLOYEES)) {
            try {
                context.set("employeeDirectory", objectMapper.valueToTree(allEmployees));
            } catch (Exception e) {
                logger.error("Context section failed: employeeDirectory", e);
                context.put("employeeDirectory", "(unavailable: " + e.getMessage() + ")");
            }
            try {
                context.set("departments", objectMapper.valueToTree(departmentService.getAllDepartmentsWithDesignations()));
            } catch (Exception e) {
                logger.error("Context section failed: departments", e);
                context.put("departments", "(unavailable: " + e.getMessage() + ")");
            }
        }

        // --- Roles & Permissions ---
        if (domains.contains(Domain.ROLE_PERMISSIONS)) {
            try {
                context.set("allRolePermissions", objectMapper.valueToTree(rolePermissionRepository.findAll()));
            } catch (Exception e) {
                logger.error("Context section failed: allRolePermissions", e);
                context.put("allRolePermissions", "(unavailable: " + e.getMessage() + ")");
            }
        }

        // --- Recruitment ---
        if (domains.contains(Domain.RECRUITMENT)) {
            try {
                List<?> activeJobs = jobService.getActiveJobs();
                context.set("activeJobs", objectMapper.valueToTree(activeJobs));
                context.put("totalJobsCount", jobService.getAllJobs().size());
            } catch (Exception e) {
                logger.error("Context section failed: jobs", e);
                context.put("allJobs", "(unavailable: " + e.getMessage() + ")");
            }
            try {
                List<JobApplication> apps = jobApplicationService.getAllApplications();
                List<Map<String, Object>> lightweight = new ArrayList<>();
                for (JobApplication a : apps.subList(0, Math.min(apps.size(), 20))) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("applicantName", a.getFirstName() + " " + a.getLastName());
                    m.put("email", a.getEmail());
                    m.put("status", a.getStatus());
                    m.put("jobTitle", a.getJob() != null ? a.getJob().getTitle() : "N/A");
                    m.put("applicationDate", a.getApplicationDate() != null ? a.getApplicationDate().toString() : null);
                    lightweight.add(m);
                }
                context.set("recentApplications", objectMapper.valueToTree(lightweight));
                context.put("totalApplicationsCount", apps.size());
            } catch (Exception e) {
                logger.error("Context section failed: applications", e);
                context.put("allApplications", "(unavailable: " + e.getMessage() + ")");
            }
            try {
                context.put("totalOffersIssued", offerService.getOfferCount());
                context.put("offersSent", offerService.getSentOfferCount());
                // omit full offerLetters dump from context — counts usually suffice
            } catch (Exception e) {
                logger.error("Context section failed: offers", e);
            }
        }

        // --- Onboarding Documents ---
        if (domains.contains(Domain.DOCUMENTS)) {
            try {
                List<OnboardingDocument> allDocs = documentService.getDocumentRepository().findAll();
                Map<String, Long> statusCounts = allDocs.stream()
                        .collect(Collectors.groupingBy(
                                d -> d.getStatus() != null ? d.getStatus() : "UNKNOWN",
                                Collectors.counting()));
                context.set("documentStatusCounts", objectMapper.valueToTree(statusCounts));
                context.put("totalDocuments", allDocs.size());

                List<Map<String, String>> pendingMandatory = new ArrayList<>();
                for (OnboardingDocument doc : allDocs) {
                    if (doc.isMandatory() && !"VERIFIED".equals(doc.getStatus())) {
                        Map<String, String> m = new HashMap<>();
                        m.put("employeeId", doc.getEmployee() != null ? doc.getEmployee().getEmployeeId() : "N/A");
                        m.put("documentType", doc.getDocumentType());
                        m.put("status", doc.getStatus());
                        pendingMandatory.add(m);
                    }
                }
                context.set("pendingMandatoryDocuments", objectMapper.valueToTree(pendingMandatory));
            } catch (Exception e) {
                logger.error("Context section failed: documents", e);
                context.put("documents", "(unavailable: " + e.getMessage() + ")");
            }
        }

        // --- Leave (org-wide) ---
        if (domains.contains(Domain.LEAVE)) {
            try {
                context.set("allLeaveRequests", objectMapper.valueToTree(leaveRequestRepository.findAll()));
            } catch (Exception e) {
                logger.error("Context section failed: allLeaveRequests", e);
                context.put("allLeaveRequests", "(unavailable: " + e.getMessage() + ")");
            }
            try {
                List<Map<String, Object>> leaveBalances = new ArrayList<>();
                for (Employee emp : allEmployees) {
                    try {
                        Map<String, Object> bal = leaveBalanceService.getEmployeeLeaveBalance(emp.getEmployeeId());
                        bal.put("employeeId", emp.getEmployeeId());
                        bal.put("employeeName", emp.getFirstName() + " " + emp.getLastName());
                        leaveBalances.add(bal);
                    } catch (Exception inner) {
                        logger.warn("Could not load leave balance for {}: {}", emp.getEmployeeId(), inner.getMessage());
                    }
                }
                context.set("leaveBalances", objectMapper.valueToTree(leaveBalances));
            } catch (Exception e) {
                logger.error("Context section failed: leaveBalances", e);
                context.put("leaveBalances", "(unavailable: " + e.getMessage() + ")");
            }
        }

        // --- Announcements ---
        if (domains.contains(Domain.ANNOUNCEMENTS)) {
            try {
                context.set("allAnnouncements", objectMapper.valueToTree(announcementService.getAllAnnouncementsForAdmin()));
            } catch (Exception e) {
                logger.error("Context section failed: announcements", e);
                context.put("allAnnouncements", "(unavailable: " + e.getMessage() + ")");
            }
        }

        // --- Holidays ---
        if (domains.contains(Domain.HOLIDAYS)) {
            try {
                context.set("upcomingHolidays", objectMapper.valueToTree(holidayService.getUpcomingHolidays()));
                context.set("allHolidays", objectMapper.valueToTree(holidayService.getAllHolidays()));
            } catch (Exception e) {
                logger.error("Context section failed: holidays", e);
                context.put("allHolidays", "(unavailable: " + e.getMessage() + ")");
            }
        }

        // --- Locations ---
        if (domains.contains(Domain.LOCATIONS)) {
            try {
                context.set("allLocations", objectMapper.valueToTree(locationService.getAllLocations()));
            } catch (Exception e) {
                logger.error("Context section failed: allLocations", e);
                context.put("allLocations", "(unavailable: " + e.getMessage() + ")");
            }
        }

        try {
            String result = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(context);

            // Safety net: even scoped, one very "chatty" domain (e.g. thousands
            // of leave requests) could still blow the TPM limit. Fall back to
            // summary-only rather than risking another 413.
            if (result.length() > MAX_GLOBAL_CONTEXT_CHARS) {
                logger.warn("Scoped context still too large ({} chars) -- falling back to summary only", result.length());
                ObjectNode fallback = objectMapper.createObjectNode();
                fallback.put("totalEmployees", allEmployees.size());
                fallback.put("note", "Full records omitted -- too many to include safely. " +
                        "Ask a more specific question (e.g. 'pending leave requests for Engineering') to narrow it down.");
                return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(fallback);
            }

            logger.info("Global org context built, length={}", result.length());
            return result;
        } catch (Exception e) {
            logger.error("!!! ERROR serializing global org context: {}", e.getMessage(), e);
            return "{ \"error\": \"Failed to serialize global context: " + e.getMessage() + "\" }";
        }
    }

    // Employee detection patterns
    private static final Pattern EMPLOYEE_ID_PATTERN = Pattern.compile("(EMP\\d{3,})", Pattern.CASE_INSENSITIVE);
    private static final Pattern EMPLOYEE_NAME_PATTERN = Pattern.compile("(employee|emp)\\s+([a-zA-Z]+(?:\\s+[a-zA-Z]+)?)", Pattern.CASE_INSENSITIVE);

    // ════════════════════════════════════════════════════════════════════
    // ACTION PATTERNS — the Super Admin can trigger these directly via chat.
    // Mirrors the pattern already used in ChatService.handleLeaveActionRequest:
    // detect intent with keywords, call the service directly, return a
    // deterministic confirmation. This bypasses the LLM for the actual
    // state change so it's never left to free-form text.
    // ════════════════════════════════════════════════════════════════════
    private static final Pattern LEAVE_ID_PATTERN = Pattern.compile("(LV-\\d{4}-\\d{3,6})", Pattern.CASE_INSENSITIVE);
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    @PostMapping("/send")
    public ResponseEntity<Map<String, Object>> sendMessage(
            @RequestBody Map<String, Object> request,
            HttpSession session) {

        Map<String, Object> response = new HashMap<>();

        String role = (String) session.getAttribute("userRole");
        if (!"SUPER_ADMIN".equals(role)) {
            response.put("success", false);
            response.put("error", "Unauthorized - Super Admin access required");
            return ResponseEntity.status(403).body(response);
        }

        try {
            String employeeId = (String) request.get("employeeId");
            String message = (String) request.get("message");

            if (message == null || message.trim().isEmpty()) {
                response.put("success", false);
                response.put("error", "Message is required");
                return ResponseEntity.badRequest().body(response);
            }

            // ── CHECK FOR ADMIN ACTIONS FIRST (approve/reject leave, create announcement) ──
            String actionResult = handleAdminActionRequest(message, employeeId, session);
            if (actionResult != null) {
                response.put("success", true);
                response.put("reply", actionResult);
                return ResponseEntity.ok(response);
            }

            LocalDate requestedDate = detectDateFromMessage(message);
            String targetEmployeeId = detectEmployeeIdFromMessage(message);
            YearMonth requestedMonth = detectMonthFromMessage(message);

            String context;

            if (targetEmployeeId != null && (message.toLowerCase().contains("month") || requestedMonth != null)) {
                YearMonth monthToUse = (requestedMonth != null) ? requestedMonth : YearMonth.now();
                context = buildContextForEmployeeMonthlySummary(targetEmployeeId, monthToUse);
            }
            else if (targetEmployeeId != null && requestedDate != null) {
                context = buildContextForEmployeeOnDate(targetEmployeeId, requestedDate);
            } else if (targetEmployeeId != null) {
                context = buildContextForEmployeeOnDate(targetEmployeeId, LocalDate.now());
            } else if (requestedDate != null) {
                context = buildContextForDate(requestedDate);
            } else {
                context = buildContextForDate(LocalDate.now());
            }
            String globalContext = buildGlobalOrgContext(message);
            String fullContext = context + "\n\n=== ORGANIZATION-WIDE DATA (ALL MODULES) ===\n\n" + globalContext;

            AdminAIService.AdminAIResult result = adminAIService.processAdminChat(employeeId, message, fullContext, null);
            String reply;

            if (result.isToolCall) {
                String toolResultText = executeAdminTool(result.toolName, result.toolArgs, employeeId, session);
                AdminAIService.AdminAIResult finalResult = adminAIService.continueAfterToolResult(
                        message, fullContext, null, result.toolCallId, result.toolName, result.toolArgs, toolResultText);

                reply = finalResult.isToolCall ? toolResultText : finalResult.textReply;
            } else {
                reply = result.textReply;
            }

            response.put("success", true);
            response.put("reply", reply);

        } catch (Exception e) {
            logger.error("Error in admin AI chat: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("error", e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Detects and executes Super Admin actions directly (no LLM in the loop
     * for the actual state change), returning a deterministic confirmation.
     * Returns null if the message isn't an action request, in which case
     * sendMessage() falls through to the normal read-only chat flow.
     */
    private String handleAdminActionRequest(String message, String adminEmployeeId, HttpSession session) {
        String msg = message.toLowerCase().trim();

        if (message.trim().endsWith("?") || isInformationalQuery(msg)) {
            return null; // a question / informational request, never an action command
        }

        boolean wantsApprove = containsWord(msg, "approve") && containsWord(msg, "leave");
        boolean wantsReject = containsWord(msg, "reject") && containsWord(msg, "leave");
        boolean wantsAnnouncement = (containsWord(msg, "create") || containsWord(msg, "post") || containsWord(msg, "send"))
                && containsWord(msg, "announcement");

        boolean wantsAddLocation = (containsWord(msg, "add") || containsWord(msg, "create")) && containsWord(msg, "location");
        boolean wantsUpdateLocation = (containsWord(msg, "update") || containsWord(msg, "edit")) && containsWord(msg, "location");
        boolean wantsDeleteLocation = (containsWord(msg, "delete") || containsWord(msg, "remove")) && containsWord(msg, "location");

        boolean wantsReport = containsWord(msg, "download") || containsWord(msg, "export")
                || containsWord(msg, "generate")
                || (containsWord(msg, "print") && !containsWord(msg, "printer"));
        if (!wantsApprove && !wantsReject && !wantsAnnouncement
                && !wantsAddLocation && !wantsUpdateLocation && !wantsDeleteLocation
                && !wantsReport) {
            return null;
        }

        try {
            if (wantsApprove) {
                return handleApproveLeave(message, adminEmployeeId);
            } else if (wantsReject) {
                return handleRejectLeave(message, adminEmployeeId);
            } else if (wantsAnnouncement) {
                return handleCreateAnnouncement(message, session);
            } else if (wantsAddLocation) {
                return handleAddLocation(message);
            } else if (wantsUpdateLocation) {
                return handleUpdateLocation(message);
            } else if (wantsDeleteLocation) {
                return handleDeleteLocation(message);
            } else {
                return handleReportRequest(message);
            }
        } catch (Exception e) {
            logger.error("Error handling admin action request: {}", e.getMessage(), e);
            return "⚠️ I ran into a problem processing that action: " + e.getMessage();
        }
    }
    private static final Set<String> INFORMATIONAL_STARTERS = Set.of(
            "what", "who", "which", "how", "give me", "show", "show me", "list",
            "summarize", "summary", "tell me", "can you tell", "could you tell",
            "display", "explain", "describe", "is there", "are there");

    private boolean isInformationalQuery(String lowerTrimmedMsg) {
        for (String starter : INFORMATIONAL_STARTERS) {
            if (lowerTrimmedMsg.startsWith(starter)) return true;
        }
        return false;
    }

    private boolean containsWord(String text, String word) {
        return Pattern.compile("\\b" + Pattern.quote(word) + "\\b", Pattern.CASE_INSENSITIVE)
                .matcher(text).find();
    }
    // ════════════════════════════════════════════════════════════════════
    //  LOCATION CRUD (chat-triggered)
    //  Expected phrasing (Super Admin types naturally, these are examples):
    //   "add location: name=Hyderabad Office, type=Branch, country=India,
    //      state=Telangana, city=Hyderabad, address=123 Main St,
    //      phone=1234567890, email=hyd@menti.com"
    //   "update location: id=3, city=Bengaluru"
    //   "delete location: id=3"  (or "delete location: name=Hyderabad Office")
    // ════════════════════════════════════════════════════════════════════
    private static final Pattern KV_PATTERN = Pattern.compile("(\\w+)\\s*=\\s*([^,]+?)(?=,\\s*\\w+\\s*=|$)");

    private Map<String, String> parseKeyValues(String message) {
        Map<String, String> result = new HashMap<>();
        Matcher m = KV_PATTERN.matcher(message);
        while (m.find()) {
            result.put(m.group(1).trim().toLowerCase(), m.group(2).trim());
        }
        return result;
    }

    private String handleAddLocation(String message) {
        Map<String, String> fields = parseKeyValues(message);
        String name = fields.get("name");
        if (name == null || name.isEmpty()) {
            return "To add a location, tell me at least a name, e.g. " +
                    "\"add location: name=Hyderabad Office, type=Branch, country=India, state=Telangana, city=Hyderabad, address=123 Main St, phone=1234567890, email=hyd@menti.com\"";
        }
        if (locationService.locationExists(name)) {
            return "⚠️ A location named \"" + name + "\" already exists. Use \"update location\" instead.";
        }

        Location location = new Location();
        location.setName(name);
        location.setLocationType(fields.get("type"));
        location.setCountry(fields.get("country"));
        location.setState(fields.get("state"));
        location.setCity(fields.get("city"));
        location.setZipCode(fields.get("zip") != null ? fields.get("zip") : fields.get("zipcode"));
        location.setAddress(fields.get("address"));
        location.setPhone(fields.get("phone"));
        location.setEmail(fields.get("email"));

        Location saved = locationService.saveLocation(location);
        return "✅ Location \"" + saved.getName() + "\" has been added (ID: " + saved.getId() + ").";
    }

    private String handleUpdateLocation(String message) {
        Map<String, String> fields = parseKeyValues(message);
        Long id = parseLongOrNull(fields.get("id"));
        Location location = null;

        if (id != null) {
            location = locationService.getLocationById(id).orElse(null);
        } else if (fields.get("name") != null) {
            location = locationService.getAllLocations().stream()
                    .filter(l -> l.getName().equalsIgnoreCase(fields.get("name")))
                    .findFirst().orElse(null);
        }

        if (location == null) {
            return "I couldn't find that location to update. Please give me the ID, e.g. " +
                    "\"update location: id=3, city=Bengaluru\" — or ask \"show all locations\" first for the ID.";
        }

        if (fields.containsKey("name")) location.setName(fields.get("name"));
        if (fields.containsKey("type")) location.setLocationType(fields.get("type"));
        if (fields.containsKey("country")) location.setCountry(fields.get("country"));
        if (fields.containsKey("state")) location.setState(fields.get("state"));
        if (fields.containsKey("city")) location.setCity(fields.get("city"));
        if (fields.containsKey("zip")) location.setZipCode(fields.get("zip"));
        if (fields.containsKey("zipcode")) location.setZipCode(fields.get("zipcode"));
        if (fields.containsKey("address")) location.setAddress(fields.get("address"));
        if (fields.containsKey("phone")) location.setPhone(fields.get("phone"));
        if (fields.containsKey("email")) location.setEmail(fields.get("email"));
        location.setUpdatedAt(java.time.LocalDateTime.now());

        Location saved = locationService.saveLocation(location);
        return "✅ Location \"" + saved.getName() + "\" (ID: " + saved.getId() + ") has been updated.";
    }

    private String handleDeleteLocation(String message) {
        Map<String, String> fields = parseKeyValues(message);
        Long id = parseLongOrNull(fields.get("id"));
        Location location = null;

        if (id != null) {
            location = locationService.getLocationById(id).orElse(null);
        } else if (fields.get("name") != null) {
            location = locationService.getAllLocations().stream()
                    .filter(l -> l.getName().equalsIgnoreCase(fields.get("name")))
                    .findFirst().orElse(null);
        }

        if (location == null) {
            return "I couldn't find that location to delete. Please give me the ID, e.g. \"delete location: id=3\".";
        }

        locationService.deleteLocation(location.getId());
        return "✅ Location \"" + location.getName() + "\" has been deleted.";
    }

    private Long parseLongOrNull(String s) {
        if (s == null) return null;
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  REPORT / DOWNLOAD GENERATION (chat-triggered)
    //  Example phrasing: "download locations report as excel",
    //  "export employee list as pdf", "print attendance report for July as pdf"
    //  Supported entities right now: locations, employees, departments.
    //  (Extend the switch below as each further module is wired in.)
    // ════════════════════════════════════════════════════════════════════
    private String handleReportRequest(String message) {
        String msg = message.toLowerCase();

        AdminReportService.Format format = AdminReportService.Format.PDF; // sensible default
        if (msg.contains("excel") || msg.contains("xlsx") || msg.contains("spreadsheet")) {
            format = AdminReportService.Format.EXCEL;
        } else if (msg.contains("csv")) {
            format = AdminReportService.Format.CSV;
        } else if (msg.contains("word") || msg.contains("doc")) {
            format = AdminReportService.Format.WORD;
        } else if (msg.contains("pdf") || msg.contains("print")) {
            format = AdminReportService.Format.PDF;
        }

        List<String> headers;
        List<List<String>> rows = new ArrayList<>();
        String title;

        try {
            if (msg.contains("location")) {
                title = "Locations Report";
                headers = Arrays.asList("ID", "Name", "Type", "Country", "State", "City", "Phone", "Email", "Employees");
                for (Location l : locationService.getAllLocations()) {
                    rows.add(Arrays.asList(
                            String.valueOf(l.getId()),
                            nullSafe(l.getName()), nullSafe(l.getLocationType()),
                            nullSafe(l.getCountry()), nullSafe(l.getState()), nullSafe(l.getCity()),
                            nullSafe(l.getPhone()), nullSafe(l.getEmail()),
                            l.getEmployees() != null ? l.getEmployees().toString() : "0"
                    ));
                }
            } else if (msg.contains("department")) {
                title = "Departments Report";
                headers = Arrays.asList("Department");
                Object depts = departmentService.getAllDepartmentsWithDesignations();
                rows.add(Arrays.asList(String.valueOf(depts))); // TODO: replace with real field mapping once
                // DepartmentService's return type/fields are confirmed — see note in guide.
            } else {
                // Default: employee directory
                title = "Employee Directory Report";
                headers = Arrays.asList("Employee ID", "Name", "Department", "Designation", "Employment Type");
                for (Employee e : employeeService.getAllEmployees()) {
                    rows.add(Arrays.asList(
                            nullSafe(e.getEmployeeId()),
                            nullSafe(e.getFirstName()) + " " + nullSafe(e.getLastName()),
                            nullSafe(e.getDepartment()), nullSafe(e.getDesignation()),
                            nullSafe(e.getEmploymentType())
                    ));
                }
            }

            AdminReportService.GeneratedReport report = adminReportService.generate(format, title, headers, rows);
            String token = UUID.randomUUID().toString();
            REPORT_STORE.put(token, report);

            return "✅ Your " + title + " (" + format + ") is ready.\n" +
                    "downloadUrl:/api/chat/admin/download/" + token;
            // NOTE: the "downloadUrl:" line is a simple marker your frontend can
            // parse out of the reply text and turn into a real <a href download>
            // button — see the guide for the two ways to wire this up.

        } catch (Exception e) {
            logger.error("Error generating report: {}", e.getMessage(), e);
            return "⚠️ I couldn't generate that report: " + e.getMessage();
        }
    }



    /**
     * Serves a previously generated report. The Content-Disposition header
     * is what makes the browser download the file (rather than trying to
     * display it) as soon as this URL is opened — e.g. from an <a> tag's
     * href, or window.location = url, or an <a download> click.
     */
    @GetMapping("/download/{token}")
    public ResponseEntity<byte[]> downloadReport(@PathVariable String token) {
        AdminReportService.GeneratedReport report = REPORT_STORE.get(token);
        if (report == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + report.fileName + "\"")
                .contentType(MediaType.parseMediaType(report.contentType))
                .body(report.content);
    }

    private String handleApproveLeave(String message, String adminEmployeeId) {
        Matcher idMatcher = LEAVE_ID_PATTERN.matcher(message);
        if (!idMatcher.find()) {
            return "To approve a leave, please give me the leave ID, e.g. \"approve leave LV-2026-1234\". " +
                    "You can ask me \"show pending leave requests\" first if you don't have the ID handy.";
        }
        String leaveId = idMatcher.group(1).toUpperCase();

        String remarks = null;
        int remarksIdx = message.toLowerCase().indexOf("remarks:");
        if (remarksIdx != -1) {
            remarks = message.substring(remarksIdx + "remarks:".length()).trim();
        }

        Map<String, Object> result = leaveActionService.approveLeave(leaveId, adminEmployeeId, remarks);
        boolean ok = Boolean.TRUE.equals(result.get("success"));
        return ok
                ? "✅ Leave request " + leaveId + " has been approved."
                : "⚠️ Could not approve leave " + leaveId + ": " + result.get("error");
    }

    private String handleRejectLeave(String message, String adminEmployeeId) {
        Matcher idMatcher = LEAVE_ID_PATTERN.matcher(message);
        if (!idMatcher.find()) {
            return "To reject a leave, please give me the leave ID, e.g. \"reject leave LV-2026-1234, reason: understaffed\".";
        }
        String leaveId = idMatcher.group(1).toUpperCase();

        String reason = "No reason provided";
        int reasonIdx = message.toLowerCase().indexOf("reason:");
        if (reasonIdx != -1) {
            reason = message.substring(reasonIdx + "reason:".length()).trim();
        }

        Map<String, Object> result = leaveActionService.rejectLeave(leaveId, adminEmployeeId, reason);
        boolean ok = Boolean.TRUE.equals(result.get("success"));
        return ok
                ? "✅ Leave request " + leaveId + " has been rejected. Reason: " + reason
                : "⚠️ Could not reject leave " + leaveId + ": " + result.get("error");
    }
    private String executeAdminTool(String toolName, JsonNode args, String adminEmployeeId, HttpSession session) {
        try {
            switch (toolName) {
                case "approve_leave": {
                    LeaveRequest lr = resolvePendingLeave(args);
                    if (lr == null) return lastResolveError;
                    Map<String, Object> result = leaveActionService.approveLeave(lr.getId(), adminEmployeeId,
                            args.hasNonNull("remarks") ? args.get("remarks").asText() : null);
                    return describeActionResult(result, "Leave " + lr.getId() + " approved.");
                }
                case "reject_leave": {
                    LeaveRequest lr = resolvePendingLeave(args);
                    if (lr == null) return lastResolveError;
                    Map<String, Object> result = leaveActionService.rejectLeave(lr.getId(), adminEmployeeId,
                            args.hasNonNull("reason") ? args.get("reason").asText() : null);
                    return describeActionResult(result, "Leave " + lr.getId() + " rejected.");
                }
                case "create_announcement": {
                    com.mentis.hrms.dto.AnnouncementDTO dto = new com.mentis.hrms.dto.AnnouncementDTO();
                    dto.setTitle(args.get("title").asText());
                    dto.setDescription(args.get("description").asText());
                    announcementService.createAnnouncement(dto, session);
                    return "Announcement \"" + args.get("title").asText() + "\" has been posted.";
                }
                case "add_location": {
                    Location loc = new Location();
                    loc.setName(args.get("name").asText());
                    if (args.hasNonNull("type")) loc.setLocationType(args.get("type").asText());
                    if (args.hasNonNull("country")) loc.setCountry(args.get("country").asText());
                    if (args.hasNonNull("state")) loc.setState(args.get("state").asText());
                    loc.setCity(args.get("city").asText());
                    if (args.hasNonNull("address")) loc.setAddress(args.get("address").asText());
                    if (args.hasNonNull("phone")) loc.setPhone(args.get("phone").asText());
                    if (args.hasNonNull("email")) loc.setEmail(args.get("email").asText());
                    Location saved = locationService.saveLocation(loc);
                    return "Location \"" + saved.getName() + "\" added (ID: " + saved.getId() + ").";
                }
                case "update_location": {
                    Long id = Long.parseLong(args.get("id").asText());
                    Optional<Location> existing = locationService.getLocationById(id);
                    if (existing.isEmpty()) return "No location found with ID " + id + ".";
                    Location loc = existing.get();
                    String field = args.get("field").asText().toLowerCase();
                    String value = args.get("value").asText();
                    switch (field) {
                        case "name": loc.setName(value); break;
                        case "city": loc.setCity(value); break;
                        case "state": loc.setState(value); break;
                        case "country": loc.setCountry(value); break;
                        case "address": loc.setAddress(value); break;
                        case "phone": loc.setPhone(value); break;
                        case "email": loc.setEmail(value); break;
                        case "type": loc.setLocationType(value); break;
                        default: return "I don't know how to update the field \"" + field + "\".";
                    }
                    locationService.saveLocation(loc);
                    return "Location " + id + " updated: " + field + " = " + value;
                }
                case "delete_location": {
                    Long id = Long.parseLong(args.get("id").asText());
                    locationService.deleteLocation(id);
                    return "Location " + id + " deleted.";
                }
                case "generate_report":
                    return handleReportGeneration(args.get("reportType").asText(), args.get("format").asText());
                default:
                    return "I'm not able to perform that action yet.";
            }


        } catch (Exception e) {
            logger.error("Tool execution failed for {}: {}", toolName, e.getMessage(), e);
            return "That action failed: " + e.getMessage();
        }
    }
    private String handleReportGeneration(String reportType, String format) {
        try {
            AdminReportService.Format fmt = switch (format.toLowerCase()) {
                case "excel", "xlsx" -> AdminReportService.Format.EXCEL;
                case "csv" -> AdminReportService.Format.CSV;
                case "word", "docx" -> AdminReportService.Format.WORD;
                default -> AdminReportService.Format.PDF;
            };

            String title;
            List<String> headers;
            List<List<String>> rows = new ArrayList<>();

            switch (reportType.toLowerCase()) {
                case "locations": {
                    title = "Locations Report";
                    headers = List.of("ID", "Name", "Type", "City", "State", "Country", "Phone", "Email");
                    for (Location loc : locationService.getAllLocations()) {
                        rows.add(List.of(
                                String.valueOf(loc.getId()),
                                nullSafe(loc.getName()),
                                nullSafe(loc.getLocationType()),
                                nullSafe(loc.getCity()),
                                nullSafe(loc.getState()),
                                nullSafe(loc.getCountry()),
                                nullSafe(loc.getPhone()),
                                nullSafe(loc.getEmail())
                        ));
                    }
                    break;
                }
                case "jobs":
                case "recruitment": {
                    title = "Active Job Postings Report";
                    headers = List.of("Title", "Department", "Job Type", "Location", "Experience", "Posted Date", "Deadline", "Status");
                    for (Job job : jobService.getActiveJobs()) {
                        rows.add(List.of(
                                nullSafe(job.getTitle()),
                                nullSafe(job.getDepartment()),
                                nullSafe(job.getJobType()),
                                nullSafe(job.getLocation()),
                                nullSafe(job.getExperienceLevel()),
                                job.getPostedDate() != null ? job.getPostedDate().toString() : "",
                                job.getApplicationDeadline() != null ? job.getApplicationDeadline().toString() : "",
                                Boolean.TRUE.equals(job.getActive()) ? "Active" : "Inactive"
                        ));
                    }
                    break;
                }
                case "employees":
                default: {
                    title = "Employee Directory Report";
                    headers = List.of("Employee ID", "Name", "Department", "Designation", "Status");
                    for (Employee emp : employeeService.getAllEmployees()) {
                        rows.add(List.of(
                                emp.getEmployeeId(),
                                emp.getFirstName() + " " + emp.getLastName(),
                                nullSafe(emp.getDepartment()),
                                nullSafe(emp.getDesignation()),
                                emp.isActive() ? "Active" : "Inactive"
                        ));
                    }
                    break;
                }
            }

            AdminReportService.GeneratedReport report = adminReportService.generate(fmt, title, headers, rows);
            String token = UUID.randomUUID().toString();
            REPORT_STORE.put(token, report);
            return "Your " + title + " (" + format.toUpperCase() + ") is ready.\ndownloadUrl:/api/chat/admin/download/" + token;

        } catch (Exception e) {
            logger.error("Report generation failed: {}", e.getMessage(), e);
            return "Sorry, I couldn't generate that report: " + e.getMessage();
        }
    }

    private String nullSafe(String s) {
        return s != null ? s : "";
    }
    private String describeActionResult(Map<String, Object> result, String successMessage) {
        boolean success = result.get("success") != null && (boolean) result.get("success");
        if (success) return successMessage;
        Object error = result.get("error");
        return "Couldn't complete that: " + (error != null ? error.toString() : "unknown error");
    }

    // Set by resolvePendingLeave when it can't find exactly one match, so the
// caller can surface a helpful message instead of a null pointer.
    private String lastResolveError;

    /**
     * Finds the leave request the model is referring to WITHOUT needing an exact
     * ID — matches on leaveId if given, otherwise on employee name and/or leave
     * type among currently PENDING requests. This is what makes "approve sick
     * leave for Priya" work without any ID typed by the user.
     */
    private LeaveRequest resolvePendingLeave(JsonNode args) {
        if (args.hasNonNull("leaveId") && !args.get("leaveId").asText().isBlank()) {
            Optional<LeaveRequest> byId = leaveRequestRepository.findById(args.get("leaveId").asText());
            if (byId.isPresent()) return byId.get();
            lastResolveError = "I couldn't find a leave request with ID " + args.get("leaveId").asText() + ".";
            return null;
        }

        List<LeaveRequest> pending = leaveRequestRepository.findByStatus("PENDING");

        String nameFilter = args.hasNonNull("employeeName") ? args.get("employeeName").asText().toLowerCase() : null;
        String typeFilter = args.hasNonNull("leaveType") ? args.get("leaveType").asText().toLowerCase() : null;

        List<LeaveRequest> matches = new ArrayList<>();
        for (LeaveRequest lr : pending) {
            boolean nameOk = nameFilter == null ||
                    getEmployeeName(lr.getEmployeeId()).toLowerCase().contains(nameFilter);
            boolean typeOk = typeFilter == null ||
                    (lr.getLeaveType() != null && lr.getLeaveType().toLowerCase().contains(typeFilter));
            if (nameOk && typeOk) matches.add(lr);
        }

        if (matches.size() == 1) {
            return matches.get(0);
        }
        if (matches.isEmpty()) {
            lastResolveError = "I couldn't find a matching pending leave request"
                    + (nameFilter != null ? " for " + nameFilter : "")
                    + (typeFilter != null ? " of type " + typeFilter : "") + ".";
            return null;
        }
        StringBuilder sb = new StringBuilder("There are " + matches.size() +
                " pending leave requests matching that — please specify the leave ID:\n");
        for (LeaveRequest lr : matches) {
            sb.append(" - ").append(getEmployeeName(lr.getEmployeeId()))
                    .append(" | ID: ").append(lr.getId())
                    .append(" | ").append(lr.getLeaveType())
                    .append(" | ").append(lr.getStartDate()).append(" to ").append(lr.getEndDate()).append("\n");
        }
        lastResolveError = sb.toString();
        return null;
    }
    private String handleCreateAnnouncement(String message, HttpSession session) {
        // Expected pattern: create announcement: <title> - <description>
        String body = message;
        int colonIdx = message.toLowerCase().indexOf("announcement");
        if (colonIdx != -1) {
            body = message.substring(colonIdx + "announcement".length()).replaceFirst("^[:\\-\\s]+", "").trim();
        }
        if (body.isEmpty()) {
            return "To create an announcement, tell me the title and details, e.g. " +
                    "\"create announcement: Office closed Friday - The office will be closed this Friday for maintenance.\"";
        }

        String title;
        String description;
        if (body.contains(" - ")) {
            String[] parts = body.split(" - ", 2);
            title = parts[0].trim();
            description = parts[1].trim();
        } else {
            title = body.length() > 60 ? body.substring(0, 57) + "..." : body;
            description = body;
        }

        AnnouncementDTO dto = new AnnouncementDTO();
        dto.setTitle(title);
        dto.setDescription(description);

        try {
            AnnouncementDTO saved = announcementService.createAnnouncement(dto, session);
            return "✅ Announcement \"" + saved.getTitle() + "\" has been posted.";
        } catch (Exception e) {
            return "⚠️ Could not create the announcement: " + e.getMessage();
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  Everything below this line is UNCHANGED from the original file —
    //  date/month detection, per-date and per-employee context builders,
    //  and small helper utilities.
    // ════════════════════════════════════════════════════════════════════

    private LocalDate detectDateFromMessage(String message) {
        String lowerMsg = message.toLowerCase().trim();

        if (lowerMsg.contains("yesterday") || lowerMsg.contains("previous day")) {
            return LocalDate.now().minusDays(1);
        }

        if (lowerMsg.contains("today") || lowerMsg.contains("current day") || lowerMsg.contains("this day")) {
            return LocalDate.now();
        }

        if (lowerMsg.contains("tomorrow") || lowerMsg.contains("next day")) {
            return LocalDate.now().plusDays(1);
        }

        for (Pattern pattern : DATE_PATTERNS) {
            Matcher matcher = pattern.matcher(message);
            if (matcher.find()) {
                try {
                    String dateStr = matcher.group(1);
                    LocalDate parsedDate = parseDate(dateStr);
                    if (parsedDate != null) {
                        return parsedDate;
                    }
                } catch (Exception e) {
                    logger.debug("Could not parse date from pattern: {}", e.getMessage());
                }
            }
        }

        return null;
    }

    private YearMonth detectMonthFromMessage(String message) {
        String lowerMsg = message.toLowerCase().trim();

        if (lowerMsg.contains("this month") || lowerMsg.contains("current month")) {
            return YearMonth.now();
        }

        for (Pattern pattern : MONTH_PATTERNS) {
            Matcher matcher = pattern.matcher(lowerMsg);
            if (matcher.find()) {
                try {
                    String monthStr = matcher.group(1);
                    if (monthStr != null) {
                        try {
                            java.time.Month month = java.time.Month.valueOf(monthStr.toUpperCase());
                            int year = YearMonth.now().getYear();
                            return YearMonth.of(year, month.getValue());
                        } catch (IllegalArgumentException e) {
                            try {
                                Map<String, Integer> shortMonthMap = new HashMap<>();
                                shortMonthMap.put("jan", 1);
                                shortMonthMap.put("feb", 2);
                                shortMonthMap.put("mar", 3);
                                shortMonthMap.put("apr", 4);
                                shortMonthMap.put("may", 5);
                                shortMonthMap.put("jun", 6);
                                shortMonthMap.put("jul", 7);
                                shortMonthMap.put("aug", 8);
                                shortMonthMap.put("sep", 9);
                                shortMonthMap.put("oct", 10);
                                shortMonthMap.put("nov", 11);
                                shortMonthMap.put("dec", 12);

                                String shortMonth = monthStr.toLowerCase().substring(0, 3);
                                if (shortMonthMap.containsKey(shortMonth)) {
                                    int month = shortMonthMap.get(shortMonth);
                                    int year = YearMonth.now().getYear();
                                    return YearMonth.of(year, month);
                                }
                            } catch (Exception e2) {
                                try {
                                    if (matcher.groupCount() >= 2) {
                                        int month = Integer.parseInt(matcher.group(1));
                                        int year = Integer.parseInt(matcher.group(2));
                                        if (month >= 1 && month <= 12 && year >= 2000 && year <= 2100) {
                                            return YearMonth.of(year, month);
                                        }
                                    }
                                } catch (Exception e3) {
                                    // Ignore
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.debug("Could not parse month: {}", e.getMessage());
                }
            }
        }

        return null;
    }

    private LocalDate parseDate(String dateStr) {
        try {
            return LocalDate.parse(dateStr);
        } catch (Exception e) {
            // Try other formats
        }

        try {
            String[] parts = dateStr.split("[/-]");
            if (parts.length == 3) {
                int month = Integer.parseInt(parts[0]);
                int day = Integer.parseInt(parts[1]);
                int year = Integer.parseInt(parts[2]);
                if (year > 0 && month > 0 && day > 0) {
                    return LocalDate.of(year, month, day);
                }
            }
        } catch (Exception e) {
            // Try other formats
        }

        return null;
    }

    private String detectEmployeeIdFromMessage(String message) {
        Matcher empMatcher = EMPLOYEE_ID_PATTERN.matcher(message);
        if (empMatcher.find()) {
            return empMatcher.group(1).toUpperCase();
        }

        Matcher nameMatcher = EMPLOYEE_NAME_PATTERN.matcher(message.toLowerCase());
        if (nameMatcher.find()) {
            String searchName = nameMatcher.group(2).trim();
            List<Employee> allEmployees = employeeService.getAllEmployees();
            for (Employee emp : allEmployees) {
                String fullName = (emp.getFirstName() + " " + emp.getLastName()).toLowerCase();
                if (fullName.contains(searchName) || searchName.contains(fullName)) {
                    return emp.getEmployeeId();
                }
            }
        }

        return null;
    }

    private String buildContextForDate(LocalDate date) {
        try {
            if (date == null) {
                date = LocalDate.now();
            }

            List<Map<String, Object>> attendanceData;
            if (date.equals(LocalDate.now())) {
                attendanceData = attendanceService.getTodayAllAttendances();
            } else {
                attendanceData = getAttendanceForDate(date);
            }

            List<Employee> allEmployees = employeeService.getAllEmployees();
            List<LeaveRequest> leavesForDate = getLeavesForDate(date);

            ObjectNode context = objectMapper.createObjectNode();

            context.put("date", date.toString());
            context.put("dayOfWeek", date.getDayOfWeek().toString());
            context.put("isToday", date.equals(LocalDate.now()));
            context.put("isYesterday", date.equals(LocalDate.now().minusDays(1)));

            context.put("totalEmployees", allEmployees.size());
            context.put("totalPresent", countPresent(attendanceData));
            context.put("totalAbsent", countAbsentForDate(allEmployees, attendanceData, leavesForDate));
            context.put("totalOnLeave", leavesForDate.size());
            context.put("totalCheckedOut", countCheckedOut(attendanceData));

            List<Map<String, String>> employeeStatus = new ArrayList<>();
            for (Employee emp : allEmployees) {
                Map<String, String> statusMap = new HashMap<>();
                statusMap.put("name", emp.getFirstName() + " " + emp.getLastName());
                statusMap.put("employeeId", emp.getEmployeeId());
                statusMap.put("department", emp.getDepartment() != null ? emp.getDepartment() : "N/A");
                statusMap.put("status", getEmployeeStatusForDate(emp.getEmployeeId(), attendanceData, leavesForDate));
                employeeStatus.add(statusMap);
            }
            context.set("employeeStatus", objectMapper.valueToTree(employeeStatus));

            List<Map<String, String>> leaveList = new ArrayList<>();
            for (LeaveRequest lr : leavesForDate) {
                Map<String, String> leaveMap = new HashMap<>();
                leaveMap.put("employeeId", lr.getEmployeeId());
                leaveMap.put("employeeName", getEmployeeName(lr.getEmployeeId()));
                leaveMap.put("leaveType", lr.getLeaveType());
                leaveMap.put("status", lr.getStatus());
                leaveList.add(leaveMap);
            }
            context.set("leaves", objectMapper.valueToTree(leaveList));

            List<Map<String, String>> absentees = new ArrayList<>();
            for (Employee emp : allEmployees) {
                boolean hasAttendance = attendanceData.stream()
                        .anyMatch(a -> emp.getEmployeeId().equals(a.get("employeeId")));
                boolean onLeave = leavesForDate.stream()
                        .anyMatch(l -> l.getEmployeeId().equals(emp.getEmployeeId()));
                if (!hasAttendance && !onLeave) {
                    Map<String, String> absentMap = new HashMap<>();
                    absentMap.put("name", emp.getFirstName() + " " + emp.getLastName());
                    absentMap.put("employeeId", emp.getEmployeeId());
                    absentMap.put("department", emp.getDepartment() != null ? emp.getDepartment() : "N/A");
                    absentees.add(absentMap);
                }
            }
            context.set("absentees", objectMapper.valueToTree(absentees));

            int present = countPresent(attendanceData);
            int total = allEmployees.size();
            double percentage = total > 0 ? (present * 100.0 / total) : 0;
            context.put("attendancePercentage", Math.round(percentage * 100.0) / 100.0);

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(context);

        } catch (Exception e) {
            logger.error("Error building context for date {}: {}", date, e.getMessage());
            return "{}";
        }
    }

    private String buildContextForEmployeeOnDate(String employeeId, LocalDate date) {
        try {
            Optional<Employee> empOpt = employeeService.getEmployeeByEmployeeId(employeeId);
            if (empOpt.isEmpty()) {
                ObjectNode errorContext = objectMapper.createObjectNode();
                errorContext.put("error", "Employee not found: " + employeeId);
                errorContext.put("employeeId", employeeId);
                return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(errorContext);
            }

            Employee employee = empOpt.get();
            Map<String, Object> attendanceData = attendanceService.getAttendanceByDateAndEmployee(date, employeeId);
            List<LeaveRequest> leavesForDate = getLeavesForDate(date);
            boolean onLeave = leavesForDate.stream().anyMatch(l -> l.getEmployeeId().equals(employeeId));

            ObjectNode context = objectMapper.createObjectNode();

            context.put("employeeId", employee.getEmployeeId());
            context.put("employeeName", employee.getFirstName() + " " + employee.getLastName());
            context.put("department", employee.getDepartment() != null ? employee.getDepartment() : "N/A");
            context.put("designation", employee.getDesignation() != null ? employee.getDesignation() : "N/A");

            context.put("date", date.toString());
            context.put("dayOfWeek", date.getDayOfWeek().toString());
            context.put("isToday", date.equals(LocalDate.now()));
            context.put("isYesterday", date.equals(LocalDate.now().minusDays(1)));

            if (onLeave) {
                context.put("status", "On Leave");
                context.put("checkInTime", "N/A");
                context.put("checkOutTime", "N/A");
                context.put("checkInFormatted", "--:--");
                context.put("checkOutFormatted", "--:--");
                context.put("workingHours", "--:--");
                context.put("isCheckedOut", false);
                context.put("hasCheckedOut", false);
                context.put("displayStatus", "On Leave");
                context.put("remarks", "Employee is on approved leave");
            } else if (attendanceData != null) {
                String status = safeGetString(attendanceData, "status", "ABSENT");
                String checkIn = safeGetString(attendanceData, "checkInFormatted", "--:--");
                String checkOut = safeGetString(attendanceData, "checkOutFormatted", "--:--");
                String workingHours = safeGetString(attendanceData, "workingHours", "--:--");
                boolean isCheckedOut = safeGetBoolean(attendanceData, "isCheckedOut", false);

                Object checkInTime = attendanceData.get("checkInTime");
                Object checkOutTime = attendanceData.get("checkOutTime");

                context.put("status", status);
                context.put("checkInTime", checkInTime != null ? checkInTime.toString() : "N/A");
                context.put("checkOutTime", checkOutTime != null ? checkOutTime.toString() : "N/A");
                context.put("checkInFormatted", checkIn);
                context.put("checkOutFormatted", checkOut);
                context.put("workingHours", workingHours);
                context.put("isCheckedOut", isCheckedOut);
                context.put("hasCheckedOut", isCheckedOut);

                if (isCheckedOut) {
                    context.put("displayStatus", "Checked Out");
                } else if ("ACTIVE".equals(status) || "CHECKED_IN".equals(status)) {
                    context.put("displayStatus", "Present");
                } else if ("BREAK".equals(status)) {
                    context.put("displayStatus", "On Break");
                } else {
                    context.put("displayStatus", status != null ? status : "Absent");
                }

                String remarks = safeGetString(attendanceData, "remarks", "Normal day");
                context.put("remarks", remarks);
            } else {
                context.put("status", "Absent");
                context.put("checkInTime", "N/A");
                context.put("checkOutTime", "N/A");
                context.put("checkInFormatted", "--:--");
                context.put("checkOutFormatted", "--:--");
                context.put("workingHours", "--:--");
                context.put("isCheckedOut", false);
                context.put("hasCheckedOut", false);
                context.put("displayStatus", "Absent");
                context.put("remarks", "No attendance record found");
            }

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(context);

        } catch (Exception e) {
            logger.error("Error building employee context for {} on {}: {}", employeeId, date, e.getMessage());
            return "{}";
        }
    }

    private String buildContextForEmployeeMonthlySummary(String employeeId, YearMonth yearMonth) {
        try {
            Optional<Employee> empOpt = employeeService.getEmployeeByEmployeeId(employeeId);
            if (empOpt.isEmpty()) {
                ObjectNode errorContext = objectMapper.createObjectNode();
                errorContext.put("error", "Employee not found: " + employeeId);
                errorContext.put("employeeId", employeeId);
                return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(errorContext);
            }

            Employee employee = empOpt.get();

            Map<String, Object> summary = attendanceService.getMonthlySummaryForEmployee(
                    employeeId,
                    yearMonth.getYear(),
                    yearMonth.getMonthValue()
            );

            Map<String, String> dailyAttendance = attendanceService.getEmployeeMonthlyAttendance(
                    employeeId,
                    yearMonth.getYear(),
                    yearMonth.getMonthValue()
            );

            ObjectNode context = objectMapper.createObjectNode();

            context.put("employeeId", employee.getEmployeeId());
            context.put("employeeName", employee.getFirstName() + " " + employee.getLastName());
            context.put("department", employee.getDepartment() != null ? employee.getDepartment() : "N/A");
            context.put("designation", employee.getDesignation() != null ? employee.getDesignation() : "N/A");

            context.put("month", yearMonth.getMonth().toString());
            context.put("year", yearMonth.getYear());
            context.put("monthYear", yearMonth.getMonth() + " " + yearMonth.getYear());

            int presentDays = (int) summary.getOrDefault("presentDays", 0);
            int absentDays = (int) summary.getOrDefault("absentDays", 0);
            int leaveDays = (int) summary.getOrDefault("leaveDays", 0);
            int lateDays = (int) summary.getOrDefault("lateDays", 0);
            int earlyDepartures = (int) summary.getOrDefault("earlyDepartures", 0);
            int totalDays = (int) summary.getOrDefault("totalDays", 0);

            context.put("presentDays", presentDays);
            context.put("absentDays", absentDays);
            context.put("leaveDays", leaveDays);
            context.put("lateDays", lateDays);
            context.put("earlyDepartures", earlyDepartures);
            context.put("totalWorkingDays", totalDays);
            context.put("totalWorkingHours", (String) summary.getOrDefault("totalWorkingHours", "0h 00m"));
            context.put("overtime", (String) summary.getOrDefault("overtime", "0h 00m"));

            Object attendancePercentageObj = summary.getOrDefault("attendancePercentage", 0);
            if (attendancePercentageObj instanceof Integer) {
                context.put("attendancePercentage", (Integer) attendancePercentageObj);
            } else if (attendancePercentageObj instanceof Double) {
                context.put("attendancePercentage", (Double) attendancePercentageObj);
            } else if (attendancePercentageObj instanceof Long) {
                context.put("attendancePercentage", (Long) attendancePercentageObj);
            } else {
                context.put("attendancePercentage", attendancePercentageObj.toString());
            }

            List<Map<String, String>> dailyBreakdown = new ArrayList<>();
            if (dailyAttendance != null && !dailyAttendance.isEmpty()) {
                LocalDate startDate = yearMonth.atDay(1);
                LocalDate endDate = yearMonth.atEndOfMonth();
                LocalDate current = startDate;

                DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy");

                while (!current.isAfter(endDate)) {
                    if (current.getDayOfWeek().getValue() != 7) {
                        Map<String, String> dayMap = new HashMap<>();
                        dayMap.put("date", current.format(dateFormatter));
                        dayMap.put("day", current.getDayOfWeek().toString());
                        String status = dailyAttendance.getOrDefault(current.toString(), "absent");

                        String displayStatus;
                        String statusCode;
                        switch (status.toLowerCase()) {
                            case "present":
                                displayStatus = "✅ Present";
                                statusCode = "present";
                                break;
                            case "absent":
                                displayStatus = "❌ Absent";
                                statusCode = "absent";
                                break;
                            case "leave":
                                displayStatus = "📋 On Leave";
                                statusCode = "leave";
                                break;
                            case "late":
                                displayStatus = "⚠️ Late";
                                statusCode = "late";
                                break;
                            default:
                                displayStatus = "❓ Unknown";
                                statusCode = "unknown";
                        }
                        dayMap.put("status", displayStatus);
                        dayMap.put("statusCode", statusCode);
                        dailyBreakdown.add(dayMap);
                    }
                    current = current.plusDays(1);
                }
            }
            context.put("dailyBreakdown", objectMapper.valueToTree(dailyBreakdown));

            StringBuilder summaryText = new StringBuilder();
            summaryText.append("📊 MONTHLY ATTENDANCE SUMMARY FOR ").append(employee.getFirstName())
                    .append(" ").append(employee.getLastName())
                    .append(" (").append(employee.getEmployeeId()).append(")\n");
            summaryText.append("📅 Month: ").append(yearMonth.getMonth()).append(" ").append(yearMonth.getYear()).append("\n");
            summaryText.append("🏢 Department: ").append(employee.getDepartment() != null ? employee.getDepartment() : "N/A").append("\n");
            summaryText.append("📋 Designation: ").append(employee.getDesignation() != null ? employee.getDesignation() : "N/A").append("\n");
            summaryText.append("\n📈 SUMMARY STATISTICS:\n");
            summaryText.append("• Total Working Days: ").append(totalDays).append("\n");
            summaryText.append("• ✅ Present: ").append(presentDays).append(" days\n");
            summaryText.append("• ❌ Absent: ").append(absentDays).append(" days\n");
            summaryText.append("• 📋 On Leave: ").append(leaveDays).append(" days\n");
            summaryText.append("• ⚠️ Late Arrivals: ").append(lateDays).append(" days\n");
            summaryText.append("• 🏃 Early Departures: ").append(earlyDepartures).append(" days\n");
            summaryText.append("• ⏱️ Total Working Hours: ").append(summary.getOrDefault("totalWorkingHours", "0h 00m")).append("\n");
            summaryText.append("• 📊 Attendance Percentage: ").append(summary.getOrDefault("attendancePercentage", 0)).append("%\n");
            summaryText.append("• 🕐 Overtime: ").append(summary.getOrDefault("overtime", "0h 00m")).append("\n");

            if (!dailyBreakdown.isEmpty()) {
                summaryText.append("\n📅 DAILY BREAKDOWN:\n");
                for (Map<String, String> day : dailyBreakdown) {
                    summaryText.append("• ").append(day.get("date")).append(" - ").append(day.get("status")).append("\n");
                }
            }

            context.put("summaryText", summaryText.toString());

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(context);

        } catch (Exception e) {
            logger.error("Error building monthly summary for {}: {}", employeeId, e.getMessage(), e);
            ObjectNode errorContext = objectMapper.createObjectNode();
            errorContext.put("error", "Error generating monthly summary: " + e.getMessage());
            try {
                return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(errorContext);
            } catch (Exception ex) {
                return "{\"error\": \"Failed to generate monthly summary\"}";
            }
        }
    }

    private List<Map<String, Object>> getAttendanceForDate(LocalDate date) {
        try {
            List<Map<String, Object>> result = new ArrayList<>();
            List<Employee> allEmployees = employeeService.getAllEmployees();

            for (Employee emp : allEmployees) {
                Map<String, Object> attData = attendanceService.getAttendanceByDateAndEmployee(date, emp.getEmployeeId());
                if (attData != null) {
                    if (!attData.containsKey("employeeName")) {
                        attData.put("employeeName", emp.getFirstName() + " " + emp.getLastName());
                        attData.put("department", emp.getDepartment() != null ? emp.getDepartment() : "N/A");
                    }
                    result.add(attData);
                }
            }

            return result;

        } catch (Exception e) {
            logger.error("Error getting attendance for date {}: {}", date, e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<LeaveRequest> getLeavesForDate(LocalDate date) {
        try {
            List<LeaveRequest> allLeaves = leaveRequestRepository.findAll();
            return allLeaves.stream()
                    .filter(lr -> {
                        LocalDate start = lr.getStartDate();
                        LocalDate end = lr.getEndDate();
                        boolean isOnDate = !date.isBefore(start) && !date.isAfter(end);
                        boolean isPendingOrApproved = "PENDING".equals(lr.getStatus()) || "APPROVED".equals(lr.getStatus());
                        return isOnDate && isPendingOrApproved;
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("Error getting leaves for date {}: {}", date, e.getMessage());
            return new ArrayList<>();
        }
    }

    private int countPresent(List<Map<String, Object>> attendances) {
        return (int) attendances.stream()
                .filter(a -> {
                    String status = (String) a.get("status");
                    return "ACTIVE".equals(status) || "BREAK".equals(status) || "CHECKED_IN".equals(status);
                })
                .count();
    }

    private int countAbsentForDate(List<Employee> employees, List<Map<String, Object>> attendances, List<LeaveRequest> leaves) {
        Set<String> presentOrLeaveIds = new HashSet<>();
        for (Map<String, Object> att : attendances) {
            presentOrLeaveIds.add((String) att.get("employeeId"));
        }
        for (LeaveRequest lr : leaves) {
            presentOrLeaveIds.add(lr.getEmployeeId());
        }
        return (int) employees.stream()
                .filter(e -> !presentOrLeaveIds.contains(e.getEmployeeId()))
                .count();
    }

    private int countCheckedOut(List<Map<String, Object>> attendances) {
        return (int) attendances.stream()
                .filter(a -> a.get("checkOutTime") != null)
                .count();
    }

    private String getEmployeeStatusForDate(String employeeId, List<Map<String, Object>> attendances, List<LeaveRequest> leaves) {
        boolean onLeave = leaves.stream().anyMatch(l -> l.getEmployeeId().equals(employeeId));
        if (onLeave) return "On Leave";

        for (Map<String, Object> att : attendances) {
            if (employeeId.equals(att.get("employeeId"))) {
                if (att.get("checkOutTime") != null) return "Checked Out";
                String status = (String) att.get("status");
                if ("ACTIVE".equals(status)) return "Present";
                if ("BREAK".equals(status)) return "On Break";
                if ("CHECKED_IN".equals(status)) return "Present";
                return status != null ? status : "Active";
            }
        }
        return "Absent";
    }

    private String getEmployeeName(String employeeId) {
        Optional<Employee> emp = employeeService.getEmployeeByEmployeeId(employeeId);
        return emp.map(e -> e.getFirstName() + " " + e.getLastName()).orElse(employeeId);
    }

    private String safeGetString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        return value.toString();
    }

    private boolean safeGetBoolean(Map<String, Object> map, String key, boolean defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return defaultValue;
    }
}
