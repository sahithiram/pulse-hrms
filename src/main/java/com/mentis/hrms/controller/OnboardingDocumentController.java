package com.mentis.hrms.controller;

import com.mentis.hrms.model.Employee;
import com.mentis.hrms.model.OnboardingDocument;
import com.mentis.hrms.service.DocumentService;
import com.mentis.hrms.service.EmployeeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.RequestContextUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Sprint 8 (REQ-8-05) — all document-handling endpoints split out of NewOnboardingController.
 * URLs preserved 1:1 under /onboarding.
 */
@Controller
@RequestMapping("/onboarding")
public class OnboardingDocumentController {
    private static final Logger logger = LoggerFactory.getLogger(OnboardingDocumentController.class);

    @Autowired
    private DocumentService documentService;

    @Autowired
    private EmployeeService employeeService;

    @Value("${app.upload.base-path:C:/hrms/uploads}")
    private String basePath;

    private static final long MAX_UPLOAD_BYTES = 5L * 1024 * 1024;
    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList("pdf", "jpg", "jpeg", "png");

    private void addSidebarContext(Model model, HttpSession session) {
        String userRole = session != null ? (String) session.getAttribute("userRole") : null;
        String userName = session != null ? (String) session.getAttribute("userName") : null;
        boolean isSuperAdmin = "SUPER_ADMIN".equalsIgnoreCase(userRole) || "ADMIN".equalsIgnoreCase(userRole);
        boolean isHR = "HR".equalsIgnoreCase(userRole);
        model.addAttribute("isSuperAdmin", isSuperAdmin);
        model.addAttribute("isHR", isHR);
        model.addAttribute("userName", (userName != null && !userName.isEmpty()) ? userName : "HR Admin");
    }

