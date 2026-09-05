package com.moodbuds.customer.api;

import static com.moodbuds.customer.api.CustomerDtos.*;

import com.moodbuds.customer.CurrentCustomer;
import com.moodbuds.customer.CustomerAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customer/auth")
@Tag(name = "Customer Authentication")
public class CustomerAuthController {
    private final CustomerAuthService auth;

    public CustomerAuthController(CustomerAuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a customer with email and password")
    AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return auth.register(request);
    }

    @PostMapping("/login")
    @Operation(summary = "Login a customer with email and password")
    AuthResponse login(@Valid @RequestBody CustomerLoginRequest request) {
        return auth.login(request);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate a refresh token and issue a new access token")
    AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return auth.refresh(request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke the current customer session")
    void logout(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody LogoutRequest request) {
        auth.logout(CurrentCustomer.id(jwt), CurrentCustomer.sessionId(jwt), request);
    }

    @PostMapping("/logout-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke all refresh sessions for the customer")
    void logoutAll(@AuthenticationPrincipal Jwt jwt) {
        auth.logoutAll(CurrentCustomer.id(jwt));
    }
}
