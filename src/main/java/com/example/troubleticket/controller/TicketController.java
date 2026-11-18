package com.example.troubleticket.controller;

import com.example.troubleticket.dto.Models.*;
import com.example.troubleticket.service.TicketService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/troubleTicket")
public class TicketController {
    private final TicketService service;

    public TicketController(TicketService service) {
        this.service = service;
    }

    private String tenant(Jwt jwt) {
        return jwt.getClaimAsString("tenant_id");
    }

    @PostMapping
    public ResponseEntity<TicketResponse> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateRequest request) {
        var result = service.create(tenant(jwt), request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri().path("/{id}").buildAndExpand(result.ticket().id()).toUri();
        return ResponseEntity.status(result.created() ? 201 : 200).location(location).body(result.ticket());
    }

    @GetMapping
    public List<TicketSummary> list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(tenant(jwt));
    }

    @GetMapping("/{id}")
    public TicketResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        return service.get(tenant(jwt), id);
    }

    @PatchMapping("/{id}")
    public TicketResponse close(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
                                @Valid @RequestBody CloseRequest request) {
        return service.close(tenant(jwt), id, request);
    }

    @PostMapping("/{id}/note")
    public ResponseEntity<NoteResponse> note(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
                                             @Valid @RequestBody NoteRequest request) {
        return ResponseEntity.status(201).body(service.addNote(tenant(jwt), id, request));
    }
}
