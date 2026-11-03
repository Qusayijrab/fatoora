package com.fatoora.invoicing.invoice;

import com.fatoora.invoicing.common.PageResponse;
import com.fatoora.invoicing.invoice.InvoiceDtos.CreditNoteRequest;
import com.fatoora.invoicing.invoice.InvoiceDtos.InvoiceRequest;
import com.fatoora.invoicing.invoice.InvoiceDtos.InvoiceResponse;
import com.fatoora.invoicing.invoice.InvoiceDtos.InvoiceSummary;
import com.fatoora.invoicing.invoice.InvoiceDtos.PaymentRequest;
import com.fatoora.invoicing.invoice.InvoiceDtos.VoidRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
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

/** Thin HTTP layer: every rule lives in InvoiceService and the Invoice entity. */
@RestController
@RequestMapping("/api/v1/invoices")
public class InvoiceController {

    private final InvoiceService service;

    public InvoiceController(InvoiceService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<InvoiceSummary> list(
            @RequestParam(defaultValue = "INVOICE") InvoiceKind kind,
            @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(required = false) UUID clientId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return service.list(kind, status, clientId, q, page, size);
    }

    @GetMapping("/{id}")
    public InvoiceResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InvoiceResponse create(@Valid @RequestBody InvoiceRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public InvoiceResponse update(@PathVariable UUID id, @Valid @RequestBody InvoiceRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.deleteDraft(id);
    }

    @PostMapping("/{id}/issue")
    public InvoiceResponse issue(@PathVariable UUID id) {
        return service.issue(id);
    }

    @PostMapping("/{id}/payments")
    public InvoiceResponse recordPayment(@PathVariable UUID id, @Valid @RequestBody PaymentRequest request) {
        return service.recordPayment(id, request);
    }

    @PostMapping("/{id}/void")
    public InvoiceResponse voidInvoice(@PathVariable UUID id, @Valid @RequestBody VoidRequest request) {
        return service.voidInvoice(id, request.reason());
    }

    @PostMapping("/{id}/credit-notes")
    public InvoiceResponse creditNote(@PathVariable UUID id, @Valid @RequestBody CreditNoteRequest request) {
        return service.createCreditNote(id, request);
    }
}
