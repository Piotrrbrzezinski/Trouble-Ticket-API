package com.example.troubleticket.security;

import com.example.troubleticket.dto.Models.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.util.UUID;
import com.example.troubleticket.repository.UserAccountRepository;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    UserDetailsService userDetailsService(UserAccountRepository users) {
        return username -> users.findByUsername(username)
            .filter(user -> user.enabled)
            .map(user -> User.withUsername(user.username)
                .password(user.passwordHash)
                .authorities("SCOPE_ticket:read", "SCOPE_ticket:write")
                .build())
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService users, PasswordEncoder encoder) {
        var provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    @Bean
    JwtEncoder jwtEncoder(@Value("${app.jwt.secret}") String secret) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtKey(secret)));
    }

    private SecretKeySpec jwtKey(String secret) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32)
            throw new IllegalArgumentException("JWT_SECRET must be at least 32 bytes for HS256");
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    JwtDecoder jwtDecoder(@Value("${app.jwt.secret}") String secret, @Value("${app.jwt.issuer}") String issuer) {
        var decoder=NimbusJwtDecoder.withSecretKey(jwtKey(secret))
            .macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> standard=JwtValidators.createDefaultWithIssuer(issuer);
        OAuth2TokenValidator<Jwt> tenant=jwt -> {
            Object id=jwt.getClaims().get("tenant_id");
            return id instanceof String value && !value.isBlank()
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token","Missing tenant_id",null));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(standard,tenant));
        return decoder;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, ObjectMapper mapper) throws Exception {
        JwtGrantedAuthoritiesConverter scopes=new JwtGrantedAuthoritiesConverter();
        JwtAuthenticationConverter converter=new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(scopes);
        AuthenticationEntryPoint unauthorized=(request,response,e)->send(response,401,"UNAUTHORIZED","Missing or invalid Bearer token",mapper,request);
        AccessDeniedHandler forbidden=(request,response,e)->send(response,403,"FORBIDDEN","Insufficient permissions",mapper,request);
        http.csrf(csrf->csrf.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a->a
                .requestMatchers(HttpMethod.POST,"/api/v1/auth/login").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/v1/troubleTicket","/api/v1/troubleTicket/*").hasAuthority("SCOPE_ticket:read")
                .requestMatchers(HttpMethod.POST,"/api/v1/troubleTicket","/api/v1/troubleTicket/*/note").hasAuthority("SCOPE_ticket:write")
                .requestMatchers(HttpMethod.PATCH,"/api/v1/troubleTicket/*").hasAuthority("SCOPE_ticket:write")
                .anyRequest().denyAll())
            .oauth2ResourceServer(o->o.jwt(jwt->jwt.jwtAuthenticationConverter(converter))
                .authenticationEntryPoint(unauthorized).accessDeniedHandler(forbidden))
            .exceptionHandling(e->e.authenticationEntryPoint(unauthorized).accessDeniedHandler(forbidden));
        return http.build();
    }
    private static void send(jakarta.servlet.http.HttpServletResponse response,int status,String code,String message,
                             ObjectMapper mapper,jakarta.servlet.http.HttpServletRequest request) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Object id=request.getAttribute("requestId");
        mapper.writeValue(response.getOutputStream(),new ErrorResponse(code,message,id==null?UUID.randomUUID().toString():id.toString()));
    }
}
