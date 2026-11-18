package com.example.troubleticket.tools;

import com.example.troubleticket.dto.Models.NoteResponse;
import com.example.troubleticket.dto.Models.TicketResponse;
import com.example.troubleticket.entity.Ticket;
import com.example.troubleticket.entity.TicketNote;
import com.example.troubleticket.exception.ApiException;
import com.example.troubleticket.repository.NoteRepository;
import com.example.troubleticket.repository.TicketRepository;
import org.springframework.http.HttpStatus;

public final class TicketReader {
    private final TicketRepository tickets;
    private final NoteRepository notes;

    public TicketReader(TicketRepository tickets, NoteRepository notes) {
        this.tickets = tickets;
        this.notes = notes;
    }

    public Ticket findOwned(String tenant, String id) {
        return tickets.findByIdAndTenantId(id, tenant).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "TROUBLE_TICKET_NOT_FOUND", "Trouble Ticket not found"));
    }

    public TicketResponse toResponse(Ticket ticket) {
        return new TicketResponse(ticket.id(), ticket.externalId(), ticket.serviceId(), ticket.description(), ticket.status(),
                notes.findByTicketIdOrderByCreatedAtAscIdAsc(ticket.id()).stream().map(this::toResponse).toList());
    }

    public NoteResponse toResponse(TicketNote note) {
        return new NoteResponse(note.id(), note.text(), note.createdAt());
    }
}
