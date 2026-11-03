package com.example.troubleticket.entity;

import jakarta.persistence.*;

@Entity
@IdClass(ServiceKey.class)
@Table(name="customer_services")
public class ServiceRow {
    @Id @Column(name="tenant_id") public String tenantId;
    @Id public Long id;
    public boolean active;
    protected ServiceRow() {}
}
