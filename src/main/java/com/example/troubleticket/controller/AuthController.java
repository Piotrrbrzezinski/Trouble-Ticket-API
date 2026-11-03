package com.example.troubleticket.controller;

import com.example.troubleticket.dto.Models.LoginRequest;
import com.example.troubleticket.dto.Models.LoginResponse;
import com.example.troubleticket.exception.ApiException;
import com.example.troubleticket.repository.UserAccountRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private static final long TOKEN_LIFETIME_SECONDS = 3600;
    private final AuthenticationManager authenticationManager;
    private final JwtEncoder jwtEncoder;
    private final UserAccountRepository users;
    private final String issuer;

    public AuthController(AuthenticationManager authenticationManager, JwtEncoder jwtEncoder,
                          UserAccountRepository users,
                          @Value("${app.jwt.issuer}") String issuer) {
        this.authenticationManager = authenticationManager;
        this.jwtEncoder = jwtEncoder;
        this.users = users;
        this.issuer = issuer;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        try {
            var authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));
            var user = users.findByUsername(authentication.getName())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid username or password"));
            var now = Instant.now();
            var claims = JwtClaimsSet.builder()
                .issuer(issuer).subject(authentication.getName())
                .issuedAt(now).expiresAt(now.plusSeconds(TOKEN_LIFETIME_SECONDS))
                .claim("tenant_id", user.tenantId)
                .claim("scope", "ticket:read ticket:write")
                .build();
            var token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims));
            return new LoginResponse(token.getTokenValue(), "Bearer", TOKEN_LIFETIME_SECONDS);
        } catch (AuthenticationException e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid username or password");
        }
    }
}
