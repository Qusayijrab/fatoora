package com.fatoora.invoicing.auth;

import com.fatoora.invoicing.auth.dto.MeResponse;
import com.fatoora.invoicing.common.error.ApiException;
import com.fatoora.invoicing.common.tenancy.TenantContext;
import com.fatoora.invoicing.organization.OrganizationRepository;
import com.fatoora.invoicing.organization.dto.OrganizationResponse;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final UserRepository users;
    private final OrganizationRepository organizations;
    private final EmailVerificationService verification;

    public MeController(UserRepository users, OrganizationRepository organizations, EmailVerificationService verification) {
        this.users = users;
        this.organizations = organizations;
        this.verification = verification;
    }

    /** The logged-in user and their active organization (null if they have none). */
    @GetMapping
    @Transactional(readOnly = true)
    public MeResponse me() {
        User user = users.findById(CurrentUser.id()).orElseThrow(() -> ApiException.unauthorized("Unknown user"));
        OrganizationResponse org = TenantContext.current()
                .flatMap(t -> organizations.findById(t.organizationId()).map(o -> OrganizationResponse.from(o, t.role())))
                .orElse(null);
        return new MeResponse(user.getId(), user.getEmail(), user.getFullName(), user.isEmailVerified(), org);
    }

    /** A fresh confirmation link (the banner's "Resend"). */
    @PostMapping("/verification-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resendVerification() {
        verification.resend(CurrentUser.id());
    }
}
