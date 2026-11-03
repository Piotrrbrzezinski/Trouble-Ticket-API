CREATE TABLE customer_services (
  id BIGINT NOT NULL,
  tenant_id VARCHAR(128) NOT NULL,
  active BOOLEAN NOT NULL,
  PRIMARY KEY (tenant_id, id)
);

CREATE TABLE trouble_tickets (
  id VARCHAR(36) PRIMARY KEY,
  tenant_id VARCHAR(128) NOT NULL,
  external_id TEXT NOT NULL,
  service_id BIGINT NOT NULL,
  description TEXT NOT NULL,
  status VARCHAR(32) NOT NULL CHECK (status IN ('new', 'acknowledged', 'inProgress', 'resolved', 'closed', 'rejected')),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT uq_ticket_tenant_external UNIQUE (tenant_id, external_id),
  CONSTRAINT ck_ticket_external_id_nonempty CHECK (char_length(external_id) >= 1),
  CONSTRAINT ck_ticket_description_nonempty CHECK (char_length(description) >= 1),
  CONSTRAINT ck_ticket_service_positive CHECK (service_id >= 1),
  CONSTRAINT fk_ticket_service FOREIGN KEY (tenant_id, service_id)
    REFERENCES customer_services (tenant_id, id)
);
CREATE INDEX idx_ticket_tenant_created ON trouble_tickets (tenant_id, created_at, id);

CREATE TABLE ticket_notes (
  id VARCHAR(36) PRIMARY KEY,
  ticket_id VARCHAR(36) NOT NULL REFERENCES trouble_tickets(id),
  text TEXT NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT ck_ticket_note_nonempty CHECK (char_length(text) >= 1)
);
CREATE INDEX idx_note_ticket_date ON ticket_notes (ticket_id, created_at, id);
