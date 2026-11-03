package com.example.troubleticket.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "trouble_tickets")
public class Ticket {
    @Id private String id;
    @Column(name="tenant_id", nullable=false) private String tenantId;
    @Column(name="external_id", nullable=false, columnDefinition="text") private String externalId;
    @Column(name="service_id", nullable=false) private Long serviceId;
    @Column(nullable=false, columnDefinition="text") private String description;
    @Column(nullable=false) private String status;
    @Column(name="created_at", nullable=false) private Instant createdAt;

    protected Ticket() {}
    public Ticket(String id, String tenantId, String externalId, Long serviceId, String description, String status, Instant createdAt) {
        this.id=id; this.tenantId=tenantId; this.externalId=externalId; this.serviceId=serviceId;
        this.description=description; this.status=status; this.createdAt=createdAt;
    }

    public String id() { return id; }
    public String tenantId() { return tenantId; }
    public String externalId() { return externalId; }
    public Long serviceId() { return serviceId; }
    public String description() { return description; }
    public String status() { return status; }
    public Instant createdAt() { return createdAt; }

    public void close() { status = "closed"; }
}
