package com.mentis.hrms.controller;

import com.mentis.hrms.service.HrExportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/export/employee")
public class EmployeeExportController {

    @Autowired
    private HrExportService hrExportService;

    @GetMapping("/attendance")
    public ResponseEntity<byte[]> attendance(@RequestParam(defaultValue = "xlsx") String format, Principal principal) {
        return respond(hrExportService.exportMyAttendance(principal.getName(), format), "attendance", format);
    }

    @GetMapping("/leaves")
    public ResponseEntity<byte[]> leaves(@RequestParam(defaultValue = "xlsx") String format, Principal principal) {
        return respond(hrExportService.exportMyLeaves(principal.getName(), format), "leave-history", format);
    }

    @GetMapping("/documents")
    public ResponseEntity<byte[]> documents(@RequestParam(defaultValue = "xlsx") String format, Principal principal) {
        return respond(hrExportService.exportMyDocuments(principal.getName(), format), "documents", format);
    }

    private ResponseEntity<byte[]> respond(byte[] bytes, String baseName, String format) {
        String fmt = format.toLowerCase();
        MediaType type = switch (fmt) {
            case "csv" -> MediaType.parseMediaType("text/csv");
            case "pdf" -> MediaType.APPLICATION_PDF;
            case "docx", "word" -> MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            default -> MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        };
        String ext = switch (fmt) { case "docx", "word" -> "docx"; case "csv" -> "csv"; case "pdf" -> "pdf"; default -> "xlsx"; };
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + baseName + "." + ext + "\"")
                .body(bytes);
    }
}
