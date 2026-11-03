package com.fatoora.invoicing.quote;

import com.fatoora.invoicing.common.PageResponse;
import com.fatoora.invoicing.quote.QuoteDtos.QuoteRequest;
import com.fatoora.invoicing.quote.QuoteDtos.QuoteResponse;
import com.fatoora.invoicing.quote.QuoteDtos.QuoteSummary;
import com.fatoora.invoicing.mail.DocumentEmailService;
import com.fatoora.invoicing.mail.DocumentEmailService.EmailRequest;
import com.fatoora.invoicing.mail.DocumentEmailService.EmailResult;
import com.fatoora.invoicing.pdf.DocumentPdfService;
import com.fatoora.invoicing.pdf.DocumentPdfService.PdfFile;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/quotes")
public class QuoteController {

    private final QuoteService service;
    private final DocumentPdfService pdfs;
    private final DocumentEmailService emails;

    public QuoteController(QuoteService service, DocumentPdfService pdfs, DocumentEmailService emails) {
        this.service = service;
        this.pdfs = pdfs;
        this.emails = emails;
    }

    @GetMapping
    public PageResponse<QuoteSummary> list(
            @RequestParam(required = false) QuoteStatus status,
            @RequestParam(required = false) UUID clientId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return service.list(status, clientId, q, page, size);
    }

    @GetMapping("/{id}")
    public QuoteResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public QuoteResponse create(@Valid @RequestBody QuoteRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public QuoteResponse update(@PathVariable UUID id, @Valid @RequestBody QuoteRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.deleteDraft(id);
    }

    @PostMapping("/{id}/send")
    public QuoteResponse send(@PathVariable UUID id) {
        return service.send(id);
    }

    @PostMapping("/{id}/accept")
    public QuoteResponse accept(@PathVariable UUID id) {
        return service.accept(id);
    }

    @PostMapping("/{id}/reject")
    public QuoteResponse reject(@PathVariable UUID id) {
        return service.reject(id);
    }

    /** Returns {"invoiceId": "..."} for the new invoice draft. */
    @PostMapping("/{id}/convert")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, UUID> convert(@PathVariable UUID id) {
        return Map.of("invoiceId", service.convertToInvoice(id));
    }

    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable UUID id) {
        PdfFile file = pdfs.quotePdf(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(file.filename()).build().toString())
                .body(file.content());
    }

    @PostMapping("/{id}/email")
    public EmailResult email(@PathVariable UUID id, @Valid @RequestBody(required = false) EmailRequest request) {
        return emails.emailQuote(id, request == null ? new EmailRequest(null, null) : request);
    }
}
