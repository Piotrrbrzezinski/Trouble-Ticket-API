package com.example.troubleticket;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

abstract class TroubleTicketApiIT {
    private static final String SECRET = "integration-test-secret-minimum-32-bytes-long";
    private static final long ACTIVE_SERVICE = 987654321L;

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtDecoder jwtDecoder;

    @BeforeEach
    void prepare() {
        jdbc.update("DELETE FROM app_users");
        jdbc.update("DELETE FROM ticket_notes");
        jdbc.update("DELETE FROM trouble_tickets");
        jdbc.update("DELETE FROM customer_services");
        jdbc.update("INSERT INTO app_users (username, password_hash, tenant_id, enabled) VALUES (?, ?, ?, true)",
                "User", "$2b$10$n1I8yzFFgei1ZYitXby71e1F/H2onzdqD3gVf9OWimPT1sT4c3c7.", "tenant-a");
        jdbc.update("INSERT INTO app_users (username, password_hash, tenant_id, enabled) VALUES (?, ?, ?, true)",
                "Alice", "$2b$10$5OAtg0AL7rgS8GhL/uAS4ef9XpjePqk2yrnSXCOamJSdJqXCw5kJC", "tenant-a");
        jdbc.update("INSERT INTO app_users (username, password_hash, tenant_id, enabled) VALUES (?, ?, ?, true)",
                "Bob", "$2b$10$era7wP8KxmXDlLPj6tqrWO7Irrk1LMPkqq38peYNgeaUwUHQ6JuoS", "tenant-b");
        jdbc.update("INSERT INTO customer_services (tenant_id, id, active) VALUES (?, ?, true)", "tenant-a", ACTIVE_SERVICE);
        jdbc.update("INSERT INTO customer_services (tenant_id, id, active) VALUES (?, ?, true)", "tenant-b", ACTIVE_SERVICE);
        jdbc.update("INSERT INTO customer_services (tenant_id, id, active) VALUES (?, ?, false)", "tenant-a", 987654322L);
    }