    /* ---------- GET DOCUMENT DETAILS (API ENDPOINT) ---------- */
    @GetMapping("/document/{documentId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getDocumentDetails(@PathVariable Long documentId) {
        Map<String, Object> response = new HashMap<>();

        try {
            Optional<OnboardingDocument> documentOpt = documentService.getDocumentRepository().findById(documentId);

            if (documentOpt.isEmpty()) {
                response.put("success", false);
                response.put("error", "Document not found: " + documentId);
                return ResponseEntity.badRequest().body(response);
            }

            OnboardingDocument document = documentOpt.get();
            Map<String, Object> docData = new HashMap<>();
            docData.put("id", document.getId());
            docData.put("documentName", document.getDocumentName());
            docData.put("documentType", document.getDocumentType());
            docData.put("status", document.getStatus());
            docData.put("description", document.getDescription());
            docData.put("mandatory", document.isMandatory());
            docData.put("submittedDate", document.getSubmittedDate());
            docData.put("verifiedDate", document.getVerifiedDate());
            docData.put("verifiedBy", document.getVerifiedBy());
            docData.put("verificationNotes", document.getVerificationNotes());
            docData.put("filePath", document.getFilePath());

            response.put("success", true);
            response.put("document", docData);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error getting document details {}: {}", documentId, e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /* ---------- UPLOAD FORM ---------- */
    @GetMapping("/employee/{employeeId}/upload/{documentType}")
    public String showUploadForm(@PathVariable String employeeId,
                                 @PathVariable String documentType,
                                 Model model,
                                 HttpSession session,
                                 HttpServletRequest request) {
        logger.info("=== SHOW UPLOAD FORM FOR EMPLOYEE: {}, DOCUMENT: {} ===", employeeId, documentType);
        addSidebarContext(model, session);

        Optional<Employee> employeeOpt = employeeService.getEmployeeByEmployeeId(employeeId);
        if (employeeOpt.isPresent()) {
            Employee employee = employeeOpt.get();
            logger.info("Employee found: {} {}", employee.getFirstName(), employee.getLastName());

            String documentName = getDocumentDisplayName(documentType);

            model.addAttribute("employee", employee);
            model.addAttribute("documentType", documentType);
            model.addAttribute("documentName", documentName);

            Map<String, ?> flashMap = RequestContextUtils.getInputFlashMap(request);
            if (flashMap != null) {
                if (flashMap.containsKey("success")) {
                    model.addAttribute("success", flashMap.get("success"));
                }
                if (flashMap.containsKey("error")) {
                    model.addAttribute("error", flashMap.get("error"));
                }
            }

            return "onboarding/upload-document";
        }

        logger.error("Employee not found: {}", employeeId);
        return "redirect:/onboarding?error=Employee+not+found";
    }

    /* ---------- UPLOAD DOCUMENT (Handles both Form and AJAX) ---------- */
    @PostMapping("/employee/{employeeId}/upload/{documentType}")
    public Object uploadDocument(@PathVariable String employeeId,
                                 @PathVariable String documentType,
                                 @RequestParam("documentFile") MultipartFile file,
                                 @RequestParam(value = "notes", required = false) String notes,
                                 HttpSession session,
                                 HttpServletRequest request,
                                 org.springframework.web.servlet.mvc.support.RedirectAttributes ra) {

        logger.info("=== UPLOAD DOCUMENT REQUEST ===");
        logger.info("Employee ID: {}, Document Type: {}, File Size: {} bytes",
                employeeId, documentType, file.getSize());

        String requestedWith = request.getHeader("X-Requested-With");
        boolean isAjax = "XMLHttpRequest".equals(requestedWith);

        try {
            if (file == null || file.isEmpty()) {
                logger.warn("No file selected for upload");
                if (isAjax) {
                    return ResponseEntity.badRequest().body(Map.of(
                            "success", false,
                            "error", "Please select a file to upload"));
                }
                ra.addFlashAttribute("error", "Please select a file to upload");
                return "redirect:/onboarding/employee/" + employeeId + "/upload/" + documentType;
            }

            if (file.getSize() > MAX_UPLOAD_BYTES) {
                logger.warn("File size exceeds 5MB limit");
                if (isAjax) {
                    return ResponseEntity.badRequest().body(Map.of(
                            "success", false,
                            "error", "File size exceeds 5MB limit"));
                }
                ra.addFlashAttribute("error", "File size exceeds 5MB limit. Please select a smaller file.");
                return "redirect:/onboarding/employee/" + employeeId + "/upload/" + documentType;
            }

            String fileName = file.getOriginalFilename();
            if (fileName != null && fileName.contains(".")) {
                String fileExtension = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
                if (!ALLOWED_EXTENSIONS.contains(fileExtension)) {
                    if (isAjax) {
                        return ResponseEntity.badRequest().body(Map.of(
                                "success", false,
                                "error", "Invalid file format. Only PDF, JPG, JPEG, PNG allowed."));
                    }
                    ra.addFlashAttribute("error", "Invalid file format. Only PDF, JPG, JPEG, PNG files are allowed.");
                    return "redirect:/onboarding/employee/" + employeeId + "/upload/" + documentType;
                }
            }

            Optional<Employee> employeeOpt = employeeService.getEmployeeByEmployeeId(employeeId);
            if (employeeOpt.isEmpty()) {
                if (isAjax) {
                    return ResponseEntity.badRequest().body(Map.of(
                            "success", false,
                            "error", "Employee not found: " + employeeId));
                }
                ra.addFlashAttribute("error", "Employee not found: " + employeeId);
                return "redirect:/onboarding";
            }

            String hrUserName = (String) session.getAttribute("hrUserName");
            if (hrUserName == null || hrUserName.isEmpty()) {
                hrUserName = (String) session.getAttribute("userName");
            }
            if (hrUserName == null || hrUserName.isEmpty()) {
                logger.warn("HR user not found in session, using default");
                hrUserName = "SYSTEM_HR";
            }

            OnboardingDocument document = documentService.hrUploadDocument(
                    employeeId, documentType, file, notes, hrUserName);

            logger.info("Document uploaded and auto-verified: ID={}, Type={}", document.getId(), documentType);

            if (isAjax) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "documentId", document.getId(),
                        "documentName", document.getDocumentName(),
                        "status", document.getStatus(),
                        "message", "Document uploaded and verified by " + hrUserName + "!"));
            }

            ra.addFlashAttribute("success", "Document uploaded and verified by " + hrUserName + "!");
            return "redirect:/dashboard/hr/onboarding/employee/" + employeeId;

        } catch (Exception e) {
            logger.error("Upload failed: {}", e.getMessage(), e);
            if (isAjax) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("success", false, "error", e.getMessage()));
            }
            ra.addFlashAttribute("error", "Upload failed: " + e.getMessage());
            return "redirect:/onboarding/employee/" + employeeId + "/upload/" + documentType;
        }
    }

    /* ---------- HR UPLOAD DOCUMENT (REAL-TIME) ---------- */
    @PostMapping("/hr/upload/{employeeId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> hrUploadDocument(
            @PathVariable String employeeId,
            @RequestParam("documentType") String documentType,
            @RequestParam("documentFile") MultipartFile file,
            @RequestParam(value = "notes", required = false) String notes,
            @RequestParam("uploadedBy") String uploadedBy) {

        Map<String, Object> response = new HashMap<>();

        try {
            OnboardingDocument document = documentService.hrUploadDocument(
                    employeeId, documentType, file, notes, uploadedBy);

            response.put("success", true);
            response.put("message", "Document uploaded and verified by HR!");
            response.put("document", document);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("HR upload failed: {}", e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /* ---------- HELPER METHOD FOR DOCUMENT DISPLAY NAMES ---------- */
    private String getDocumentDisplayName(String documentType) {
        Map<String, String> documentNames = new HashMap<>();
        documentNames.put("PAN_CARD", "PAN Card");
        documentNames.put("AADHAAR_CARD", "Aadhaar Card");
        documentNames.put("PASSPORT", "Passport");
        documentNames.put("DRIVING_LICENSE", "Driving License");
        documentNames.put("VOTER_ID", "Voter ID");
        documentNames.put("OFFER_LETTER", "Signed Offer Letter");
        documentNames.put("APPOINTMENT_LETTER", "Appointment Letter");
        documentNames.put("RELIEVING_LETTER", "Relieving Letter");
        documentNames.put("EXPERIENCE_LETTERS", "Experience Letters");
        documentNames.put("TENTH_MARKSHEET", "10th Marksheet");
        documentNames.put("TWELFTH_MARKSHEET", "12th Marksheet");
        documentNames.put("DEGREE_CERTIFICATE", "Degree Certificate");
        documentNames.put("POST_GRADUATION", "Post Graduation");
        documentNames.put("BANK_ACCOUNT", "Bank Account Details");
        documentNames.put("PAN_CARD_COPY", "PAN Card Copy");
        documentNames.put("PASSPORT_PHOTO", "Passport Photo");
        documentNames.put("SIGNATURE", "Digital Signature");
        documentNames.put("MEDICAL_CERTIFICATE", "Medical Certificate");
        documentNames.put("NDA", "Non-Disclosure Agreement");
        documentNames.put("POLICY_ACKNOWLEDGMENT", "Policy Acknowledgment");

        return documentNames.getOrDefault(documentType, documentType.replace("_", " "));
    }

    /* ---------- VERIFY/REJECT DOCUMENT (FLEXIBLE HR AUTH) ---------- */
    @PostMapping("/verify/{documentId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> verifyDocumentWithMessage(
            @PathVariable Long documentId,
            @RequestParam String verifiedBy,
            @RequestParam String status,
            @RequestParam(required = false) String remarks,
            HttpSession session) {

        Map<String, Object> response = new HashMap<>();

        String backupUserId = (String) session.getAttribute("userId");
        String backupUserRole = (String) session.getAttribute("userRole");
        String backupUserName = (String) session.getAttribute("userName");
        logger.debug("Session backed up before verification - Role: {}", backupUserRole);

        try {
            String hrUserName = (String) session.getAttribute("userName");
            String actualVerifier = (hrUserName != null && !hrUserName.isEmpty())
                    ? hrUserName
                    : "HR_ADMIN";

            OnboardingDocument document = documentService.verifyDocumentWithMessage(
                    documentId, actualVerifier, status, remarks);

            Map<String, Object> docMap = new HashMap<>();
            docMap.put("id", document.getId());
            docMap.put("documentName", document.getDocumentName());
            docMap.put("status", document.getStatus());
            docMap.put("verifiedBy", document.getVerifiedBy());
            docMap.put("verifiedDate", document.getVerifiedDate());
            docMap.put("verificationNotes", document.getVerificationNotes());

            if (document.getEmployee() != null) {
                docMap.put("employeeId", document.getEmployee().getEmployeeId());
            }

            response.put("success", true);
            response.put("message", "Document " + status.toLowerCase() + " by " + actualVerifier);
            response.put("document", docMap);

            if (session.getAttribute("userId") == null && backupUserId != null) {
                logger.warn("Session corrupted during verification - RESTORING HR attributes");
                session.setAttribute("userId", backupUserId);
                session.setAttribute("userRole", backupUserRole);
                session.setAttribute("userName", backupUserName);
            }

            logger.info("Document {} verified successfully, HR session preserved", documentId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Verification failed: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("error", e.getMessage());

            if (session.getAttribute("userId") == null && backupUserId != null) {
                session.setAttribute("userId", backupUserId);
                session.setAttribute("userRole", backupUserRole);
                session.setAttribute("userName", backupUserName);
            }

            return ResponseEntity.badRequest().body(response);
        }
    }

    /* ---------- GET DOCUMENTS LIST FOR EMPLOYEE (JSON API) ---------- */
    @GetMapping("/employee/{employeeId}/documents-list")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getDocumentsList(@PathVariable String employeeId) {
        Map<String, Object> response = new HashMap<>();

        try {
            Optional<Employee> employeeOpt = employeeService.getEmployeeByEmployeeId(employeeId);

            if (employeeOpt.isEmpty()) {
                response.put("success", false);
                response.put("error", "Employee not found");
                return ResponseEntity.badRequest().body(response);
            }

            Employee employee = employeeOpt.get();

            List<OnboardingDocument> documents = documentService.getDocumentsByEmployee(employee);

            if (documents.isEmpty()) {
                documentService.initializeDocumentChecklist(employee);
                documents = documentService.getDocumentsByEmployee(employee);
            }

            List<Map<String, Object>> docsList = documents.stream().map(doc -> {
                Map<String, Object> docMap = new HashMap<>();
                docMap.put("id", doc.getId());
                docMap.put("documentType", doc.getDocumentType());
                docMap.put("documentName", doc.getDocumentName());
                docMap.put("description", doc.getDescription());
                docMap.put("mandatory", doc.isMandatory());
                docMap.put("status", doc.getStatus());
                docMap.put("submittedDate", doc.getSubmittedDate());
                docMap.put("filePath", doc.getFilePath());
                return docMap;
            }).collect(Collectors.toList());

            response.put("success", true);
            response.put("documents", docsList);
            response.put("employeeId", employeeId);
            response.put("employeeName", employee.getFirstName() + " " + employee.getLastName());
            response.put("totalDocuments", documents.size());
            response.put("submittedDocuments",
                    documents.stream().filter(d -> "SUBMITTED".equals(d.getStatus()) || "VERIFIED".equals(d.getStatus())).count());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error getting documents list for {}: {}", employeeId, e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /* ---------- GET DOCUMENTS FRAGMENT (FOR AJAX REFRESH) ---------- */
    @GetMapping("/employee/{employeeId}/documents-fragment")
    @ResponseBody
    public String getDocumentsFragment(@PathVariable String employeeId) {
        try {
            Optional<Employee> employeeOpt = employeeService.getEmployeeByEmployeeId(employeeId);
            if (employeeOpt.isEmpty()) return "";

            Employee employee = employeeOpt.get();
            List<OnboardingDocument> documents = documentService.getDocumentsByEmployee(employee);

            StringBuilder html = new StringBuilder();
            int count = 0;
            for (OnboardingDocument doc : documents) {
                count++;
                html.append("<tr>")
                        .append("<td>").append(count).append("</td>")
                        .append("<td>")
                        .append("<div class='doc-name-wrapper'>")
                        .append("<div class='doc-name'>").append(doc.getDocumentName()).append("</div>")
                        .append("<div><span class='doc-type'>").append(doc.getDocumentType()).append("</span>");

                if (doc.isMandatory()) {
                    html.append("<span class='doc-mandatory'>*</span>");
                }

                html.append("</div></div></td>")
                        .append("<td><span class='status-badge status-").append(doc.getStatus().toLowerCase()).append("'>")
                        .append(doc.getStatus()).append("</span></td>")
                        .append("<td>");

                if (doc.getSubmittedDate() != null) {
                    html.append("<div class='datetime-wrapper'>")
                            .append("<div class='date-row'><i class='fas fa-calendar'></i>")
                            .append("<span>").append(doc.getSubmittedDate().format(DateTimeFormatter.ofPattern("dd MMM yyyy"))).append("</span></div>")
                            .append("<div class='time-row'><i class='fas fa-clock'></i>")
                            .append("<span>").append(doc.getSubmittedDate().format(DateTimeFormatter.ofPattern("HH:mm:ss"))).append("</span></div>")
                            .append("</div>");
                } else {
                    html.append("<i class='fas fa-minus'></i>");
                }

                html.append("</td><td><div class='action-buttons'>");

                if (doc.getFilePath() != null) {
                    html.append("<button class='btn-icon btn-icon-view' onclick='viewDocument(\"")
                            .append(doc.getId()).append("\", \"").append(doc.getDocumentType()).append("\")' title='View'>")
                            .append("<i class='fas fa-eye'></i></button>");
                }

                html.append("<button class='btn-icon btn-icon-details' onclick='viewDetails(this)' data-id='")
                        .append(doc.getId()).append("' title='Details'><i class='fas fa-info'></i></button>");

                if ("PENDING".equals(doc.getStatus()) || "REJECTED".equals(doc.getStatus())) {
                    html.append("<a href='/onboarding/employee/").append(employeeId).append("/upload/")
                            .append(doc.getDocumentType()).append("' class='btn-icon btn-icon-upload' title='Upload'>")
                            .append("<i class='fas fa-upload'></i></a>");
                }

                if (doc.getFilePath() != null) {
                    html.append("<a href='/onboarding/employee/").append(employeeId).append("/download/")
                            .append(doc.getId()).append("' class='btn-icon btn-icon-download' download title='Download'>")
                            .append("<i class='fas fa-download'></i></a>");
                }

                if ("SUBMITTED".equals(doc.getStatus())) {
                    html.append("<button class='btn-icon btn-icon-verify' onclick=\"verifyDoc('")
                            .append(doc.getId()).append("', 'VERIFIED')\" title='Verify'><i class='fas fa-check'></i></button>");
                    html.append("<button class='btn-icon btn-icon-reject' onclick=\"verifyDoc('")
                            .append(doc.getId()).append("', 'REJECTED')\" title='Reject'><i class='fas fa-times'></i></button>");
                }

                if ("VERIFIED".equals(doc.getStatus())) {
                    html.append("<span class='btn-icon' style='color:#10b981; pointer-events:none;' title='Verified'>")
                            .append("<i class='fas fa-check-double'></i></span>");
                }

                html.append("</div></td></tr>");
            }

            return html.toString();
        } catch (Exception e) {
            logger.error("Error generating documents fragment", e);
            return "<tr><td colspan='5' style='text-align: center; padding: 20px; color: #ef4444;'><i class='fas fa-exclamation-triangle'></i> Error loading documents</td></tr>";
        }
    }

    /* ---------- DOWNLOAD DOCUMENT ---------- */
    @GetMapping("/employee/{employeeId}/download/{documentId}")
    @ResponseBody
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> downloadEmployeeDocument(
            @PathVariable String employeeId,
            @PathVariable Long documentId) {

        return hrDownloadDocument(documentId);
    }

    @GetMapping("/download/{documentId}")
    @ResponseBody
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> hrDownloadDocument(@PathVariable Long documentId) {
        try {
            logger.info("HR downloading document: {}", documentId);

            OnboardingDocument document = documentService.getDocumentById(documentId);

            if (document == null || document.getFilePath() == null || document.getFilePath().trim().isEmpty()) {
                logger.error("Document or file path not found: {}", documentId);
                return ResponseEntity.notFound().build();
            }

            String rawPath = document.getFilePath().trim();
            Path candidate = Paths.get(rawPath);
            Path filePath = (candidate.isAbsolute() && Files.exists(candidate))
                    ? candidate
                    : Paths.get(basePath).resolve(rawPath.replaceFirst("^[/\\\\]+", ""));

            if (!Files.exists(filePath)) {
                logger.error("File not found: {}", filePath);
                return ResponseEntity.notFound().build();
            }

            byte[] fileContent = Files.readAllBytes(filePath);

            String fileName = filePath.getFileName().toString();
            String extension = getFileExtension(fileName);
            String empId = (document.getEmployee() != null && document.getEmployee().getEmployeeId() != null)
                    ? document.getEmployee().getEmployeeId() : "EMP";
            String downloadName = document.getDocumentType() + "_" + empId + (extension.isEmpty() ? "" : "." + extension);

            String contentType = Files.probeContentType(filePath);
            if (contentType == null) {
                contentType = determineContentType(fileName);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(contentType));
            headers.setContentDisposition(ContentDisposition.attachment().filename(downloadName).build());
            headers.setContentLength(fileContent.length);
            headers.setCacheControl("no-cache, no-store, must-revalidate");

            logger.info("HR successfully prepared download: {} ({})", downloadName, contentType);
            return new ResponseEntity<>(fileContent, headers, HttpStatus.OK);

        } catch (Exception e) {
            logger.error("Error HR-downloading document {}: {}", documentId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("Error downloading file: " + e.getMessage()).getBytes());
        }
    }

    /* ---------- HELPER METHODS FOR DOWNLOAD ---------- */
    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) return "";
        return fileName.substring(fileName.lastIndexOf(".") + 1);
    }

    private String determineContentType(String fileName) {
        String ext = getFileExtension(fileName).toLowerCase();
        return switch (ext) {
            case "pdf" -> "application/pdf";
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "txt" -> "text/plain";
            default -> "application/octet-stream";
        };
    }
}
