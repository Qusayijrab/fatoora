package com.fatoora.invoicing.auth;

import com.fatoora.invoicing.auth.dto.LoginRequest;
import com.fatoora.invoicing.auth.dto.RegisterRequest;
import com.fatoora.invoicing.auth.dto.TokenResponse;
import com.fatoora.invoicing.common.error.ApiException;
import com.fatoora.invoicing.organization.Membership;
import com.fatoora.invoicing.organization.MembershipRepository;
import com.fatoora.invoicing.organization.OrganizationService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final String INVALID_CREDENTIALS = "Invalid email or password";

    private final UserRepository users;
    private final MembershipRepository memberships;
    private final OrganizationService organizations;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /**
     * Hash compared against when the email doesn't exist, so "unknown email" and
     * "wrong password" take the same time and can't be told apart.
     */
    private final String dummyHash;
    private final EmailVerificationService verification;
    private final RefreshTokenService refreshTokens;

    public AuthService(
            UserRepository users,
            MembershipRepository memberships,
            OrganizationService organizations,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            EmailVerificationService verification,
            RefreshTokenService refreshTokens) {
        this.verification = verification;
        this.refreshTokens = refreshTokens;
        this.users = users;
        this.memberships = memberships;
        this.organizations = organizations;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    /** Creates user + organization + OWNER membership in one transaction: all or nothing. */
    @Transactional
    public TokenResponse register(RegisterRequest request) {
        String email = User.normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw ApiException.conflict("An account with this email already exists");
        }
        // Flushed now: the verification token below is written with plain SQL and references this row.
        User user = users.saveAndFlush(new User(email, passwordEncoder.encode(request.password()), request.fullName()));
        Membership owner = organizations.createWithOwner(
                user, request.organizationName(), request.defaultCurrency(), request.timeZone());
        verification.sendLink(user);
        memberships.flush(); // the refresh token row (plain SQL) references the new organization
        return session(user.getId(), owner.getOrganization().getId());
    }

    /** Logs in to the user's oldest organization; use switch-org to change. */
    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = users.findByEmail(User.normalizeEmail(request.email())).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), dummyHash);
            throw ApiException.unauthorized(INVALID_CREDENTIALS);
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw ApiException.unauthorized(INVALID_CREDENTIALS);
        }
        List<Membership> mine = memberships.findAllForUser(user.getId());
        UUID defaultOrg = mine.isEmpty() ? null : mine.getFirst().getOrganization().getId();
        return session(user.getId(), defaultOrg);
    }

    /**
     * New token for another org. Same 403 whether the org doesn't exist or the user
     * isn't a member, so this can't be used to probe for other orgs.
     */
    @Transactional
    public TokenResponse switchOrganization(UUID userId, UUID organizationId) {
        if (!memberships.existsInOrganization(organizationId, userId)) {
            throw ApiException.forbidden("You are not a member of this organization");
        }
        return session(userId, organizationId);
    }

    /**
     * Trades a refresh token for a new access token and refresh token (rotation). Not
     * transactional itself: rotate() commits its revocations even when it answers 401.
     */
    public TokenResponse refresh(String refreshToken) {
        var next = refreshTokens.rotate(refreshToken);
        return jwtService.issue(next.userId(), next.organizationId())
                .withRefreshToken(next.refreshToken(), RefreshTokenService.LIFETIME.toSeconds());
    }

    /** An access token plus a new refresh-token family. */
    private TokenResponse session(UUID userId, UUID organizationId) {
        return jwtService.issue(userId, organizationId)
                .withRefreshToken(refreshTokens.start(userId, organizationId), RefreshTokenService.LIFETIME.toSeconds());
    }
}
