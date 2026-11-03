package com.example.troubleticket.repository;

import com.example.troubleticket.entity.TicketNote;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface NoteRepository extends JpaRepository<TicketNote, String> {
    List<TicketNote> findByTicketIdOrderByCreatedAtAscIdAsc(String ticketId);
}
