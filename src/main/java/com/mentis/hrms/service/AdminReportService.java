package com.mentis.hrms.service;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.UnitValue;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Generic tabular report generator used by AdminAIController's report/download
 * feature. Takes a title, column headers, and rows of string data, and can
 * render them into any of the four formats the project already has libraries
 * for: CSV (no library needed), Excel (.xlsx via Apache POI), PDF (via iText,
 * already in pom.xml), and Word (.docx via Apache POI).
 *
 * "Print" isn't a separate file format — the PDF output is what you hand the
 * browser to trigger its native print dialog, so generatePdf() covers that
 * case too.
 */
@Service
public class AdminReportService {

    private static final Logger logger = LoggerFactory.getLogger(AdminReportService.class);

    public enum Format { CSV, EXCEL, PDF, WORD }

    public static class GeneratedReport {
        public final byte[] content;
        public final String contentType;
        public final String fileName;

        public GeneratedReport(byte[] content, String contentType, String fileName) {
            this.content = content;
            this.contentType = contentType;
            this.fileName = fileName;
        }
    }

    public GeneratedReport generate(Format format, String title, List<String> headers, List<List<String>> rows) throws IOException {
        String safeTitle = title.replaceAll("[^a-zA-Z0-9\\-_]", "_");
        switch (format) {
            case CSV:
                return new GeneratedReport(generateCsv(headers, rows), "text/csv", safeTitle + ".csv");
            case EXCEL:
                return new GeneratedReport(generateExcel(title, headers, rows), "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", safeTitle + ".xlsx");
            case WORD:
                return new GeneratedReport(generateDocx(title, headers, rows), "application/vnd.openxmlformats-officedocument.wordprocessingml.document", safeTitle + ".docx");
            case PDF:
            default:
                return new GeneratedReport(generatePdf(title, headers, rows), "application/pdf", safeTitle + ".pdf");
        }
    }

    // ────────────────────────────────────────────────────────────────
    // CSV
    // ────────────────────────────────────────────────────────────────
    private byte[] generateCsv(List<String> headers, List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", escapeCsvRow(headers))).append("\n");
        for (List<String> row : rows) {
            sb.append(String.join(",", escapeCsvRow(row))).append("\n");
        }
        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private List<String> escapeCsvRow(List<String> row) {
        return row.stream()
                .map(v -> v == null ? "" : v.replace("\"", "\"\""))
                .map(v -> v.contains(",") || v.contains("\"") || v.contains("\n") ? "\"" + v + "\"" : v)
                .collect(java.util.stream.Collectors.toList());
    }

    // ────────────────────────────────────────────────────────────────
    // EXCEL (.xlsx)
    // ────────────────────────────────────────────────────────────────
    private byte[] generateExcel(String title, List<String> headers, List<List<String>> rows) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(safeSheetName(title));

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                // FIX: Use fully qualified name to avoid conflict with iText Cell
                org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (List<String> rowData : rows) {
                Row row = sheet.createRow(rowIdx++);
                for (int i = 0; i < rowData.size(); i++) {
                    // FIX: Use fully qualified name to avoid conflict with iText Cell
                    org.apache.poi.ss.usermodel.Cell cell = row.createCell(i);
                    cell.setCellValue(rowData.get(i) == null ? "" : rowData.get(i));
                }
            }

            for (int i = 0; i < headers.size(); i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }

    private String safeSheetName(String title) {
        String name = title.length() > 31 ? title.substring(0, 31) : title;
        return name.replaceAll("[\\\\/*?\\[\\]:]", "_");
    }

    // ────────────────────────────────────────────────────────────────
    // PDF (also used for "print" — browser prints the PDF)
    // ────────────────────────────────────────────────────────────────
    private byte[] generatePdf(String title, List<String> headers, List<List<String>> rows) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             PdfWriter writer = new PdfWriter(out);
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document document = new Document(pdfDoc)) {

            document.add(new Paragraph(title).setBold().setFontSize(16));
            document.add(new Paragraph(" "));

            Table table = new Table(UnitValue.createPercentArray(headers.size())).useAllAvailableWidth();
            for (String h : headers) {
                table.addHeaderCell(new Cell().add(new Paragraph(h).setBold()));
            }
            for (List<String> row : rows) {
                for (String value : row) {
                    table.addCell(new Cell().add(new Paragraph(value == null ? "" : value)));
                }
            }
            document.add(table);
            document.close();
            return out.toByteArray();
        }
    }

    // ────────────────────────────────────────────────────────────────
    // WORD (.docx)
    // ────────────────────────────────────────────────────────────────
    private byte[] generateDocx(String title, List<String> headers, List<List<String>> rows) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFParagraph titlePara = doc.createParagraph();
            XWPFRun titleRun = titlePara.createRun();
            titleRun.setBold(true);
            titleRun.setFontSize(16);
            titleRun.setText(title);

            XWPFTable table = doc.createTable(rows.size() + 1, headers.size());
            for (int i = 0; i < headers.size(); i++) {
                table.getRow(0).getCell(i).setText(headers.get(i));
            }
            for (int r = 0; r < rows.size(); r++) {
                List<String> rowData = rows.get(r);
                XWPFTableRow tableRow = table.getRow(r + 1);
                for (int c = 0; c < rowData.size(); c++) {
                    tableRow.getCell(c).setText(rowData.get(c) == null ? "" : rowData.get(c));
                }
            }

            doc.write(out);
            return out.toByteArray();
        }
    }
}