    @Test
    void loginIssuesUsableTokenAndRejectsInvalidCredentials() throws Exception {
        String response = mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"User\",\"password\":\"user\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresIn").value(3600))
            .andReturn().getResponse().getContentAsString();
        String token = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).get("accessToken").asText();
        Jwt jwt = jwtDecoder.decode(token);
        assertEquals("User", jwt.getSubject());
        assertEquals("tenant-a", jwt.getClaimAsString("tenant_id"));
        assertEquals("ticket:read ticket:write", jwt.getClaimAsString("scope"));
        mvc.perform(get("/api/v1/troubleTicket").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk());
        mvc.perform(post("/api/v1/troubleTicket")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(createBody("logged-in-user")))
            .andExpect(status().isCreated());

        String otherTenantResponse = mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"Bob\",\"password\":\"bob-password\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String otherTenantToken = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(otherTenantResponse).get("accessToken").asText();
        assertEquals("Bob", jwtDecoder.decode(otherTenantToken).getSubject());
        assertEquals("tenant-b", jwtDecoder.decode(otherTenantToken).getClaimAsString("tenant_id"));

        for (String credentials : List.of(
                "{\"username\":\"User\",\"password\":\"wrong\"}",
                "{\"username\":\"Other\",\"password\":\"user\"}")) {
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createAndGetHaveCorrectShapeAndHeaders() throws Exception {
        String body = mvc.perform(post("/api/v1/troubleTicket")
                    .header(HttpHeaders.AUTHORIZATION, bearer("tenant-a", "ticket:write"))
                    .contentType(MediaType.APPLICATION_JSON).content(createBody("client-1")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/troubleTicket/")))
                .andExpect(header().exists("X-Request-ID"))
                .andExpect(jsonPath("$.status").value("acknowledged"))
                .andExpect(jsonPath("$.externalId").value("client-1"))
                .andExpect(jsonPath("$.notes", hasSize(1)))
                .andExpect(jsonPath("$.notes[0].text").value("Initial note"))
                .andExpect(jsonPath("$.notes[0].date").exists())
                .andReturn().getResponse().getContentAsString();
        String id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).get("id").asText();

        mvc.perform(get("/api/v1/troubleTicket/{id}", id)
                .header(HttpHeaders.AUTHORIZATION, bearer("tenant-a", "ticket:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void secondCreateIsIdempotentEvenIfPayloadDiffers() throws Exception {
        var first = mvc.perform(post("/api/v1/troubleTicket")
                .header(HttpHeaders.AUTHORIZATION, bearer("tenant-a", "ticket:write"))
                .contentType(MediaType.APPLICATION_JSON).content(createBody("client-2")))
                .andExpect(status().isCreated()).andReturn().getResponse();
        var second = mvc.perform(post("/api/v1/troubleTicket")
                .header(HttpHeaders.AUTHORIZATION, bearer("tenant-a", "ticket:write"))
                .contentType(MediaType.APPLICATION_JSON).content(createBody("client-2").replace("Initial note", "Ignored note")))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertEquals(first.getContentAsString(), second.getContentAsString());
        assertEquals(first.getHeader("Location"), second.getHeader("Location"));
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM trouble_tickets", Long.class));
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM ticket_notes", Long.class));
    }

    @Test
    void tenantIsolationAffectsListReadCloseAndNotes() throws Exception {
        String ticket = create("tenant-a", "private-ticket");
        mvc.perform(get("/api/v1/troubleTicket")
                .header(HttpHeaders.AUTHORIZATION, bearer("tenant-b", "ticket:read")))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/v1/troubleTicket/{id}", ticket)
                .header(HttpHeaders.AUTHORIZATION, bearer("tenant-b", "ticket:read")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("TROUBLE_TICKET_NOT_FOUND"));
        mvc.perform(patch("/api/v1/troubleTicket/{id}", ticket)
                .header(HttpHeaders.AUTHORIZATION, bearer("tenant-b", "ticket:write"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"closed\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/troubleTicket/{id}/note", ticket)
                .header(HttpHeaders.AUTHORIZATION, bearer("tenant-b", "ticket:write"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"Attempt\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/troubleTicket")
                .header(HttpHeaders.AUTHORIZATION, bearer("tenant-a", "ticket:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].externalId").value("private-ticket"))
                .andExpect(jsonPath("$[0].id").doesNotExist())
                .andExpect(jsonPath("$[0].notes").doesNotExist());
    }

    @Test
    void closeCanBeRepeatedAndNoteDoesNotChangeStatus() throws Exception {
        String ticket = create("tenant-a", "lifecycle");
        mvc.perform(patch("/api/v1/troubleTicket/{id}", ticket)
                .header(HttpHeaders.AUTHORIZATION, bearer("tenant-a", "ticket:write"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"inProgress\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        for (int i = 0; i < 2; i++) {
            mvc.perform(patch("/api/v1/troubleTicket/{id}", ticket)
                    .header(HttpHeaders.AUTHORIZATION, bearer("tenant-a", "ticket:write"))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"closed\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("closed"));
        }
        mvc.perform(post("/api/v1/troubleTicket/{id}/note", ticket)
                .header(HttpHeaders.AUTHORIZATION, bearer("tenant-a", "ticket:write"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"After closure\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.text").value("After closure"))
                .andExpect(jsonPath("$.date").exists());
        mvc.perform(get("/api/v1/troubleTicket/{id}", ticket)
                .header(HttpHeaders.AUTHORIZATION, bearer("tenant-a", "ticket:read")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("closed"))
                .andExpect(jsonPath("$.notes", hasSize(2)));
    }

    @Test
    void rejectsInvalidInputAndMissingServices() throws Exception {
        mvc.perform(post("/api/v1/troubleTicket")
                .header(HttpHeaders.AUTHORIZATION, bearer("tenant-a", "ticket:write"))
                .contentType(MediaType.APPLICATION_JSON).content(createBody("x").replace("\"new\"", "\"closed\"")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(post("/api/v1/troubleTicket")
                .header(HttpHeaders.AUTHORIZATION, bearer("tenant-a", "ticket:write"))
                .contentType(MediaType.APPLICATION_JSON).content(createBody("x").replace("\"note\":", "\"tenantId\":\"tenant-b\",\"note\":")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/troubleTicket")
                .header(HttpHeaders.AUTHORIZATION, bearer("tenant-a", "ticket:write"))
                .contentType(MediaType.APPLICATION_JSON).content(createBody("x").replace("987654321", "987654322")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SERVICE_NOT_FOUND"));
        mvc.perform(post("/api/v1/troubleTicket")
                .header(HttpHeaders.AUTHORIZATION, bearer("tenant-a", "ticket:write"))
                .contentType(MediaType.APPLICATION_JSON).content(createBody("x").replace("987654321", "12345")))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/troubleTicket")
                .header(HttpHeaders.AUTHORIZATION, bearer("tenant-a", "ticket:write"))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void enforcesJwtSignatureIssuerTenantAndScope() throws Exception {
        mvc.perform(get("/api/v1/troubleTicket"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
        mvc.perform(get("/api/v1/troubleTicket")
                .header(HttpHeaders.AUTHORIZATION, bearer("tenant-a", "ticket:write")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(get("/api/v1/troubleTicket")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + signed("tenant-a", "ticket:read", "unexpected-issuer")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/troubleTicket")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + signed(null, "ticket:read", "trouble-ticket-local")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void concurrentCreatesYieldExactlyOneTicketAndNote() throws Exception {
        int requests = 6;
        var begin = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(requests);
        try {
            List<Future<Integer>> statuses = new ArrayList<>();
            for (int i = 0; i < requests; i++) {
                statuses.add(pool.submit(() -> {
                    begin.await();
                    return mvc.perform(post("/api/v1/troubleTicket")
                            .header(HttpHeaders.AUTHORIZATION, bearer("tenant-a", "ticket:write"))
                            .contentType(MediaType.APPLICATION_JSON).content(createBody("race")))
                            .andReturn().getResponse().getStatus();
                }));
            }
            begin.countDown();
            var actual = new ArrayList<Integer>();
            for (Future<Integer> status : statuses) actual.add(status.get());
            assertEquals(1, actual.stream().filter(s -> s == 201).count());
            assertEquals(requests - 1, actual.stream().filter(s -> s == 200).count());
            assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM trouble_tickets", Long.class));
            assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM ticket_notes", Long.class));
        } finally {
            pool.shutdownNow();
        }
    }

    private String create(String tenant, String externalId) throws Exception {
        String response = mvc.perform(post("/api/v1/troubleTicket")
                .header(HttpHeaders.AUTHORIZATION, bearer(tenant, "ticket:write"))
                .contentType(MediaType.APPLICATION_JSON).content(createBody(externalId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).get("id").asText();
    }

    private String createBody(String externalId) {
        return "{\"externalId\":\"" + externalId + "\",\"serviceId\":987654321,"
                + "\"description\":\"Internet unavailable\",\"status\":\"new\",\"note\":\"Initial note\"}";
    }

    private String bearer(String tenant, String scope) {
        return "Bearer " + signed(tenant, scope, "trouble-ticket-local");
    }

    private String signed(String tenant, String scope, String issuer) {
        var key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        var now = Instant.now();
        var claims = JwtClaimsSet.builder()
                .issuer(issuer).subject("integration-client")
                .issuedAt(now).expiresAt(now.plusSeconds(3600))
                .claim("scope", scope);
        if (tenant != null) claims.claim("tenant_id", tenant);
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build()))
                .getTokenValue();
    }
}
