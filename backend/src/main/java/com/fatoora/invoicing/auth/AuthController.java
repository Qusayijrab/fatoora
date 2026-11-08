package com.fatoora.invoicing.auth;

import com.fatoora.invoicing.auth.dto.LoginRequest;
import com.fatoora.invoicing.auth.dto.RegisterRequest;
import com.fatoora.invoicing.auth.dto.TokenResponse;
import com.fatoora.invoicing.common.ratelimit.RateLimiter;
import com.fatoora.invoicing.common.ratelimit.RateLimits;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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

    public AuthController(AuthService authService, RateLimiter limiter, RateLimits limits) {
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

    @PostMapping("/switch-org/{organizationId}")
    public TokenResponse switchOrganization(@PathVariable UUID organizationId) {
        return authService.switchOrganization(CurrentUser.id(), organizationId);
    }
}
