package com.example.troubleticket;

import com.example.troubleticket.dto.Models.CreateRequest;
import com.example.troubleticket.entity.Ticket;
import com.example.troubleticket.exception.ApiException;
import com.example.troubleticket.repository.NoteRepository;
import com.example.troubleticket.repository.ServiceRepository;
import com.example.troubleticket.repository.TicketRepository;
import com.example.troubleticket.service.TicketService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {
    @Mock TicketRepository tickets;
    @Mock NoteRepository notes;
    @Mock ServiceRepository services;
    @Mock PlatformTransactionManager transactionManager;

    private TicketService service() { return new TicketService(tickets, notes, services, transactionManager); }

    @Test
    void createRejectsStatusOtherThanNew() {
        var error = assertThrows(ApiException.class, () -> service().create("a",
                new CreateRequest("external", 1L, "description", "resolved", "note")));
        assertEquals(HttpStatus.BAD_REQUEST, error.status);
        assertEquals("VALIDATION_ERROR", error.code);
        verifyNoInteractions(tickets, notes, services);
    }

    @Test
    void createRejectsServiceNotAccessibleToTenant() {
        when(tickets.findByTenantIdAndExternalId("a", "external")).thenReturn(Optional.empty());
        when(services.existsActive("a", 1L)).thenReturn(false);
        var error = assertThrows(ApiException.class, () -> service().create("a",
                new CreateRequest("external", 1L, "description", "new", "note")));
        assertEquals(HttpStatus.NOT_FOUND, error.status);
        assertEquals("SERVICE_NOT_FOUND", error.code);
        verifyNoInteractions(notes);
    }

    @Test
    void idempotentCreateReturnsExistingRepresentationWithoutRecheckingService() {
        var ticket = mock(Ticket.class);
        when(ticket.id()).thenReturn("id");
        when(ticket.externalId()).thenReturn("external");
        when(ticket.serviceId()).thenReturn(1L);
        when(ticket.description()).thenReturn("first description");
        when(ticket.status()).thenReturn("closed");
        when(tickets.findByTenantIdAndExternalId("a", "external")).thenReturn(Optional.of(ticket));
        when(notes.findByTicketIdOrderByCreatedAtAscIdAsc("id")).thenReturn(List.of());
        var result = service().create("a", new CreateRequest("external", 999L, "new description", "new", "new note"));
        assertFalse(result.created());
        assertEquals("id", result.ticket().id());
        assertEquals("closed", result.ticket().status());
        assertEquals("first description", result.ticket().description());
        verifyNoInteractions(services);
    }
}
