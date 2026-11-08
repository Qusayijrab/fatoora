package com.fatoora.invoicing.report;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Aging and tax summary, as JSON for the reports page and as CSV for the accountant. */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/aging")
    public ReportService.Aging aging() {
        return reports.aging();
    }

    @GetMapping(value = "/aging.csv", produces = "text/csv")
    public ResponseEntity<byte[]> agingCsv() {
        return csv("aging.csv", reports.agingCsv());
    }

    @GetMapping("/tax")
    public ReportService.TaxSummary tax(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reports.tax(from, to);
    }

    @GetMapping(value = "/tax.csv", produces = "text/csv")
    public ResponseEntity<byte[]> taxCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        var s = reports.tax(from, to);
        return csv("tax-" + s.from() + "-to-" + s.to() + ".csv", reports.taxCsv(from, to));
    }

    private static ResponseEntity<byte[]> csv(String filename, String body) {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(body.getBytes(StandardCharsets.UTF_8));
    }
}
