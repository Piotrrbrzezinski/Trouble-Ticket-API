package com.example.troubleticket.entity;

import java.io.Serializable;
import java.util.Objects;

public class ServiceKey implements Serializable {
    public String tenantId;
    public Long id;
    public ServiceKey() {}
    public ServiceKey(String tenantId, Long id) { this.tenantId=tenantId; this.id=id; }
    @Override public boolean equals(Object other) {
        if (!(other instanceof ServiceKey key)) return false;
        return Objects.equals(tenantId,key.tenantId) && Objects.equals(id,key.id);
    }
    @Override public int hashCode() { return Objects.hash(tenantId,id); }
}
