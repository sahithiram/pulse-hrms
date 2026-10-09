package com.mentis.hrms.service;

import com.itextpdf.html2pdf.HtmlConverter;
import com.mentis.hrms.model.*;
import com.mentis.hrms.repository.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Generates downloadable reports (CSV / Excel / PDF / Word) for the HR AI Assistant.
 *
 * Every export method below pulls data straight from the existing repositories/services
 * (nothing new is written to the database), turns it into a plain headers+rows table,
 * and hands that table to one of the four format renderers at the bottom of the file.
 *
 * This class does not touch, extend, or replace PdfGenerationService - that one is kept
 * exactly as-is for offer letters. This is a separate, report-focused export path.
 */
@Service
@Transactional(readOnly = true)
public class HrExportService {

    private static final Logger logger = LoggerFactory.getLogger(HrExportService.class);
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    @Autowired private AttendanceRepository attendanceRepository;
    @Autowired private LeaveRequestRepository leaveRequestRepository;
    @Autowired private JobRepository jobRepository;
    @Autowired private JobApplicationService jobApplicationService;
    @Autowired private HolidayRepository holidayRepository;
    @Autowired private OnboardingDocumentRepository onboardingDocumentRepository;

    // ===================================================================
    // PUBLIC EXPORT METHODS - one per data area
    // ===================================================================

    public byte[] exportAttendance(String format) {
        LocalDate today = LocalDate.now();
        List<Attendance> records = attendanceRepository.findAll().stream()
                .filter(a -> today.equals(a.getAttendanceDate()))
                .collect(Collectors.toList());

        String[] headers = {"Employee ID", "Date", "Status", "Check-In", "Check-Out", "Working Hours"};
        List<String[]> rows = records.stream().map(a -> new String[]{
                nullSafe(a.getEmployeeId()),
                nullSafe(a.getAttendanceDate()),
                nullSafe(a.getStatus()),
                formatDateTime(a.getCheckInTime()),
                formatDateTime(a.getCheckOutTime()),
                nullSafe(a.getTotalWorkingHours())
        }).collect(Collectors.toList());

        return render("Attendance Report - " + today, headers, rows, format);
    }

    public byte[] exportLeave(String format) {
        List<LeaveRequest> records = leaveRequestRepository.findAll();

        String[] headers = {"Employee ID", "Leave Type", "Start Date", "End Date", "Days", "Status", "Reason", "Approved By"};
        List<String[]> rows = records.stream().map(lr -> new String[]{
                nullSafe(lr.getEmployeeId()),
                nullSafe(lr.getLeaveType()),
                nullSafe(lr.getStartDate()),
                nullSafe(lr.getEndDate()),
                lr.getTotalDays() != null ? String.valueOf(lr.getTotalDays()) : "",
                nullSafe(lr.getStatus()),
                nullSafe(lr.getReason()),
                nullSafe(lr.getApprovedBy())
        }).collect(Collectors.toList());

        return render("Leave Requests Report", headers, rows, format);
    }

    public byte[] exportRecruitment(String format) {
        List<Job> jobs = jobRepository.findAll();
        List<JobApplication> apps = jobApplicationService.getAllApplications();

        Map<Long, Long> appCountByJobId = apps.stream()
                .filter(a -> a.getJob() != null)
                .collect(Collectors.groupingBy(a -> a.getJob().getId(), Collectors.counting()));

        String[] headers = {"Job Title", "Department", "Location", "Job Type", "Experience", "Deadline", "Active", "Applications"};
        List<String[]> rows = jobs.stream().map(j -> new String[]{
                nullSafe(j.getTitle()),
                nullSafe(j.getDepartment()),
                nullSafe(j.getLocation()),
                nullSafe(j.getJobType()),
                nullSafe(j.getExperienceLevel()),
                nullSafe(j.getApplicationDeadline()),
                Boolean.TRUE.equals(j.getActive()) ? "Yes" : "No",
                String.valueOf(appCountByJobId.getOrDefault(j.getId(), 0L))
        }).collect(Collectors.toList());

        return render("Recruitment Report", headers, rows, format);
    }

