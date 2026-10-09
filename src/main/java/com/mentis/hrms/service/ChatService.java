package com.mentis.hrms.service;

import com.mentis.hrms.dto.ChatRequestDto;
import com.mentis.hrms.dto.ChatResponseDto;
import com.mentis.hrms.model.Attendance;
import com.mentis.hrms.model.ChatMessage;
import com.mentis.hrms.model.ChatSession;
import com.mentis.hrms.model.Holiday;
import com.mentis.hrms.model.Notification;
import com.mentis.hrms.model.OnboardingDocument;
import com.mentis.hrms.model.Employee;
import com.mentis.hrms.model.LeaveRequest;
import com.mentis.hrms.dto.AnnouncementDTO;
import com.mentis.hrms.repository.ChatMessageRepository;
import com.mentis.hrms.repository.ChatSessionRepository;
import com.mentis.hrms.repository.LeaveRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Transactional
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final int MAX_HISTORY_PAIRS = 8;

    // ── Core chat repositories ──────────────────────────────────────────────
    @Autowired
    private ChatSessionRepository sessionRepository;

    @Autowired
    private ChatMessageRepository messageRepository;

    @Autowired
    private OpenRouterService openRouterService;

    // ── Data services ───────────────────────────────────────────────────────
    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private LeaveBalanceService leaveBalanceService;

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private HolidayService holidayService;

    @Autowired
    @Lazy
    private DocumentService documentService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private AnnouncementService announcementService;


    @Autowired
    private LeaveActionService leaveActionService;

    // ════════════════════════════════════════════════════════════════════════
    //  PUBLIC API
    // ════════════════════════════════════════════════════════════════════════

    public ChatResponseDto sendMessage(ChatRequestDto request) {
        ChatResponseDto response = new ChatResponseDto();
        try {
            log.info("📨 Processing chat message from employee: {}", request.getEmployeeId());
            log.debug("Message: {}", request.getMessage());

            // ── CHECK FOR SPECIFIC ATTENDANCE REQUESTS FIRST ──
            String specificResponse = handleSpecificAttendanceRequest(request.getMessage(), request.getEmployeeId());
            if (specificResponse != null) {
                log.info("✅ Specific attendance response sent");
                response.setSuccess(true);
                response.setReply(specificResponse);
                response.setSessionId(-1L);
                return response;
            }

            // ── CHECK FOR ATTENDANCE SUMMARY ──
            String directSummary = handleAttendanceSummaryRequest(request.getMessage(), request.getEmployeeId());
            if (directSummary != null) {
                log.info("✅ Direct attendance summary response sent");
                response.setSuccess(true);
                response.setReply(directSummary);
                response.setSessionId(-1L);
                return response;
            }
// ── CHECK FOR LEAVE ACTIONS (apply / withdraw / cancel) ──
            String leaveActionResponse = handleLeaveActionRequest(request.getMessage(), request.getEmployeeId());
            if (leaveActionResponse != null) {
                log.info("✅ Leave action response sent");
                response.setSuccess(true);
                response.setReply(leaveActionResponse);
                response.setSessionId(-1L);
                return response;
            }
            // ── BUILD CONTEXT ──
            String employeeContext = buildRichEmployeeContext(request.getEmployeeId());
            log.debug("Built rich context for employee: {}", request.getEmployeeId());

            // ── PROCESS CHAT ──
            if (request.isTemporary()) {
                List<OpenRouterService.MessagePair> history = buildHistoryFromClient(request.getHistory());
                String reply = openRouterService.chat(request.getMessage(), history, employeeContext);
                response.setSuccess(true);
                response.setReply(reply);
                response.setSessionId(-1L);
            } else {
                ChatSession session = getOrCreateSession(request);
                List<ChatMessage> existing = messageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId());
                List<OpenRouterService.MessagePair> history = buildHistoryFromDB(existing);
                String reply = openRouterService.chat(request.getMessage(), history, employeeContext);
                saveMessage(session, "user", request.getMessage());
                saveMessage(session, "assistant", reply);
                session.setUpdatedAt(LocalDateTime.now());
                if (session.getSessionName() == null || session.getSessionName().isEmpty()) {
                    session.setSessionName(generateSessionName(request.getMessage()));
                }
                sessionRepository.save(session);
                response.setSuccess(true);
                response.setReply(reply);
                response.setSessionId(session.getId());
                response.setSessionName(session.getSessionName());
            }

        } catch (Exception e) {
            log.error("❌ Chat error for employee {}: {}", request.getEmployeeId(), e.getMessage(), e);
            response.setSuccess(false);
            response.setError("Failed to process your request. Please try again.");
        }
        return response;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  CONTEXT BUILDER  – every piece of data Menti needs
    // ════════════════════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private String buildRichEmployeeContext(String employeeId) {
        StringBuilder sb = new StringBuilder();
        LocalDate today = LocalDate.now();
        int year = today.getYear();
        int month = today.getMonthValue();

        Employee emp = null;

        // ── 1. CLEAR SECTION MARKERS ──────────────────────────────────────────
        sb.append("═══════════════════════════════════════════════════════════════\n");
        sb.append("EMPLOYEE DATA — USE THIS TO ANSWER QUESTIONS DIRECTLY\n");
        sb.append("═══════════════════════════════════════════════════════════════\n\n");

        // ── 2. PROFILE ────────────────────────────────────────────────────────
        sb.append("📌 PROFILE\n");
        sb.append("─────────\n");
        sb.append("Employee ID: ").append(employeeId).append("\n");
        try {
            Optional<Employee> empOpt = employeeService.getEmployeeByEmployeeId(employeeId);
            if (empOpt.isPresent()) {
                emp = empOpt.get();
                sb.append("Name: ").append(nullSafe(emp.getFirstName())).append(" ")
                        .append(nullSafe(emp.getLastName())).append("\n");
                sb.append("Department: ").append(nullSafe(emp.getDepartment(), "N/A")).append("\n");
                sb.append("Designation: ").append(nullSafe(emp.getDesignation(), "N/A")).append("\n");
                sb.append("Employment Type: ").append(nullSafe(emp.getEmploymentType(), "N/A")).append("\n");
                sb.append("Work Location: ").append(nullSafe(emp.getWorkLocation(), "N/A")).append("\n");
                sb.append("Join Date: ")
                        .append(emp.getStartDate() != null ? emp.getStartDate() :
                                (emp.getCreatedDate() != null ? emp.getCreatedDate().toLocalDate() : "N/A"))
                        .append("\n");
                if (emp.getDocumentDeadline() != null) {
                    sb.append("Document Deadline: ")
                            .append(emp.getDocumentDeadline().format(FORMATTER)).append("\n");
                }
            }
        } catch (Exception e) {
            sb.append("(Profile data temporarily unavailable)\n");
        }
        sb.append("\n");

        // ── 3. TODAY'S ATTENDANCE ────────────────────────────────────────────
        sb.append("📅 TODAY'S ATTENDANCE (").append(today).append(")\n");
        sb.append("─────────────────────────────────────────────────────────\n");
        try {
            List<Attendance> todayAtt = attendanceService.getTodayAttendanceForEmployee(employeeId);
            if (!todayAtt.isEmpty()) {
                Attendance latest = todayAtt.get(0);
                sb.append("Status: ").append(nullSafe(latest.getStatus(), "Not checked in")).append("\n");
                sb.append("Check-in: ")
                        .append(latest.getCheckInTime() != null
                                ? latest.getCheckInTime().format(DateTimeFormatter.ofPattern("hh:mm a"))
                                : "Not checked in yet")
                        .append("\n");
                sb.append("Check-out: ")
                        .append(latest.getCheckOutTime() != null
                                ? latest.getCheckOutTime().format(DateTimeFormatter.ofPattern("hh:mm a"))
                                : "Not checked out yet")
                        .append("\n");
                sb.append("Working hours: ")
                        .append(latest.getTotalWorkingHours() != null
                                ? latest.getTotalWorkingHours() : "Calculating...")
                        .append("\n");
            } else {
                sb.append("Status: Not checked in today\n");
            }
        } catch (Exception e) {
            sb.append("(Today's attendance data temporarily unavailable)\n");
        }
        sb.append("\n");

        // ── 4. MONTHLY ATTENDANCE SUMMARY ────────────────────────────────────
        sb.append("📊 MONTHLY ATTENDANCE SUMMARY (").append(month).append("/").append(year).append(")\n");
        sb.append("─────────────────────────────────────────────────────────\n");
        try {
            Map<String, Object> summary = attendanceService.getMonthlySummaryForEmployee(employeeId, year, month);
            sb.append("Working days counted: ").append(summary.getOrDefault("totalDays", "N/A")).append("\n");
            sb.append("Present days: ").append(summary.getOrDefault("presentDays", "N/A")).append("\n");
            sb.append("Absent days: ").append(summary.getOrDefault("absentDays", "N/A")).append("\n");
            sb.append("Leave days: ").append(summary.getOrDefault("leaveDays", "N/A")).append("\n");
            sb.append("Attendance percentage: ").append(summary.getOrDefault("attendancePercentage", "N/A")).append("%\n");
            sb.append("Total working hours: ").append(summary.getOrDefault("totalWorkingHours", "N/A")).append("\n");
            sb.append("Overtime: ").append(summary.getOrDefault("overtime", "0h 00m")).append("\n");
            sb.append("Late arrivals: ").append(summary.getOrDefault("lateDays", "N/A")).append("\n");
            sb.append("Early departures: ").append(summary.getOrDefault("earlyDepartures", "N/A")).append("\n");
        } catch (Exception e) {
            sb.append("(Monthly summary temporarily unavailable)\n");
        }
        sb.append("\n");

        // ── 5. RECENT ATTENDANCE LOG ─────────────────────────────────────────
        sb.append("📋 RECENT ATTENDANCE LOG (last 7 days)\n");
        sb.append("─────────────────────────────────────────────────────────\n");
        sb.append("DATE       | DAY     | CHECK-IN | CHECK-OUT | HOURS  | STATUS\n");
        sb.append("───────────|─────────|──────────|───────────|────────|────────\n");
        try {
            List<Map<String, Object>> recent = attendanceService.getRecentAttendanceForEmployee(employeeId, 7);
            if (recent.isEmpty()) {
                sb.append("No attendance records in the last 7 days.\n");
            } else {
                for (Map<String, Object> day : recent) {
                    String date = day.getOrDefault("dateFormatted", "").toString();
                    String dayName = day.getOrDefault("day", "").toString();
                    String checkIn = day.getOrDefault("checkInFormatted", "--:--").toString();
                    String checkOut = day.getOrDefault("checkOutFormatted", "--:--").toString();
                    String hours = day.getOrDefault("currentWorkingTime", "0h 00m").toString();
                    String status = day.getOrDefault("statusText", day.getOrDefault("status", "")).toString();
                    // Truncate to fit table
                    if (date.length() > 10) date = date.substring(0, 10);
                    if (dayName.length() > 8) dayName = dayName.substring(0, 8);
                    sb.append(String.format("%-10s | %-8s | %-8s | %-9s | %-6s | %-8s\n",
                            date, dayName, checkIn, checkOut, hours, status));
                }
            }
        } catch (Exception e) {
            sb.append("(Recent attendance log temporarily unavailable)\n");
        }
        sb.append("\n");

        // ── 6. LEAVE BALANCE ──────────────────────────────────────────────────
        sb.append("🏖️ LEAVE BALANCE\n");
        sb.append("─────────────────────────────────────────────────────────\n");
        try {
            Map<String, Object> leaveData = leaveBalanceService.getEmployeeLeaveBalance(employeeId);
            Object balancesObj = leaveData.get("balances");
            if (balancesObj instanceof Map) {
                Map<String, Object> balances = (Map<String, Object>) balancesObj;
                appendLeaveLine(sb, "Sick", balances.get("sick"));
                appendLeaveLine(sb, "Casual", balances.get("casual"));
                appendLeaveLine(sb, "Earned", balances.get("earned"));
                sb.append("Total used: ").append(leaveData.getOrDefault("usedLeaves", "N/A")).append("\n");
                sb.append("Total available: ").append(leaveData.getOrDefault("availableLeaves", "N/A")).append("\n");
            }
        } catch (Exception e) {
            sb.append("(Leave balance temporarily unavailable)\n");
        }
        sb.append("\n");

        // ── 7. PENDING LEAVE REQUESTS ────────────────────────────────────────
        sb.append("⏳ PENDING LEAVE REQUESTS\n");
        sb.append("─────────────────────────────────────────────────────────\n");
        try {
            List<LeaveRequest> leaves = leaveRequestRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId);
            List<LeaveRequest> pending = leaves.stream()
                    .filter(l -> "PENDING".equalsIgnoreCase(l.getStatus()))
                    .limit(5).collect(Collectors.toList());
            if (pending.isEmpty()) {
                sb.append("No pending leave requests.\n");
            } else {
                for (LeaveRequest l : pending) {
                    sb.append("- ").append(l.getLeaveType())
                            .append(": ").append(l.getStartDate())
                            .append(" to ").append(l.getEndDate())
                            .append(" (").append(l.getTotalDays()).append(" days)\n");
                }
            }
        } catch (Exception e) {
            sb.append("(Leave request data temporarily unavailable)\n");
        }
        sb.append("\n");

        // ── 8. HOLIDAYS ──────────────────────────────────────────────────────
        sb.append("🎉 COMPANY HOLIDAYS\n");
        sb.append("─────────────────────────────────────────────────────────\n");
        try {
            List<Holiday> holidays = holidayService.getAllHolidays();
            if (holidays.isEmpty()) {
                sb.append("No holidays configured.\n");
            } else {
                holidays.sort(Comparator.comparing(Holiday::getHolidayDate));
                for (Holiday h : holidays) {
                    sb.append("- ").append(h.getHolidayDate())
                            .append(" | ").append(h.getHolidayName())
                            .append(" | Type: ").append(nullSafe(h.getHolidayType(), "N/A")).append("\n");
                }
            }
        } catch (Exception e) {
            sb.append("(Holiday data temporarily unavailable)\n");
        }
        sb.append("\n");

        // ── 9. DOCUMENTS ──────────────────────────────────────────────────────
        sb.append("📄 DOCUMENT STATUS\n");
        sb.append("─────────────────────────────────────────────────────────\n");
        try {
            if (emp != null) {
                List<OnboardingDocument> docs = documentService.getDocumentsByEmployee(emp);
                if (docs.isEmpty()) {
                    sb.append("No documents found.\n");
                } else {
                    long pending = docs.stream().filter(d -> "PENDING".equalsIgnoreCase(d.getStatus())).count();
                    long submitted = docs.stream().filter(d -> "SUBMITTED".equalsIgnoreCase(d.getStatus())).count();
                    long verified = docs.stream().filter(d -> "VERIFIED".equalsIgnoreCase(d.getStatus())).count();
                    long rejected = docs.stream().filter(d -> "REJECTED".equalsIgnoreCase(d.getStatus())).count();
                    sb.append("Total: ").append(docs.size()).append("\n");
                    sb.append("  - Pending: ").append(pending).append("\n");
                    sb.append("  - Submitted: ").append(submitted).append("\n");
                    sb.append("  - Verified: ").append(verified).append("\n");
                    sb.append("  - Rejected: ").append(rejected).append("\n");

                    // List mandatory documents that need attention
                    List<OnboardingDocument> mandatoryNeedingAction = docs.stream()
                            .filter(d -> d.isMandatory() && ("PENDING".equalsIgnoreCase(d.getStatus()) || "REJECTED".equalsIgnoreCase(d.getStatus())))
                            .collect(Collectors.toList());
                    if (!mandatoryNeedingAction.isEmpty()) {
                        sb.append("⚠️ Mandatory documents needing attention:\n");
                        for (OnboardingDocument d : mandatoryNeedingAction) {
                            sb.append("  - ").append(d.getDocumentName()).append(" (").append(d.getStatus()).append(")\n");
                        }
                    }
                }
            }
        } catch (Exception e) {
            sb.append("(Document data temporarily unavailable)\n");
        }
        sb.append("\n");

        // ── 10. NOTIFICATIONS ─────────────────────────────────────────────────
        sb.append("🔔 RECENT NOTIFICATIONS\n");
        sb.append("─────────────────────────────────────────────────────────\n");
        try {
            List<Notification> notifications = notificationService.getPersistentNotifications(employeeId, "EMPLOYEE");
            if (notifications.isEmpty()) {
                sb.append("No notifications.\n");
            } else {
                notifications.stream().limit(5).forEach(n -> {
                    sb.append("- [").append(nullSafe(n.getType(), "INFO")).append("] ")
                            .append(n.getTitle());
                    if (n.getMessage() != null && !n.getMessage().equals(n.getTitle())) {
                        sb.append(": ").append(n.getMessage());
                    }
                    sb.append(n.getReadAt() == null ? " [UNREAD]" : " [read]");
                    sb.append("\n");
                });
                long unread = notifications.stream().filter(n -> n.getReadAt() == null).count();
                sb.append("Unread notifications: ").append(unread).append("\n");
            }
        } catch (Exception e) {
            sb.append("(Notification data temporarily unavailable)\n");
        }
        sb.append("\n");

        // ── 11. ANNOUNCEMENTS ─────────────────────────────────────────────────
        sb.append("📢 COMPANY ANNOUNCEMENTS\n");
        sb.append("─────────────────────────────────────────────────────────\n");
        try {
            List<AnnouncementDTO> announcements = announcementService.getAnnouncementsForEmployee();
            if (announcements.isEmpty()) {
                sb.append("No active announcements.\n");
            } else {
                for (AnnouncementDTO a : announcements) {
                    sb.append("- [").append(nullSafe(a.getCategory(), "General")).append("]");
                    if (Boolean.TRUE.equals(a.getIsPinned())) sb.append(" 📌");
                    sb.append(" ").append(a.getTitle());
                    if (a.getDescription() != null && !a.getDescription().isBlank()) {
                        String desc = a.getDescription().length() > 100
                                ? a.getDescription().substring(0, 97) + "..."
                                : a.getDescription();
                        sb.append(": ").append(desc);
                    }
                    if (a.getPriority() != null && !"NORMAL".equalsIgnoreCase(a.getPriority())) {
                        sb.append(" [" + a.getPriority() + "]");
                    }
                    sb.append("\n");
                }
            }
        } catch (Exception e) {
            sb.append("(Announcement data temporarily unavailable)\n");
        }
        sb.append("\n");

        // ── 12. FINAL INSTRUCTION ────────────────────────────────────────────
        sb.append("═══════════════════════════════════════════════════════════════\n");
        sb.append("⚠️ FINAL INSTRUCTION — READ THIS CAREFULLY ⚠️\n");
        sb.append("═══════════════════════════════════════════════════════════════\n");
        sb.append("\n");
        sb.append("ALL OF THE ABOVE IS REAL, LIVE DATA FROM THE HRMS SYSTEM.\n");
        sb.append("\n");
        sb.append("WHEN THE USER ASKS FOR ANY SUMMARY:\n");
        sb.append("  1. LOOK at the data above.\n");
        sb.append("  2. EXTRACT the relevant numbers.\n");
        sb.append("  3. PRESENT them in a clean, readable format.\n");
        sb.append("  4. NEVER say 'go to the X tab' or 'check the Y page'.\n");
        sb.append("  5. The data is RIGHT HERE — USE IT.\n");
        sb.append("\n");
        sb.append("FOR ATTENDANCE SUMMARY:\n");
        sb.append("  → Use the MONTHLY ATTENDANCE SUMMARY section\n");
        sb.append("  → Use the RECENT ATTENDANCE LOG section\n");
        sb.append("  → Show: present days, absent days, leave days, percentage, hours\n");
        sb.append("\n");
        sb.append("FOR LEAVE SUMMARY:\n");
        sb.append("  → Use the LEAVE BALANCE section\n");
        sb.append("  → Show: used, remaining, pending requests\n");
        sb.append("\n");
        sb.append("REMEMBER: Display data. Never redirect.\n");
        sb.append("═══════════════════════════════════════════════════════════════\n");

        return sb.toString();
    }

    private String handleAttendanceSummaryRequest(String message, String employeeId) {
        String msg = message.toLowerCase().trim();

        // Check if this is an attendance-related request
        boolean mentionsMonthByName = extractMonthYearFromMessage(message) != null;
        boolean isAttendanceRequest = msg.contains("attendance") ||
                msg.contains("present") || msg.contains("absent") ||
                msg.contains("check in") || msg.contains("checkin") ||
                msg.contains("check out") || msg.contains("checkout") ||
                msg.contains("working hours") || msg.contains("worked") ||
                msg.contains("this month") || msg.contains("current month") ||
                msg.contains("last month") || msg.contains("next month") ||
                msg.contains("coming month") || msg.contains("previous month") ||
                msg.contains("my attendance") || msg.contains("attendance summary") ||
                (mentionsMonthByName && (msg.contains("attend") || msg.contains("work") || msg.contains("hour")));
        if (!isAttendanceRequest) {
            return null;
        }

        log.info("📊 Handling attendance summary request for employee: {}", employeeId);

        // ── Extract month/year from message ──
        int[] extracted = extractMonthYearFromMessage(message);
        LocalDate today = LocalDate.now();

        int year;
        int month;
        String monthDisplayName;

        if (extracted != null) {
            month = extracted[0];
            year = extracted[1];
            log.info("📅 Extracted from message: Month={}, Year={}", month, year);
        } else {
            month = today.getMonthValue();
            year = today.getYear();
            log.info("📅 Using current month: {}/{}", month, year);
        }

        monthDisplayName = java.time.Month.of(month).toString().charAt(0) +
                java.time.Month.of(month).toString().substring(1).toLowerCase();

        try {
            // ── Get monthly summary ──
            Map<String, Object> summary = attendanceService.getMonthlySummaryForEmployee(employeeId, year, month);

            // ── ✅ SAFE CONVERSION ──
            int totalDays = summary != null ? ((Number) summary.getOrDefault("totalDays", 0)).intValue() : 0;
            int presentDays = summary != null ? ((Number) summary.getOrDefault("presentDays", 0)).intValue() : 0;
            int absentDays = summary != null ? ((Number) summary.getOrDefault("absentDays", 0)).intValue() : 0;
            int leaveDays = summary != null ? ((Number) summary.getOrDefault("leaveDays", 0)).intValue() : 0;
            int percentage = summary != null ? ((Number) summary.getOrDefault("attendancePercentage", 0)).intValue() : 0;
            String totalHours = summary != null ? summary.getOrDefault("totalWorkingHours", "0h 00m").toString() : "0h 00m";

            StringBuilder response = new StringBuilder();
            response.append("**📊 Your Attendance Summary**\n\n");
            response.append("**").append(monthDisplayName).append(" ").append(year).append(":**\n");
            response.append("- Working days: ").append(totalDays).append("\n");
            response.append("- Present days: ").append(presentDays).append("\n");
            response.append("- Absent days: ").append(absentDays).append("\n");
            response.append("- Leave days: ").append(leaveDays).append("\n");
            response.append("- Attendance percentage: ").append(percentage).append("%\n");
            response.append("- Total hours worked: ").append(totalHours).append("\n");
            response.append("- Overtime: ").append(summary.getOrDefault("overtime", "0h 00m")).append("\n");
            response.append("- Late arrivals: ").append(summary.getOrDefault("lateDays", "0")).append("\n");
            response.append("- Early departures: ").append(summary.getOrDefault("earlyDepartures", "0")).append("\n");

            // ── Get today's status (only if showing current month) ──
            boolean isCurrentMonth = (month == today.getMonthValue() && year == today.getYear());
            if (isCurrentMonth) {
                response.append("\n**Today (").append(today).append("):**\n");
                try {
                    Attendance todayAtt = attendanceService.getTodayAttendance(employeeId);
                    if (todayAtt != null && todayAtt.getCheckInTime() != null) {
                        LocalDateTime checkOutTime = todayAtt.getCheckOutTime();
                        LocalDateTime now = LocalDateTime.now();
                        boolean isPastSevenPM = now.getHour() >= 19;

                        // CASE 1: Still logged in (before 7:00 PM)
                        if (checkOutTime == null && !isPastSevenPM) {
                            response.append("- 🟢 **Active** (Currently working)\n");
                            response.append("- Check-in at: ")
                                    .append(todayAtt.getCheckInTime().format(DateTimeFormatter.ofPattern("hh:mm a"))).append("\n");

                            long minutes = java.time.Duration.between(
                                    todayAtt.getCheckInTime(),
                                    now
                            ).toMinutes() - (todayAtt.getTotalBreakMinutes() != null ? todayAtt.getTotalBreakMinutes() : 0);
                            response.append("- Working hours so far: ")
                                    .append(String.format("%dh %02dm", Math.max(0, minutes / 60), Math.max(0, minutes % 60))).append("\n");
                            response.append("- ⚠️ **Remember to check out before leaving!**\n");
                        }
                        // CASE 2: Auto-checked out (past 7:00 PM, forgot to logout)
                        else if (checkOutTime == null && isPastSevenPM) {
                            response.append("- ✅ Auto-Checked Out at 07:00 pm\n");
                            response.append("- Check-in at: ")
                                    .append(todayAtt.getCheckInTime().format(DateTimeFormatter.ofPattern("hh:mm a"))).append("\n");
                            response.append("- 📌 You forgot to check out - system auto-checked you out at 7:00 PM\n");
                        }
                        // CASE 3: Actually checked out
                        else if (checkOutTime != null) {
                            response.append("- ✅ Checked Out at ")
                                    .append(checkOutTime.format(DateTimeFormatter.ofPattern("hh:mm a"))).append("\n");
                            response.append("- Check-in at: ")
                                    .append(todayAtt.getCheckInTime().format(DateTimeFormatter.ofPattern("hh:mm a"))).append("\n");
                            if (todayAtt.getTotalWorkingHours() != null) {
                                response.append("- Working hours today: ").append(todayAtt.getTotalWorkingHours()).append("\n");
                            }
                        }
                    } else {
                        response.append("- ❌ Not checked in today\n");
                    }
                } catch (Exception e) {
                    log.warn("Could not get today's attendance: {}", e.getMessage());
                    response.append("- Attendance data temporarily unavailable\n");
                }
            }

            // ── Get recent attendance (EXCLUDING TODAY) ──
            boolean showRecent = msg.contains("recent") || msg.contains("last") ||
                    msg.contains("daily") || msg.contains("day by day") ||
                    isCurrentMonth;

            if (showRecent) {
                try {
                    int days = 7;
                    java.util.regex.Pattern dayPattern = java.util.regex.Pattern.compile("\\b(\\d+)\\s*(?:day|d)\\b");
                    java.util.regex.Matcher dayMatcher = dayPattern.matcher(msg);
                    if (dayMatcher.find()) {
                        days = Math.min(Integer.parseInt(dayMatcher.group(1)), 30);
                    }

                    List<Map<String, Object>> recent = attendanceService.getRecentAttendanceForEmployee(employeeId, days);
                    if (recent != null && !recent.isEmpty()) {

                        // ── 🔥 FILTER OUT TODAY ──
                        List<Map<String, Object>> filteredRecent = new ArrayList<>();
                        for (Map<String, Object> day : recent) {
                            Object dateObj = day.get("attendanceDate");
                            if (dateObj instanceof LocalDate) {
                                LocalDate date = (LocalDate) dateObj;
                                if (!date.equals(today)) {
                                    filteredRecent.add(day);
                                }
                            } else {
                                // Fallback: check dateFormatted
                                String dateStr = day.get("dateFormatted") != null ? day.get("dateFormatted").toString() : "";
                                String todayStr = today.toString();
                                // Check if dateFormatted contains today's date
                                if (!dateStr.isEmpty() && !dateStr.contains(todayStr) && !dateStr.contains("Today")) {
                                    filteredRecent.add(day);
                                }
                            }
                        }

                        if (filteredRecent.isEmpty()) {
                            response.append("\n**Recent Activity:** No past attendance records found.\n");
                        } else {
                            response.append("\n**Recent Activity (last ").append(Math.min(filteredRecent.size(), days)).append(" days):**\n");
                            int count = 0;
                            for (Map<String, Object> day : filteredRecent) {
                                if (count >= days) break;

                                String date = day != null && day.get("dateFormatted") != null
                                        ? day.get("dateFormatted").toString() : "Unknown date";
                                String status = day != null && day.get("statusText") != null
                                        ? day.get("statusText").toString()
                                        : (day != null && day.get("status") != null ? day.get("status").toString() : "Unknown");
                                String checkIn = day != null && day.get("checkInFormatted") != null
                                        ? day.get("checkInFormatted").toString() : "--:--";
                                String checkOut = day != null && day.get("checkOutFormatted") != null
                                        ? day.get("checkOutFormatted").toString() : "--:--";
                                String hours = day != null && day.get("currentWorkingTime") != null
                                        ? day.get("currentWorkingTime").toString() : "0h 00m";
                                String remarks = day != null && day.get("remarks") != null
                                        ? day.get("remarks").toString() : "";

                                response.append("- ").append(date).append(": ").append(status)
                                        .append(" (In: ").append(checkIn).append(", Out: ").append(checkOut)
                                        .append(", Hours: ").append(hours).append(")");
                                if (!remarks.isEmpty()) {
                                    response.append(" [").append(remarks).append("]");
                                }
                                response.append("\n");
                                count++;
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Could not get recent attendance: {}", e.getMessage());
                    response.append("\n**Recent Activity:** Data temporarily unavailable.\n");
                }
            }

            log.info("✅ Attendance summary generated for {}/{}", month, year);
            return response.toString();

        } catch (Exception e) {
            log.error("❌ Error generating attendance summary: {}", e.getMessage(), e);
            return "I'm having trouble retrieving your " + monthDisplayName + " " + year +
                    " attendance data. Please try again later.";
        }
    }


    /**
     * Handles specific attendance-related questions
     * Returns formatted response for specific queries
     */
    private String handleSpecificAttendanceRequest(String message, String employeeId) {
        String msg = message.toLowerCase().trim();
        LocalDate today = LocalDate.now();

        // ── 1. CHECK-IN STATUS ──
        if (msg.contains("am i checked in") || msg.contains("checked in") && msg.contains("today") ||
                msg.contains("check in status") || msg.contains("checkin status")) {
            try {
                Attendance todayAtt = attendanceService.getTodayAttendance(employeeId);
                if (todayAtt == null || todayAtt.getCheckInTime() == null) {
                    return "❌ **You are not checked in today.**\n\nPlease check in to start your work day.";
                }

                if (todayAtt.getCheckOutTime() != null) {
                    return "✅ **You checked out today** at " +
                            todayAtt.getCheckOutTime().format(DateTimeFormatter.ofPattern("hh:mm a")) +
                            ".\n\nYour total working time today: " +
                            (todayAtt.getTotalWorkingHours() != null ? todayAtt.getTotalWorkingHours() : "Calculating...");
                }

                if ("BREAK".equals(todayAtt.getStatus())) {
                    return "🟡 **You are currently on break.**\n\n" +
                            "Check-in time: " + todayAtt.getCheckInTime().format(DateTimeFormatter.ofPattern("hh:mm a"));
                }

                return "🟢 **You are currently checked in.**\n\n" +
                        "Check-in time: " + todayAtt.getCheckInTime().format(DateTimeFormatter.ofPattern("hh:mm a")) +
                        "\nCurrent working hours: " + todayAtt.getCurrentSessionTime();

            } catch (Exception e) {
                log.error("Error checking status: {}", e.getMessage());
                return "I couldn't check your status right now. Please try again later.";
            }
        }

        // ── 2. TODAY'S CHECK-IN TIME ──
        if (msg.contains("what time did i check in") || msg.contains("check in time") ||
                msg.contains("checkin time") || msg.contains("what time did i checkin")) {
            try {
                Attendance todayAtt = attendanceService.getTodayAttendance(employeeId);
                if (todayAtt == null || todayAtt.getCheckInTime() == null) {
                    return "❌ **You haven't checked in today.**\n\nPlease check in to start your work day.";
                }
                return "✅ **You checked in today at:** " +
                        todayAtt.getCheckInTime().format(DateTimeFormatter.ofPattern("hh:mm a"));

            } catch (Exception e) {
                log.error("Error getting check-in time: {}", e.getMessage());
                return "I couldn't get your check-in time right now. Please try again later.";
            }
        }

        // ── 3. TODAY'S STATUS ──
        if (msg.contains("today's status") || msg.contains("status today") ||
                msg.contains("what is my status") || msg.contains("my today status") ||
                msg.contains("am i working") || msg.contains("am i active")) {
            try {
                Attendance todayAtt = attendanceService.getTodayAttendance(employeeId);
                if (todayAtt == null || todayAtt.getCheckInTime() == null) {
                    return "❌ **Status: Not Checked In**\n\nYou have not checked in today.";
                }

                if (todayAtt.getCheckOutTime() != null) {
                    return "✅ **Status: Checked Out**\n\n" +
                            "You checked out at: " + todayAtt.getCheckOutTime().format(DateTimeFormatter.ofPattern("hh:mm a")) +
                            "\nTotal working time today: " +
                            (todayAtt.getTotalWorkingHours() != null ? todayAtt.getTotalWorkingHours() : "Calculating...");
                }

                if ("BREAK".equals(todayAtt.getStatus())) {
                    return "🟡 **Status: On Break**\n\n" +
                            "Check-in time: " + todayAtt.getCheckInTime().format(DateTimeFormatter.ofPattern("hh:mm a")) +
                            "\nWorking hours so far: " + todayAtt.getCurrentSessionTime();
                }

                return "🟢 **Status: Active (Working)**\n\n" +
                        "Check-in time: " + todayAtt.getCheckInTime().format(DateTimeFormatter.ofPattern("hh:mm a")) +
                        "\nWorking hours so far: " + todayAtt.getCurrentSessionTime();

            } catch (Exception e) {
                log.error("Error getting status: {}", e.getMessage());
                return "I couldn't get your status right now. Please try again later.";
            }
        }

        // ── 4. ATTENDANCE PERCENTAGE ──
        if (msg.contains("attendance percentage") || msg.contains("percentage") && msg.contains("attendance")) {
            try {
                int year = today.getYear();
                int month = today.getMonthValue();

                // Check if specific month/year mentioned
                int[] extracted = extractMonthYearFromMessage(message);
                if (extracted != null) {
                    year = extracted[1];
                    month = extracted[0];
                }

                Map<String, Object> summary = attendanceService.getMonthlySummaryForEmployee(employeeId, year, month);
                String monthName = java.time.Month.of(month).toString().charAt(0) +
                        java.time.Month.of(month).toString().substring(1).toLowerCase();

                int presentDays = summary != null ? ((Number) summary.getOrDefault("presentDays", 0)).intValue() : 0;
                int totalDays = summary != null ? ((Number) summary.getOrDefault("totalDays", 0)).intValue() : 0;
                int percentage = summary != null ? ((Number) summary.getOrDefault("attendancePercentage", 0)).intValue() : 0;

                if (totalDays == 0) {
                    return "📊 **Attendance Percentage**\n\nNo working days found for " + monthName + " " + year + ".";
                }

                return "📊 **Your Attendance Percentage**\n\n" +
                        "**" + monthName + " " + year + ":**\n" +
                        "- Present days: " + presentDays + " out of " + totalDays + "\n" +
                        "- Attendance percentage: **" + percentage + "%**\n" +
                        "- Absent days: " + summary.getOrDefault("absentDays", 0) + "\n" +
                        "- Leave days: " + summary.getOrDefault("leaveDays", 0);

            } catch (Exception e) {
                log.error("Error getting percentage: {}", e.getMessage());
                return "I couldn't calculate your attendance percentage right now. Please try again later.";
            }
        }

        // ── 5. PERIODIC ATTENDANCE (last N days, this month, specific month) ──
        boolean isPeriodicRequest = msg.contains("last") && (msg.contains("day") || msg.contains("days") || msg.contains("week")) ||
                msg.contains("this month") || msg.contains("current month") ||
                msg.contains("jan") || msg.contains("feb") || msg.contains("mar") ||
                msg.contains("apr") || msg.contains("may") || msg.contains("jun") ||
                msg.contains("jul") || msg.contains("aug") || msg.contains("sep") ||
                msg.contains("oct") || msg.contains("nov") || msg.contains("dec") ||
                msg.contains("attendance this month") || msg.contains("attendance for");

        if (isPeriodicRequest) {
            return handlePeriodicAttendanceRequest(message, employeeId);
        }

        // Not a specific request
        return null;
    }

    /**
     * Handles periodic attendance requests (last N days, this month, specific month)
     */
    private String handlePeriodicAttendanceRequest(String message, String employeeId) {
        String msg = message.toLowerCase().trim();
        LocalDate today = LocalDate.now();

        // ── Check for "last N days" or "last N weeks" ──
        java.util.regex.Pattern daysPattern = java.util.regex.Pattern.compile("last\\s*(\\d+)\\s*(?:day|d)");
        java.util.regex.Matcher daysMatcher = daysPattern.matcher(msg);
        if (daysMatcher.find()) {
            int days = Integer.parseInt(daysMatcher.group(1));
            days = Math.min(days, 30); // Limit to 30 days
            return getAttendanceForPeriod(employeeId, days);
        }

        // ── Check for "last N weeks" ──
        java.util.regex.Pattern weeksPattern = java.util.regex.Pattern.compile("last\\s*(\\d+)\\s*week");
        java.util.regex.Matcher weeksMatcher = weeksPattern.matcher(msg);
        if (weeksMatcher.find()) {
            int weeks = Integer.parseInt(weeksMatcher.group(1));
            int days = weeks * 7;
            days = Math.min(days, 30);
            return getAttendanceForPeriod(employeeId, days);
        }

        // ── Check for "last 7 days" or "last week" ──
        if (msg.contains("last 7 days") || msg.contains("last week")) {
            return getAttendanceForPeriod(employeeId, 7);
        }

        // ── Check for "this month" or "current month" ──
        if (msg.contains("this month") || msg.contains("current month")) {
            return getAttendanceForMonth(employeeId, today.getYear(), today.getMonthValue());
        }

        // ── Try to extract month/year ──
        int[] extracted = extractMonthYearFromMessage(message);
        if (extracted != null) {
            return getAttendanceForMonth(employeeId, extracted[1], extracted[0]);
        }

        // Default to current month summary
        return getAttendanceForMonth(employeeId, today.getYear(), today.getMonthValue());
    }

    /**
     * Get attendance for a specific number of days (past)
     */
    private String getAttendanceForPeriod(String employeeId, int days) {
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(days);

        try {
            List<Map<String, Object>> recent = attendanceService.getRecentAttendanceForEmployee(employeeId, days);
            if (recent == null || recent.isEmpty()) {
                return "📋 **Attendance for last " + days + " days**\n\nNo attendance records found for this period.";
            }

            // Calculate statistics
            int presentCount = 0;
            int absentCount = 0;
            int leaveCount = 0;
            int totalWorkingMinutes = 0;
            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("hh:mm a");

            StringBuilder response = new StringBuilder();
            response.append("📋 **Your Attendance (Last ").append(recent.size()).append(" days)**\n\n");

            for (Map<String, Object> day : recent) {
                String date = day.getOrDefault("dateFormatted", "").toString();
                String status = day.getOrDefault("statusText", day.getOrDefault("status", "")).toString();
                String checkIn = day.getOrDefault("checkInFormatted", "--:--").toString();
                String checkOut = day.getOrDefault("checkOutFormatted", "--:--").toString();
                String hours = day.getOrDefault("currentWorkingTime", "0h 00m").toString();

                if (status.equalsIgnoreCase("On Leave")) {
                    leaveCount++;
                    response.append("- ").append(date).append(": **On Leave**\n");
                } else if (status.equalsIgnoreCase("Absent") || status.equalsIgnoreCase("Not Checked In")) {
                    absentCount++;
                    response.append("- ").append(date).append(": ❌ **Absent**\n");
                } else {
                    presentCount++;
                    response.append("- ").append(date).append(": ✅ **").append(status).append("**");
                    response.append(" (In: ").append(checkIn).append(", Out: ").append(checkOut);
                    response.append(", Hours: ").append(hours).append(")\n");
                }
            }

            // Add summary
            response.append("\n**Summary:**\n");
            response.append("- Present: ").append(presentCount).append(" days\n");
            response.append("- Absent: ").append(absentCount).append(" days\n");
            response.append("- Leave: ").append(leaveCount).append(" days\n");

            int totalDays = presentCount + absentCount + leaveCount;
            if (totalDays > 0) {
                int percentage = (presentCount * 100) / totalDays;
                response.append("- Attendance: **").append(percentage).append("%**\n");
            }

            return response.toString();

        } catch (Exception e) {
            log.error("Error getting period attendance: {}", e.getMessage());
            return "I couldn't retrieve your attendance data right now. Please try again later.";
        }
    }

    /**
     * Get attendance for a specific month
     */
    private String getAttendanceForMonth(String employeeId, int year, int month) {
        String monthName = java.time.Month.of(month).toString().charAt(0) +
                java.time.Month.of(month).toString().substring(1).toLowerCase();

        try {
            log.info("📊 Getting attendance for {}/{} for employee: {}", month, year, employeeId);

            Map<String, Object> summary = attendanceService.getMonthlySummaryForEmployee(employeeId, year, month);

            log.debug("Summary data: {}", summary);

            // ── ✅ FIX: SAFELY CONVERT VALUES TO INT ──
            // Use Number class to handle both Integer and Long
            int totalDays = summary != null ? ((Number) summary.getOrDefault("totalDays", 0)).intValue() : 0;
            int presentDays = summary != null ? ((Number) summary.getOrDefault("presentDays", 0)).intValue() : 0;
            int absentDays = summary != null ? ((Number) summary.getOrDefault("absentDays", 0)).intValue() : 0;
            int leaveDays = summary != null ? ((Number) summary.getOrDefault("leaveDays", 0)).intValue() : 0;
            int percentage = summary != null ? ((Number) summary.getOrDefault("attendancePercentage", 0)).intValue() : 0;
            String totalHours = summary != null ? summary.getOrDefault("totalWorkingHours", "0h 00m").toString() : "0h 00m";

            if (totalDays == 0) {
                // Check if this is a future month or current month with no data
                LocalDate today = LocalDate.now();
                LocalDate monthDate = LocalDate.of(year, month, 1);

                if (monthDate.isAfter(today)) {
                    return "📊 **Attendance for " + monthName + " " + year + "**\n\n" +
                            "This month is in the future. No attendance data available yet.\n" +
                            "Come back after you've started working in " + monthName + ".";
                } else {
                    return "📊 **Attendance for " + monthName + " " + year + "**\n\n" +
                            "No attendance records found for this month.\n" +
                            "If you think this is incorrect, please contact HR.";
                }
            }

            StringBuilder response = new StringBuilder();
            response.append("📊 **Your Attendance for ").append(monthName).append(" ").append(year).append("**\n\n");
            response.append("- Working days: ").append(totalDays).append("\n");
            response.append("- Present days: ").append(presentDays).append("\n");
            response.append("- Absent days: ").append(absentDays).append("\n");
            response.append("- Leave days: ").append(leaveDays).append("\n");
            response.append("- Attendance percentage: **").append(percentage).append("%**\n");
            response.append("- Total hours worked: ").append(totalHours).append("\n");

            // ── Get recent days for this month (if any) ──
            try {
                List<Map<String, Object>> recent = attendanceService.getRecentAttendanceForEmployee(employeeId, 10);
                if (recent != null && !recent.isEmpty()) {
                    boolean hasMonthData = false;
                    for (Map<String, Object> day : recent) {
                        Object dateObj = day.get("attendanceDate");
                        if (dateObj instanceof LocalDate) {
                            LocalDate date = (LocalDate) dateObj;
                            if (date.getYear() == year && date.getMonthValue() == month) {
                                hasMonthData = true;
                                break;
                            }
                        }
                    }

                    if (hasMonthData) {
                        response.append("\n**Recent days in ").append(monthName).append(":**\n");
                        for (Map<String, Object> day : recent) {
                            Object dateObj = day.get("attendanceDate");
                            if (dateObj instanceof LocalDate) {
                                LocalDate date = (LocalDate) dateObj;
                                if (date.getYear() == year && date.getMonthValue() == month) {
                                    String dateStr = day.getOrDefault("dateFormatted", "").toString();
                                    String status = day.getOrDefault("statusText", day.getOrDefault("status", "")).toString();
                                    String hours = day.getOrDefault("currentWorkingTime", "0h 00m").toString();
                                    response.append("- ").append(dateStr).append(": ").append(status);
                                    if (!status.equalsIgnoreCase("On Leave") && !status.equalsIgnoreCase("Absent") && !status.equalsIgnoreCase("Not Checked In")) {
                                        response.append(" (").append(hours).append(")");
                                    }
                                    response.append("\n");
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Could not get recent attendance for month view: {}", e.getMessage());
                // Don't fail the whole response
            }

            return response.toString();

        } catch (Exception e) {
            log.error("❌ Error getting month attendance for {}/{}: {}", month, year, e.getMessage(), e);
            return "I couldn't retrieve your attendance for " + monthName + " " + year +
                    ". Please try again later.\n\nError: " + e.getMessage();
        }
    }

    /**
     * Extracts month and year from user message
     * Returns int[2] where [0] = month (1-12), [1] = year
     * Returns null if no month/year found
     */
    private int[] extractMonthYearFromMessage(String message) {
        String msg = message.toLowerCase().trim();
        LocalDate today = LocalDate.now();
        int currentYear = today.getYear();
        int currentMonth = today.getMonthValue();

        // ── Check for "this month" FIRST ──
        if (msg.contains("this month") || msg.contains("current month")) {
            log.info("📅 Detected 'this month' request -> using current month: {}/{}", currentMonth, currentYear);
            return new int[]{currentMonth, currentYear};
        }

        // ── Check for "last month" ──
        if (msg.contains("last month") || msg.contains("previous month")) {
            int lastMonth = currentMonth - 1;
            int year = currentYear;
            if (lastMonth < 1) {
                lastMonth = 12;
                year = currentYear - 1;
            }
            log.info("📅 Detected 'last month' request -> using: {}/{}", lastMonth, year);
            return new int[]{lastMonth, year};
        }

        // ── Check for "next month" or "coming month" ──
        if (msg.contains("next month") || msg.contains("coming month")) {
            int nextMonth = currentMonth + 1;
            int year = currentYear;
            if (nextMonth > 12) {
                nextMonth = 1;
                year = currentYear + 1;
            }
            log.info("📅 Detected 'next month' request -> using: {}/{}", nextMonth, year);
            return new int[]{nextMonth, year};
        }

        // ── Month name mapping (full names only, not partial) ──
        // Use WORD BOUNDARY matching to avoid "mar" matching "this month"
        Map<String, Integer> monthMap = new LinkedHashMap<>();
        monthMap.put("january", 1);
        monthMap.put("february", 2);
        monthMap.put("march", 3);
        monthMap.put("april", 4);
        monthMap.put("may", 5);
        monthMap.put("june", 6);
        monthMap.put("july", 7);
        monthMap.put("august", 8);
        monthMap.put("september", 9);
        monthMap.put("october", 10);
        monthMap.put("november", 11);
        monthMap.put("december", 12);

        // ── Try to find full month name using word boundaries ──
        for (Map.Entry<String, Integer> entry : monthMap.entrySet()) {
            // Use word boundary to match whole words only
            String pattern = "\\b" + entry.getKey() + "\\b";
            if (java.util.regex.Pattern.compile(pattern).matcher(msg).find()) {
                int foundMonth = entry.getValue();
                int foundYear = currentYear;

                // Look for 4-digit year
                java.util.regex.Pattern yearPattern = java.util.regex.Pattern.compile("\\b(20\\d{2})\\b");
                java.util.regex.Matcher yearMatcher = yearPattern.matcher(msg);
                if (yearMatcher.find()) {
                    foundYear = Integer.parseInt(yearMatcher.group(1));
                }

                log.info("📅 Extracted month: {} ({}) from '{}'", entry.getKey(), foundMonth, msg);
                return new int[]{foundMonth, foundYear};
            }
        }

        // ── Try short month names (3-letter abbreviations with word boundaries) ──
        Map<String, Integer> shortMonthMap = new LinkedHashMap<>();
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

        for (Map.Entry<String, Integer> entry : shortMonthMap.entrySet()) {
            String pattern = "\\b" + entry.getKey() + "\\b";
            if (java.util.regex.Pattern.compile(pattern).matcher(msg).find()) {
                int foundMonth = entry.getValue();
                int foundYear = currentYear;

                // Look for 4-digit year
                java.util.regex.Pattern yearPattern = java.util.regex.Pattern.compile("\\b(20\\d{2})\\b");
                java.util.regex.Matcher yearMatcher = yearPattern.matcher(msg);
                if (yearMatcher.find()) {
                    foundYear = Integer.parseInt(yearMatcher.group(1));
                }

                log.info("📅 Extracted short month: {} ({}) from '{}'", entry.getKey(), foundMonth, msg);
                return new int[]{foundMonth, foundYear};
            }
        }

        // ── Try to find numeric month (e.g., "5", "05", "month 5") ──
        java.util.regex.Pattern numPattern = java.util.regex.Pattern.compile("\\b(0?[1-9]|1[0-2])\\b");
        java.util.regex.Matcher numMatcher = numPattern.matcher(msg);
        if (numMatcher.find()) {
            int foundMonth = Integer.parseInt(numMatcher.group(1));
            int foundYear = currentYear;

            // Look for year
            java.util.regex.Pattern yearPattern = java.util.regex.Pattern.compile("\\b(20\\d{2})\\b");
            java.util.regex.Matcher yearMatcher = yearPattern.matcher(msg);
            if (yearMatcher.find()) {
                foundYear = Integer.parseInt(yearMatcher.group(1));
            }

            log.info("📅 Extracted numeric month: {} from '{}'", foundMonth, msg);
            return new int[]{foundMonth, foundYear};
        }

        // ── No month found ──
        log.info("📅 No month extracted from: '{}'", msg);
        return null;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  HELPER UTILITIES
    // ════════════════════════════════════════════════════════════════════════

    private void appendLeaveLine(StringBuilder sb, String label, Object balanceObj) {
        if (balanceObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> bal = (Map<String, Object>) balanceObj;
            Object balance = bal.getOrDefault("balance", "N/A");
            sb.append(label).append(" Leave — allocated: ")
                    .append(bal.getOrDefault("total", "N/A"))
                    .append(", used: ").append(bal.getOrDefault("used", "N/A"))
                    .append(", remaining: ");
            // Special display for Earned leave which can be very large / unlimited
            if (balance instanceof Number && ((Number) balance).doubleValue() > 900) {
                sb.append("Unlimited");
            } else {
                sb.append(balance);
            }
            sb.append("\n");
        }
    }

    /** Returns the value if non-null, otherwise empty string. */
    private String nullSafe(String value) {
        return value != null ? value : "";
    }

    /** Returns the value if non-null, otherwise the fallback. */
    private String nullSafe(String value, String fallback) {
        return value != null ? value : fallback;
    }
    private static final Set<String> INFORMATIONAL_STARTERS = Set.of(
            "what", "who", "which", "how", "give me", "show", "show me", "list",
            "summarize", "summary", "tell me", "can you tell", "could you tell",
            "display", "explain", "describe", "is there", "are there", "do i", "does");

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
    // ════════════════════════════════════════════════════════════════════════
    //  SESSION MANAGEMENT
    // ════════════════════════════════════════════════════════════════════════

    private ChatSession getOrCreateSession(ChatRequestDto request) {
        if (request.getSessionId() != null && request.getSessionId() > 0) {
            return sessionRepository.findById(request.getSessionId())
                    .orElseGet(() -> createNewSession(request.getEmployeeId(), false));
        }
        return createNewSession(request.getEmployeeId(), false);
    }

    private ChatSession createNewSession(String employeeId, boolean temporary) {
        ChatSession session = new ChatSession();
        session.setEmployeeId(employeeId);
        session.setIsTemporary(temporary);
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        return sessionRepository.save(session);
    }

    private void saveMessage(ChatSession session, String role, String content) {
        ChatMessage msg = new ChatMessage();
        msg.setSession(session);
        msg.setRole(role);
        msg.setContent(content);
        msg.setCreatedAt(LocalDateTime.now());
        messageRepository.save(msg);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  HISTORY BUILDERS
    // ════════════════════════════════════════════════════════════════════════

    private List<OpenRouterService.MessagePair> buildHistoryFromDB(List<ChatMessage> messages) {
        List<OpenRouterService.MessagePair> pairs = new ArrayList<>();
        String userMsg = null;
        int start = Math.max(0, messages.size() - (MAX_HISTORY_PAIRS * 2));
        List<ChatMessage> recent = messages.subList(start, messages.size());

        for (ChatMessage msg : recent) {
            if ("user".equals(msg.getRole())) {
                userMsg = msg.getContent();
            } else if ("assistant".equals(msg.getRole()) && userMsg != null) {
                pairs.add(new OpenRouterService.MessagePair(userMsg, msg.getContent()));
                userMsg = null;
            }
        }
        return pairs;
    }

    private List<OpenRouterService.MessagePair> buildHistoryFromClient(
            List<ChatRequestDto.MessageDto> history) {
        List<OpenRouterService.MessagePair> pairs = new ArrayList<>();
        if (history == null || history.isEmpty()) return pairs;

        String userMsg = null;
        int start = Math.max(0, history.size() - (MAX_HISTORY_PAIRS * 2));

        for (int i = start; i < history.size(); i++) {
            ChatRequestDto.MessageDto msg = history.get(i);
            if ("user".equals(msg.getRole())) {
                userMsg = msg.getContent();
            } else if ("assistant".equals(msg.getRole()) && userMsg != null) {
                pairs.add(new OpenRouterService.MessagePair(userMsg, msg.getContent()));
                userMsg = null;
            }
        }
        return pairs;
    }

    private String generateSessionName(String firstMessage) {
        if (firstMessage == null || firstMessage.trim().isEmpty()) return "New Chat";
        return firstMessage.length() > 50
                ? firstMessage.substring(0, 47) + "..."
                : firstMessage;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  READ-ONLY SESSION QUERIES
    // ════════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public ChatResponseDto loadSessionHistory(Long sessionId, String employeeId) {
        ChatResponseDto response = new ChatResponseDto();
        try {
            ChatSession session = sessionRepository.findById(sessionId)
                    .orElseThrow(() -> new RuntimeException("Session not found"));

            if (!session.getEmployeeId().equals(employeeId)) {
                response.setSuccess(false);
                response.setError("Unauthorized access");
                return response;
            }

            List<ChatMessage> messages =
                    messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
            List<ChatResponseDto.MessageInfo> messageInfos = messages.stream()
                    .map(this::toMessageInfo)
                    .collect(Collectors.toList());

            response.setSuccess(true);
            response.setSessionId(sessionId);
            response.setSessionName(session.getSessionName());
            response.setMessages(messageInfos);

        } catch (Exception e) {
            log.error("Error loading session", e);
            response.setSuccess(false);
            response.setError(e.getMessage());
        }
        return response;
    }

    @Transactional(readOnly = true)
    public ChatResponseDto getAllSessions(String employeeId) {
        ChatResponseDto response = new ChatResponseDto();
        try {
            List<ChatSession> sessions = sessionRepository
                    .findByEmployeeIdAndIsTemporaryFalseOrderByUpdatedAtDesc(employeeId);

            List<ChatResponseDto.SessionInfo> sessionInfos = sessions.stream()
                    .map(s -> {
                        ChatResponseDto.SessionInfo info = new ChatResponseDto.SessionInfo();
                        info.setId(s.getId());
                        info.setSessionName(
                                s.getSessionName() != null ? s.getSessionName() : "Chat " + s.getId());
                        info.setUpdatedAt(s.getUpdatedAt().format(FORMATTER));
                        return info;
                    })
                    .collect(Collectors.toList());

            response.setSuccess(true);
            response.setSessions(sessionInfos);

        } catch (Exception e) {
            log.error("Error getting sessions", e);
            response.setSuccess(false);
            response.setError(e.getMessage());
        }
        return response;
    }

    public ChatResponseDto deleteSession(Long sessionId, String employeeId) {
        ChatResponseDto response = new ChatResponseDto();
        try {
            ChatSession session = sessionRepository.findById(sessionId)
                    .orElseThrow(() -> new RuntimeException("Session not found"));

            if (!session.getEmployeeId().equals(employeeId)) {
                response.setSuccess(false);
                response.setError("Unauthorized");
                return response;
            }

            messageRepository.deleteBySessionId(sessionId);
            sessionRepository.delete(session);
            response.setSuccess(true);

        } catch (Exception e) {
            log.error("Error deleting session", e);
            response.setSuccess(false);
            response.setError(e.getMessage());
        }
        return response;
    }
    private static final Pattern EMP_LEAVE_ID_PATTERN = Pattern.compile("(LV-\\d{4}-\\d{3,6})", Pattern.CASE_INSENSITIVE);
    private static final Pattern ISO_DATE_PATTERN = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})");
    /**
     * Detects and handles employee leave actions via chat: apply, withdraw, cancel.
     * Mirrors the pattern of handleSpecificAttendanceRequest/handleAttendanceSummaryRequest -
     * checked early in sendMessage() and, if it returns non-null, bypasses the LLM entirely
     * so leave state changes are deterministic and auditable (never left to free-form LLM text).
     */
    private String handleLeaveActionRequest(String message, String employeeId) {
        String msg = message.toLowerCase().trim();

        if (message.trim().endsWith("?") || isInformationalQuery(msg)) {
            return null; // a question about leave, not a command to apply/withdraw/cancel
        }

        boolean wantsApply = containsWord(msg, "apply") && containsWord(msg, "leave");
        boolean wantsWithdraw = containsWord(msg, "withdraw") && containsWord(msg, "leave");
        boolean wantsCancel = containsWord(msg, "cancel") && containsWord(msg, "leave");

        if (!wantsApply && !wantsWithdraw && !wantsCancel) {
            return null;
        }

        try {
            if (wantsApply) {
                return handleApplyLeave(message, msg, employeeId);
            } else if (wantsWithdraw) {
                return handleWithdrawLeave(message, employeeId);
            } else {
                return handleCancelLeave(message, employeeId);
            }
        } catch (Exception e) {
            log.error("Error handling leave action request: {}", e.getMessage(), e);
            return "⚠️ I ran into a problem processing that leave request: " + e.getMessage();
        }
    }

    private String handleApplyLeave(String original, String msg, String employeeId) {
        String leaveType = null;
        if (msg.contains("sick")) leaveType = "sick";
        else if (msg.contains("casual")) leaveType = "casual";
        else if (msg.contains("earned")) leaveType = "earned";

        if (leaveType == null) {
            return "To apply for leave, please tell me the leave type (sick, casual, or earned) and the dates, " +
                    "e.g. \"apply for sick leave from 2026-08-01 to 2026-08-03, reason: fever\".";
        }

        Matcher dateMatcher = ISO_DATE_PATTERN.matcher(original);
        LocalDate startDate = null, endDate = null;
        if (dateMatcher.find()) {
            startDate = LocalDate.parse(dateMatcher.group(1));
            if (dateMatcher.find()) {
                endDate = LocalDate.parse(dateMatcher.group(1));
            } else {
                endDate = startDate;
            }
        }

        if (startDate == null) {
            return "I couldn't find the leave dates in your message. Please use YYYY-MM-DD format, " +
                    "e.g. \"apply for sick leave from 2026-08-01 to 2026-08-03\".";
        }

        String reason = null;
        int reasonIdx = msg.indexOf("reason:");
        if (reasonIdx == -1) reasonIdx = msg.indexOf("because");
        if (reasonIdx != -1) {
            reason = original.substring(reasonIdx).replaceFirst("(?i)^(reason:|because)", "").trim();
            if (reason.isEmpty()) reason = null;
        }

        LeaveRequest leaveRequest = new LeaveRequest();
        leaveRequest.setEmployeeId(employeeId);
        leaveRequest.setLeaveType(leaveType);
        leaveRequest.setStartDate(startDate);
        leaveRequest.setEndDate(endDate);
        leaveRequest.setReason(reason);

        Map<String, Object> result = leaveActionService.applyLeave(leaveRequest);
        boolean ok = Boolean.TRUE.equals(result.get("success"));
        if (ok) {
            return "✅ Your " + leaveType + " leave request from " + startDate + " to " + endDate +
                    " has been submitted (ID: " + result.get("leaveId") + "). It is now pending HR approval.";
        } else {
            return "⚠️ Could not submit your leave request: " + result.get("error");
        }
    }

    private String handleWithdrawLeave(String original, String employeeId) {
        String leaveId = extractLeaveId(original, employeeId, "PENDING");
        if (leaveId == null) {
            return "I couldn't find a matching pending leave request to withdraw. " +
                    "You can give me the leave ID (e.g. LV-2026-1234), or ask \"show my pending leaves\" first.";
        }
        Map<String, Object> result = leaveActionService.withdrawLeave(leaveId, "Withdrawn via chat");
        boolean ok = Boolean.TRUE.equals(result.get("success"));
        return ok
                ? "✅ Your leave request " + leaveId + " has been withdrawn."
                : "⚠️ Could not withdraw leave " + leaveId + ": " + result.get("error");
    }

    private String handleCancelLeave(String original, String employeeId) {
        String leaveId = extractLeaveId(original, employeeId, null);
        if (leaveId == null) {
            return "I couldn't find a matching leave request to cancel. " +
                    "You can give me the leave ID (e.g. LV-2026-1234), or ask \"show my leaves\" first.";
        }
        Map<String, Object> result = leaveActionService.cancelLeave(leaveId, "Cancelled via chat");
        boolean ok = Boolean.TRUE.equals(result.get("success"));
        return ok
                ? "✅ Your leave request " + leaveId + " has been cancelled" +
                (String.valueOf(result.get("message")).contains("restored") ? " and your leave balance has been restored." : ".")
                : "⚠️ Could not cancel leave " + leaveId + ": " + result.get("error");
    }

    /**
     * Finds the leave ID to act on: an explicit LV-#### mentioned in the message, or (as a
     * fallback) the employee's most recent leave matching the required status.
     * requiredStatus == null means "PENDING or APPROVED" (used for cancel).
     */
    private String extractLeaveId(String original, String employeeId, String requiredStatus) {
        Matcher idMatcher = EMP_LEAVE_ID_PATTERN.matcher(original);
        if (idMatcher.find()) {
            return idMatcher.group(1).toUpperCase();
        }
        List<LeaveRequest> leaves = leaveRequestRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId);
        for (LeaveRequest l : leaves) {
            if (requiredStatus == null) {
                if ("PENDING".equals(l.getStatus()) || "APPROVED".equals(l.getStatus())) {
                    return l.getId();
                }
            } else if (requiredStatus.equals(l.getStatus())) {
                return l.getId();
            }
        }
        return null;
    }
    private ChatResponseDto.MessageInfo toMessageInfo(ChatMessage msg) {
        ChatResponseDto.MessageInfo info = new ChatResponseDto.MessageInfo();
        info.setId(msg.getId());
        info.setRole(msg.getRole());
        info.setContent(msg.getContent());
        info.setCreatedAt(msg.getCreatedAt().format(FORMATTER));
        return info;
    }
}
