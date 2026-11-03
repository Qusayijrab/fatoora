package com.fatoora.invoicing.portal;

import com.fatoora.invoicing.pdf.DocumentPdfService.PdfFile;
import com.fatoora.invoicing.portal.PortalService.PortalView;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

public final class PortalControllers {

    private PortalControllers() {}

    static ResponseEntity<byte[]> pdf(PdfFile file) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(file.filename()).build().toString())
                .body(file.content());
    }

    /** Logged-in: share or revoke a client's portal link. */
    @RestController
    static class ClientPortalLinkController {
        private final PortalService portal;

        ClientPortalLinkController(PortalService portal) {
            this.portal = portal;
        }

        @PostMapping("/api/v1/clients/{id}/portal-link")
        Map<String, String> link(@PathVariable UUID id) {
            return Map.of("url", portal.portalLink(id));
        }

        @DeleteMapping("/api/v1/clients/{id}/portal-link")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        void revoke(@PathVariable UUID id) {
            portal.revokePortalLink(id);
        }
    }

    /** Public: the client's view. The token is the permission. */
    @RestController
    static class PublicPortalController {
        private final PortalService portal;

        PublicPortalController(PortalService portal) {
            this.portal = portal;
        }

        @GetMapping("/api/v1/public/portal/{token}")
        PortalView view(@PathVariable String token) {
            return portal.view(token);
        }

        @GetMapping(value = "/api/v1/public/portal/{token}/invoices/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
        ResponseEntity<byte[]> invoicePdf(@PathVariable String token, @PathVariable UUID id) {
            return pdf(portal.invoicePdf(token, id));
        }

        @GetMapping(value = "/api/v1/public/portal/{token}/quotes/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
        ResponseEntity<byte[]> quotePdf(@PathVariable String token, @PathVariable UUID id) {
            return pdf(portal.quotePdf(token, id));
        }

        @PostMapping("/api/v1/public/portal/{token}/quotes/{id}/accept")
        Map<String, String> accept(@PathVariable String token, @PathVariable UUID id) {
            return Map.of("status", portal.answerQuote(token, id, true).name());
        }

        @PostMapping("/api/v1/public/portal/{token}/quotes/{id}/reject")
        Map<String, String> reject(@PathVariable String token, @PathVariable UUID id) {
            return Map.of("status", portal.answerQuote(token, id, false).name());
        }
    }
}