    public byte[] exportHolidays(String format) {
        List<Holiday> holidays = holidayRepository.findAll().stream()
                .sorted(Comparator.comparing(Holiday::getHolidayDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());

        String[] headers = {"Holiday Name", "Date", "Type", "Description"};
        List<String[]> rows = holidays.stream().map(h -> new String[]{
                nullSafe(h.getHolidayName()),
                nullSafe(h.getHolidayDate()),
                nullSafe(h.getHolidayType()),
                nullSafe(h.getDescription())
        }).collect(Collectors.toList());

        return render("Company Holidays", headers, rows, format);
    }

    /** Covers both "onboarding checklist" and "documents uploaded by employees" -
     *  in this codebase they're the same OnboardingDocument table. */
    public byte[] exportDocuments(String format) {
        List<OnboardingDocument> docs = onboardingDocumentRepository.findAll();

        String[] headers = {"Employee ID", "Employee Name", "Document", "Status", "Mandatory", "Submitted", "Verified By"};
        List<String[]> rows = docs.stream().map(d -> new String[]{
                d.getEmployee() != null ? nullSafe(d.getEmployee().getEmployeeId()) : "",
                d.getEmployee() != null ? nullSafe(d.getEmployee().getEmployeeName()) : "",
                nullSafe(d.getDocumentName()),
                nullSafe(d.getStatus()),
                d.isMandatory() ? "Yes" : "No",
                formatDateTime(d.getSubmittedDate()),
                nullSafe(d.getVerifiedBy())
        }).collect(Collectors.toList());

        return render("Employee Documents Report", headers, rows, format);
    }

    // ===================================================================
    // FORMAT DISPATCH
    // ===================================================================

    private byte[] render(String title, String[] headers, List<String[]> rows, String format) {
        String fmt = format == null ? "excel" : format.toLowerCase().trim();
        try {
            return switch (fmt) {
                case "csv" -> toCsv(headers, rows);
                case "pdf" -> toPdf(title, headers, rows);
                case "docx", "word" -> toDocx(title, headers, rows);
                default -> toExcel(title, headers, rows);
            };
        } catch (Exception e) {
            logger.error("Failed to render {} report as {}: {}", title, fmt, e.getMessage(), e);
            throw new RuntimeException("Failed to generate " + fmt + " file: " + e.getMessage(), e);
        }
    }

    private byte[] toCsv(String[] headers, List<String[]> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", escapeCsvRow(headers))).append("\n");
        for (String[] row : rows) {
            sb.append(String.join(",", escapeCsvRow(row))).append("\n");
        }
        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private String[] escapeCsvRow(String[] row) {
        String[] out = new String[row.length];
        for (int i = 0; i < row.length; i++) {
            String v = row[i] == null ? "" : row[i];
            if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
                v = "\"" + v.replace("\"", "\"\"") + "\"";
            }
            out[i] = v;
        }
        return out;
    }

    private byte[] toExcel(String title, String[] headers, List<String[]> rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(safeSheetName(title));

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }

            int r = 1;
            for (String[] row : rows) {
                Row dataRow = sheet.createRow(r++);
                for (int i = 0; i < row.length; i++) {
                    dataRow.createCell(i).setCellValue(row[i] == null ? "" : row[i]);
                }
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            workbook.write(bos);
            return bos.toByteArray();
        }
    }

    private byte[] toPdf(String title, String[] headers, List<String[]> rows) throws Exception {
        StringBuilder html = new StringBuilder();
        html.append("<html><body style='font-family:Helvetica,Arial,sans-serif;font-size:11px;'>");
        html.append("<h2 style='color:#215c89;'>").append(escapeHtml(title)).append("</h2>");
        html.append("<table style='border-collapse:collapse;width:100%;' border='1' cellpadding='5'>");
        html.append("<tr style='background:#215c89;color:#fff;'>");
        for (String h : headers) {
            html.append("<th>").append(escapeHtml(h)).append("</th>");
        }
        html.append("</tr>");
        for (String[] row : rows) {
            html.append("<tr>");
            for (String cell : row) {
                html.append("<td>").append(escapeHtml(cell)).append("</td>");
            }
            html.append("</tr>");
        }
        html.append("</table></body></html>");

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        HtmlConverter.convertToPdf(html.toString(), bos);
        return bos.toByteArray();
    }
// ===================================================================
// EMPLOYEE-SCOPED EXPORTS (same render() engine, filtered to one employeeId)
// ===================================================================

