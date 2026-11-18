package com.example.troubleticket.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "ticket_notes")
public class TicketNote {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    @Column(name = "ticket_id", nullable = false)
    private String ticketId;
    @Column(nullable = false, columnDefinition = "text")
    private String text;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TicketNote() {
    }

    public TicketNote(String ticketId, String text, Instant createdAt) {
        this.ticketId = ticketId;
        this.text = text;
        this.createdAt = createdAt;
    }

    public String id() {
        return id;
    }

    public String ticketId() {
        return ticketId;
    }

    public String text() {
        return text;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
