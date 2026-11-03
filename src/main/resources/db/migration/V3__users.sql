CREATE TABLE app_users (
  username VARCHAR(128) PRIMARY KEY,
  password_hash VARCHAR(100) NOT NULL,
  tenant_id VARCHAR(128) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  CONSTRAINT ck_app_users_username_nonempty CHECK (char_length(username) >= 1),
  CONSTRAINT ck_app_users_tenant_nonempty CHECK (char_length(tenant_id) >= 1)
);

CREATE INDEX idx_app_users_tenant ON app_users (tenant_id);
