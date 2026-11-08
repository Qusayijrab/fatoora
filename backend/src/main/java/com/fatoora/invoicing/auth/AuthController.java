package com.fatoora.invoicing.auth;

import com.fatoora.invoicing.auth.dto.LoginRequest;
import com.fatoora.invoicing.auth.dto.RegisterRequest;
import com.fatoora.invoicing.auth.dto.TokenResponse;
import com.fatoora.invoicing.common.ratelimit.RateLimiter;
import com.fatoora.invoicing.common.ratelimit.RateLimits;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import com.fatoora.invoicing.common.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final RateLimiter limiter;
    private final RateLimits limits;
    private final EmailVerificationService verification;
    private final RefreshTokenService refreshTokens;
    private final PasswordResetService passwordResets;

    public AuthController(AuthService authService, RateLimiter limiter, RateLimits limits,
            EmailVerificationService verification, RefreshTokenService refreshTokens,
            PasswordResetService passwordResets) {
        this.passwordResets = passwordResets;
        this.verification = verification;
        this.refreshTokens = refreshTokens;
        this.authService = authService;
        this.limiter = limiter;
        this.limits = limits;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        limiter.hit("register-ip", RateLimits.clientIp(http), limits.registrationsPerIpPerHour(), Duration.ofHours(1),
                "Too many sign-ups from this network. Try again later.");
        return authService.register(request);
    }

    /** Limited per network and per account, so password guessing is slow either way. */
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        limiter.hit("login-ip", RateLimits.clientIp(http), limits.loginsPerIpPer10Minutes(), Duration.ofMinutes(10),
                "Too many sign-in attempts. Wait a few minutes and try again.");
        limiter.hit("login-email", RateLimits.emailKey(request.email()), limits.loginsPerEmailPer15Minutes(),
                Duration.ofMinutes(15), "Too many sign-in attempts for this account. Wait a few minutes and try again.");
        return authService.login(request);
    }

    /** The link from the confirmation email (public: the token is the permission). */
    @PostMapping("/verify-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verifyEmail(@Valid @RequestBody VerifyEmailRequest request, HttpServletRequest http) {
        limiter.hit("verify-ip", RateLimits.clientIp(http), 30, Duration.ofMinutes(10),
                "Too many attempts. Wait a few minutes and try again.");
        verification.verify(request.token());
    }

    public record VerifyEmailRequest(@NotBlank @Size(max = 100) String token) {}

    public record RefreshRequest(@NotBlank @Size(max = 100) String refreshToken) {}

    public record ForgotPasswordRequest(@NotBlank @Email @Size(max = 254) String email) {}

    public record ResetPasswordRequest(
            @NotBlank @Size(max = 100) String token,
            @NotNull @Size(min = 8) @MaxUtf8Bytes(72) String password) {

        @Override
        public String toString() {
            return "ResetPasswordRequest[]";
        }
    }

    /** Emails a reset link if the address has an account; always 204, so it reveals nothing. */
    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request, HttpServletRequest http) {
        passwordResets.requestLink(request.email(), RateLimits.clientIp(http));
    }

    /** Sets a new password with the link from the email; signs out every session. */
    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request, HttpServletRequest http) {
        limiter.hit("reset-use-ip", RateLimits.clientIp(http), 30, Duration.ofMinutes(10),
                "Too many attempts. Wait a few minutes and try again.");
        passwordResets.reset(request.token(), request.password());
    }

    /** A new access token and refresh token for a refresh token (public: the token is the permission). */
    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    /** Ends this device's session: its refresh token chain stops working. */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request) {
        refreshTokens.revoke(request.refreshToken());
    }

    @PostMapping("/switch-org/{organizationId}")
    public TokenResponse switchOrganization(@PathVariable UUID organizationId) {
        return authService.switchOrganization(CurrentUser.id(), organizationId);
    }
}
