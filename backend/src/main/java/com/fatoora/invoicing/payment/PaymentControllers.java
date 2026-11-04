package com.fatoora.invoicing.payment;

import com.fatoora.invoicing.payment.PaymentLinkService.MockCheckoutView;
import com.fatoora.invoicing.payment.PaymentLinkService.PublicInvoiceView;
import com.fatoora.invoicing.pdf.DocumentPdfService.PdfFile;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** HTTP endpoints for payments: one authenticated, the rest public (token or signature is the permission). */
public final class PaymentControllers {

    private PaymentControllers() {}

    /** Logged-in: get the shareable payment link of an invoice. */
    @RestController
    static class InvoicePaymentLinkController {
        private final PaymentLinkService service;

        InvoicePaymentLinkController(PaymentLinkService service) {
            this.service = service;
        }

        @PostMapping("/api/v1/invoices/{id}/payment-link")
        Map<String, String> paymentLink(@PathVariable UUID id) {
            return Map.of("url", service.linkForInvoice(id));
        }
    }

    /** Logged-in: online payments that were taken but could not be applied, until marked handled. */
    @RestController
    static class PaymentProblemController {
        private final PaymentProblemService service;

        PaymentProblemController(PaymentProblemService service) {
            this.service = service;
        }

        @GetMapping("/api/v1/payment-problems")
        List<PaymentProblemService.PaymentProblem> list() {
            return service.unresolved();
        }

        @PostMapping("/api/v1/payment-problems/{id}/resolve")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        void resolve(@PathVariable UUID id) {
            service.resolve(id);
        }
    }

    /** Public: the payer's view of one invoice, by token. */
    @RestController
    static class PublicPayController {
        private final PaymentLinkService service;

        PublicPayController(PaymentLinkService service) {
            this.service = service;
        }

        @GetMapping("/api/v1/public/pay/{token}")
        PublicInvoiceView view(@PathVariable String token) {
            return service.publicView(token);
        }

        @GetMapping(value = "/api/v1/public/pay/{token}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
        ResponseEntity<byte[]> pdf(@PathVariable String token) {
            PdfFile file = service.publicPdf(token);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(file.filename()).build().toString())
                    .body(file.content());
        }

        @PostMapping("/api/v1/public/pay/{token}/checkout")
        Map<String, String> checkout(@PathVariable String token) {
            return Map.of("url", service.startCheckout(token));
        }
    }

    /** Public: the mock provider's hosted checkout (development and demos only). */
    @RestController
    static class MockCheckoutController {
        private final PaymentLinkService service;
        private final WebhookService webhooks;

        MockCheckoutController(PaymentLinkService service, WebhookService webhooks) {
            this.service = service;
            this.webhooks = webhooks;
        }

        @GetMapping("/api/v1/public/mock-checkout/{sessionId}")
        MockCheckoutView view(@PathVariable String sessionId) {
            return service.mockCheckout(sessionId);
        }

        /** Simulates the provider: it signs a completion event and delivers it to our webhook pipeline. */
        @PostMapping("/api/v1/public/mock-checkout/{sessionId}/complete")
        Map<String, String> complete(@PathVariable String sessionId) {
            var event = service.mockComplete(sessionId);
            var outcome = webhooks.handle(MockPaymentGateway.NAME, event.payload(),
                    Map.of(MockPaymentGateway.SIGNATURE_HEADER, event.signatureHeader()));
            return Map.of("outcome", outcome.name(), "payToken", service.mockCheckout(sessionId).payToken());
        }
    }

    /** Public: provider webhooks. The raw body is needed to check the signature. */
    @RestController
    static class WebhookController {
        private final WebhookService webhooks;

        WebhookController(WebhookService webhooks) {
            this.webhooks = webhooks;
        }

        @PostMapping(value = "/api/v1/webhooks/{provider}", consumes = MediaType.ALL_VALUE)
        Map<String, String> receive(@PathVariable String provider, @RequestBody String payload,
                @RequestHeader Map<String, String> headers) {
            Map<String, String> lower = new HashMap<>();
            headers.forEach((k, v) -> lower.put(k.toLowerCase(Locale.ROOT), v));
            return Map.of("outcome", webhooks.handle(provider, payload, lower).name());
        }
    }
}
