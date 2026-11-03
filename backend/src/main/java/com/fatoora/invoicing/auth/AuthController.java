package com.fatoora.invoicing.auth;

import com.fatoora.invoicing.auth.dto.LoginRequest;
import com.fatoora.invoicing.auth.dto.RegisterRequest;
import com.fatoora.invoicing.auth.dto.TokenResponse;
import jakarta.validation.Valid;
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

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/switch-org/{organizationId}")
    public TokenResponse switchOrganization(@PathVariable UUID organizationId) {
        return authService.switchOrganization(CurrentUser.id(), organizationId);
    }
}
