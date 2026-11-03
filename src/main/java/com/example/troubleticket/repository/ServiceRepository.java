package com.example.troubleticket.repository;

import com.example.troubleticket.entity.ServiceKey;
import com.example.troubleticket.entity.ServiceRow;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface ServiceRepository extends Repository<ServiceRow, ServiceKey> {
    @Query(value = "SELECT EXISTS (SELECT 1 FROM customer_services WHERE tenant_id = :tenant AND id = :id AND active = TRUE)", nativeQuery = true)
    boolean existsActive(@Param("tenant") String tenantId, @Param("id") Long id);
}
