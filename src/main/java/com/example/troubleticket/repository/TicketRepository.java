package com.example.troubleticket.repository;

import com.example.troubleticket.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, String> {
    Optional<Ticket> findByTenantIdAndExternalId(String tenantId, String externalId);
    Optional<Ticket> findByIdAndTenantId(String id, String tenantId);
    List<Ticket> findByTenantIdOrderByCreatedAtAscIdAsc(String tenantId);
}