    public byte[] exportMyAttendance(String employeeId, String format) {
        List<Attendance> records = attendanceRepository.findAll().stream()
                .filter(a -> employeeId.equals(a.getEmployeeId()))
                .sorted(Comparator.comparing(Attendance::getAttendanceDate).reversed())
                .collect(Collectors.toList());

        String[] headers = {"Date", "Status", "Check-In", "Check-Out", "Working Hours"};
        List<String[]> rows = records.stream().map(a -> new String[]{
                nullSafe(a.getAttendanceDate()),
                nullSafe(a.getStatus()),
                formatDateTime(a.getCheckInTime()),
                formatDateTime(a.getCheckOutTime()),
                nullSafe(a.getTotalWorkingHours())
        }).collect(Collectors.toList());

        return render("My Attendance - " + employeeId, headers, rows, format);
    }

    public byte[] exportMyLeaves(String employeeId, String format) {
        List<LeaveRequest> records = leaveRequestRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId);

        String[] headers = {"Leave Type", "Start Date", "End Date", "Days", "Status", "Reason"};
        List<String[]> rows = records.stream().map(lr -> new String[]{
                nullSafe(lr.getLeaveType()),
                nullSafe(lr.getStartDate()),
                nullSafe(lr.getEndDate()),
                lr.getTotalDays() != null ? String.valueOf(lr.getTotalDays()) : "",
                nullSafe(lr.getStatus()),
                nullSafe(lr.getReason())
        }).collect(Collectors.toList());

        return render("My Leave History - " + employeeId, headers, rows, format);
    }

    public byte[] exportMyDocuments(String employeeId, String format) {
        List<OnboardingDocument> docs = onboardingDocumentRepository.findAll().stream()
                .filter(d -> d.getEmployee() != null && employeeId.equals(d.getEmployee().getEmployeeId()))
                .collect(Collectors.toList());

        String[] headers = {"Document", "Status", "Mandatory", "Submitted"};
        List<String[]> rows = docs.stream().map(d -> new String[]{
                nullSafe(d.getDocumentName()),
                nullSafe(d.getStatus()),
                d.isMandatory() ? "Yes" : "No",
                formatDateTime(d.getSubmittedDate())
        }).collect(Collectors.toList());

        return render("My Documents - " + employeeId, headers, rows, format);
    }
    private byte[] toDocx(String title, String[] headers, List<String[]> rows) throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph titlePara = doc.createParagraph();
            XWPFRun titleRun = titlePara.createRun();
            titleRun.setText(title);
            titleRun.setBold(true);
            titleRun.setFontSize(16);

            XWPFTable table = doc.createTable(rows.size() + 1, headers.length);
            for (int i = 0; i < headers.length; i++) {
                table.getRow(0).getCell(i).setText(headers[i]);
            }
            for (int r = 0; r < rows.size(); r++) {
                for (int c = 0; c < headers.length; c++) {
                    String val = c < rows.get(r).length ? rows.get(r)[c] : "";
                    table.getRow(r + 1).getCell(c).setText(val == null ? "" : val);
                }
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            doc.write(bos);
            return bos.toByteArray();
        }
    }

    // ===================================================================
    // SMALL HELPERS
    // ===================================================================

    private String nullSafe(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private String formatDateTime(java.time.LocalDateTime dt) {
        return dt == null ? "" : dt.format(DT_FMT);
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String safeSheetName(String title) {
        String name = title.replaceAll("[\\\\/*\\[\\]:?]", " ");
        return name.length() > 31 ? name.substring(0, 31) : name;
    }
}
