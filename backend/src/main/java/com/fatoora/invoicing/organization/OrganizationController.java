package com.fatoora.invoicing.organization;

import com.fatoora.invoicing.auth.CurrentUser;
import com.fatoora.invoicing.organization.dto.CreateOrganizationRequest;
import com.fatoora.invoicing.organization.dto.OrganizationResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations")
public class OrganizationController {

    private final OrganizationService service;

    public OrganizationController(OrganizationService service) {
        this.service = service;
    }

    /** Every org the caller belongs to, with their role in each. */
    @GetMapping
    public List<OrganizationResponse> mine() {
        return service.listForUser(CurrentUser.id());
    }

    /** The org selected in the caller's token. */
    @GetMapping("/current")
    public OrganizationResponse current() {
        return service.current();
    }

    /**
     * Creates another org with the caller as OWNER. Call POST /auth/switch-org/{id}
     * afterwards to work inside it.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrganizationResponse create(@Valid @RequestBody CreateOrganizationRequest request) {
        return service.createForUser(CurrentUser.id(), request.name(), request.defaultCurrency(), request.timeZone());
    }
}
