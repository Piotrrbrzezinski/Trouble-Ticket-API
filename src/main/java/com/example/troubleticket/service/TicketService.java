package com.example.troubleticket.service;

import com.example.troubleticket.dto.Models.*;
import com.example.troubleticket.entity.Ticket;
import com.example.troubleticket.entity.TicketNote;
import com.example.troubleticket.exception.ApiException;
import com.example.troubleticket.repository.NoteRepository;
import com.example.troubleticket.repository.ServiceRepository;
import com.example.troubleticket.repository.TicketRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class TicketService {
    private final TicketRepository tickets;
    private final NoteRepository notes;
    private final ServiceRepository services;
    private final TransactionTemplate transactions;
    private final Clock clock;

    @Autowired
    public TicketService(TicketRepository tickets, NoteRepository notes, ServiceRepository services,
                         org.springframework.transaction.PlatformTransactionManager manager) {
        this(tickets, notes, services, new TransactionTemplate(manager), Clock.systemUTC());
    }

    // Package-visible clock injection is useful for deterministic unit tests.
    TicketService(TicketRepository tickets, NoteRepository notes, ServiceRepository services,
                  TransactionTemplate transactions, Clock clock) {
        this.tickets=tickets; this.notes=notes; this.services=services; this.transactions=transactions; this.clock=clock;
    }

    public record CreateResult(TicketResponse ticket, boolean created) {}

    public CreateResult create(String tenant, CreateRequest request) {
        if (!"new".equals(request.status())) throw validation("Create accepts only status 'new'");
        var existing = tickets.findByTenantIdAndExternalId(tenant, request.externalId());
        if (existing.isPresent()) return new CreateResult(toResponse(existing.get()), false);
        if (!services.existsActive(tenant, request.serviceId()))
            throw new ApiException(HttpStatus.NOT_FOUND,"SERVICE_NOT_FOUND", "Service not found or unavailable");
        try {
            // Unique (tenant_id, external_id) is the concurrency-safe idempotency guarantee.
            Ticket created = transactions.execute(status -> {
                var ticket = new Ticket(UUID.randomUUID().toString(), tenant, request.externalId(),
                    request.serviceId(), request.description(), "acknowledged", clock.instant());
                tickets.saveAndFlush(ticket);
                notes.saveAndFlush(new TicketNote(UUID.randomUUID().toString(), ticket.id(), request.note(), clock.instant()));
                return ticket;
            });
            return new CreateResult(toResponse(created), true);
        } catch (DataIntegrityViolationException duplicateOrForeignKeyFailure) {
            // TransactionTemplate has already rolled back; read in a fresh transaction.
            var concurrentlyCreated = tickets.findByTenantIdAndExternalId(tenant, request.externalId());
            if (concurrentlyCreated.isPresent()) return new CreateResult(toResponse(concurrentlyCreated.get()), false);
            // Other integrity violations must not be mislabeled as idempotent retries.
            throw duplicateOrForeignKeyFailure;
        }
    }

    @Transactional(readOnly=true)
    public List<TicketSummary> list(String tenant) {
        return tickets.findByTenantIdOrderByCreatedAtAscIdAsc(tenant).stream()
            .map(t -> new TicketSummary(t.externalId(),t.serviceId(),t.description(),t.status())).toList();
    }

    @Transactional(readOnly=true)
    public TicketResponse get(String tenant, String id) { return toResponse(owned(tenant,id)); }

    @Transactional
    public TicketResponse close(String tenant, String id, CloseRequest request) {
        if (!"closed".equals(request.status())) throw validation("Patch accepts only status 'closed'");
        Ticket ticket=owned(tenant,id);
        ticket.close();
        tickets.save(ticket);
        return toResponse(ticket);
    }

    @Transactional
    public NoteResponse addNote(String tenant, String id, NoteRequest request) {
        Ticket ticket=owned(tenant,id);
        return toResponse(notes.saveAndFlush(new TicketNote(UUID.randomUUID().toString(), ticket.id(),
                request.text(), clock.instant())));
    }

    private Ticket owned(String tenant, String id) {
        return tickets.findByIdAndTenantId(id,tenant).orElseThrow(() ->
            new ApiException(HttpStatus.NOT_FOUND,"TROUBLE_TICKET_NOT_FOUND","Trouble Ticket not found"));
    }
    private TicketResponse toResponse(Ticket t) {
        return new TicketResponse(t.id(),t.externalId(),t.serviceId(),t.description(),t.status(),
            notes.findByTicketIdOrderByCreatedAtAscIdAsc(t.id()).stream().map(this::toResponse).toList());
    }
    private NoteResponse toResponse(TicketNote n) { return new NoteResponse(n.id(),n.text(),n.createdAt()); }
    private ApiException validation(String message) { return new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR",message); }
}
