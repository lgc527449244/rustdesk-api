ALTER TABLE rustdesk_devices
    ADD COLUMN last_client_ip VARCHAR(45) NULL AFTER client_version;

ALTER TABLE rustdesk_audit_events
    ADD COLUMN client_ip VARCHAR(45) NULL AFTER device_uuid;

CREATE INDEX idx_rustdesk_audit_events_ip_received_at
    ON rustdesk_audit_events (client_ip, received_at);
