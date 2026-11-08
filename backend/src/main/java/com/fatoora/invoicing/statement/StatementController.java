package com.fatoora.invoicing.statement;

import com.fatoora.invoicing.pdf.DocumentPdfService.PdfFile;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** A client's statement of account as a PDF. Defaults: this year to today, in the client's currency. */
@RestController
public class StatementController {

    private final StatementService statements;

    public StatementController(StatementService statements) {
        this.statements = statements;
    }

    @GetMapping(value = "/api/v1/clients/{id}/statement", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> statement(@PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String currency) {
        PdfFile file = statements.pdf(id, from, to, currency);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(file.filename(),
                        java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .body(file.content());
    }
}
