package com.example.troubleticket.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_users")
public class UserAccount {
    @Id
    @Column(length = 128)
    public String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    public String passwordHash;

    @Column(name = "tenant_id", nullable = false, length = 128)
    public String tenantId;

    @Column(nullable = false)
    public boolean enabled;

    protected UserAccount() {}
}
