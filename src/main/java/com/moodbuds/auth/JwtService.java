package com.moodbuds.auth;

import java.time.Instant;
import java.util.ArrayList;

import com.moodbuds.config.SecurityProperties;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final JwtEncoder encoder;
    private final SecurityProperties properties;

    public JwtService(JwtEncoder encoder, SecurityProperties properties) {
        this.encoder = encoder;
        this.properties = properties;
    }

    public IssuedToken issue(AdminPrincipal principal) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.tokenValidity());
        var authorities = new ArrayList<>(principal.authorities());
        authorities.add("ROLE_" + principal.role());
        authorities.add("ADMIN_ACCESS");
        var claims = JwtClaimsSet.builder()
                .issuer("moodbuds-api")
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(principal.username())
                .claim("adminId", principal.id())
                .claim("role", principal.role())
                .claim("tokenType", "ADMIN")
                .claim("authorities", authorities)
                .build();
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        return new IssuedToken(encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue(), expiresAt);
    }

    public IssuedToken issueCustomer(long customerId, String email, String sessionId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.customerAccessTokenValidity());
        var claims = JwtClaimsSet.builder()
                .issuer("moodbuds-api")
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(email)
                .id(sessionId)
                .claim("customerId", customerId)
                .claim("sessionId", sessionId)
                .claim("role", "CUSTOMER")
                .claim("tokenType", "CUSTOMER")
                .claim("authorities", java.util.List.of("CUSTOMER_ACCESS", "ROLE_CUSTOMER"))
                .build();
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        return new IssuedToken(encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue(), expiresAt);
    }

    public record IssuedToken(String value, Instant expiresAt) {}
}
