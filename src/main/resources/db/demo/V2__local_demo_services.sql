-- Loaded only by Docker Compose; never applied in production by default.
INSERT INTO customer_services (id, tenant_id, active) VALUES
  (987654321, 'tenant-a', TRUE),
  (987654322, 'tenant-a', FALSE),
  (987654321, 'tenant-b', TRUE);
