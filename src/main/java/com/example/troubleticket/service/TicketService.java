package com.example.troubleticket.service;

import com.example.troubleticket.dto.Models.*;
import com.example.troubleticket.entity.Ticket;
import com.example.troubleticket.entity.TicketNote;
import com.example.troubleticket.exception.ApiException;
import com.example.troubleticket.repository.NoteRepository;
import com.example.troubleticket.repository.ServiceRepository;
import com.example.troubleticket.repository.TicketRepository;
import com.example.troubleticket.tools.TicketReader;
import com.example.troubleticket.tools.TicketStatusValidator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.List;

@Service
public class TicketService {
    private final TicketRepository tickets;
    private final NoteRepository notes;
    private final ServiceRepository services;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final TicketReader reader;
    private final TicketStatusValidator validator;

    public TicketService(TicketRepository tickets, NoteRepository notes, ServiceRepository services,
                         PlatformTransactionManager manager) {
        this.tickets = tickets;
        this.notes = notes;
        this.services = services;
        this.transactions = new TransactionTemplate(manager);
        this.clock = Clock.systemUTC();
        this.reader = new TicketReader(tickets, notes);
        this.validator = new TicketStatusValidator();
    }

    public record CreateResult(TicketResponse ticket, boolean created) {
    }

    public CreateResult create(String tenant, CreateRequest request) {
        validator.validateNew(request.status());
        return tickets.findByTenantIdAndExternalId(tenant, request.externalId())
                .map(ticket -> new CreateResult(reader.toResponse(ticket), false))
                .orElseGet(() -> createNewOrGetExisting(tenant, request));
    }

    private CreateResult createNewOrGetExisting(String tenant, CreateRequest request) {
        if (!services.existsActive(tenant, request.serviceId()))
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "SERVICE_NOT_FOUND",
                    "Service not found or unavailable");
        try {
            var ticket = transactions.execute(status -> {
                var created = new Ticket(
                        tenant,
                        request.externalId(),
                        request.serviceId(),
                        request.description(),
                        "acknowledged",
                        clock.instant()
                );
                tickets.saveAndFlush(created);
                notes.save(new TicketNote(created.id(), request.note(), clock.instant()));
                return created;
            });
            return new CreateResult(reader.toResponse(ticket), true);

        } catch (DataIntegrityViolationException ex) {
            return tickets.findByTenantIdAndExternalId(tenant, request.externalId())
                    .map(ticket -> new CreateResult(reader.toResponse(ticket), false))
                    .orElseThrow(() -> ex);
        }
    }

    @Transactional(readOnly = true)
    public List<TicketSummary> list(String tenant) {
        return tickets.findByTenantIdOrderByCreatedAtAscIdAsc(tenant).stream()
                .map(t -> new TicketSummary(t.externalId(), t.serviceId(), t.description(), t.status())).toList();
    }

    @Transactional(readOnly = true)
    public TicketResponse get(String tenant, String id) {
        return reader.toResponse(reader.findOwned(tenant, id));
    }

    @Transactional
    public TicketResponse close(String tenant, String id, CloseRequest request) {
        validator.validateClosed(request.status());
        Ticket ticket = reader.findOwned(tenant, id);
        ticket.close();
        tickets.save(ticket);
        return reader.toResponse(ticket);
    }

    @Transactional
    public NoteResponse addNote(String tenant, String id, NoteRequest request) {
        Ticket ticket = reader.findOwned(tenant, id);
        return reader.toResponse(notes.save(new TicketNote(ticket.id(),
                request.text(), clock.instant())));
    }
}
