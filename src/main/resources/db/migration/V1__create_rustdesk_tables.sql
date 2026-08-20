CREATE TABLE rustdesk_devices
(
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    row_version        BIGINT       NOT NULL DEFAULT 0,
    rustdesk_id        VARCHAR(64)  NOT NULL,
    device_uuid        VARCHAR(255) NOT NULL,
    hostname           VARCHAR(255) NULL,
    username           VARCHAR(255) NULL,
    operating_system   VARCHAR(1000) NULL,
    cpu                VARCHAR(500)  NULL,
    memory             VARCHAR(100)  NULL,
    client_version     VARCHAR(64)  NULL,
    protocol_version   BIGINT       NULL,
    active_connections JSON         NULL,
    raw_sysinfo        JSON         NULL,
    sysinfo_received   BOOLEAN      NOT NULL DEFAULT FALSE,
    last_seen_at       TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_at         TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at         TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_rustdesk_devices_rustdesk_id UNIQUE (rustdesk_id),
    INDEX idx_rustdesk_devices_device_uuid (device_uuid),
    INDEX idx_rustdesk_devices_last_seen_at (last_seen_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE rustdesk_audit_events
(
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    kind          VARCHAR(16)   NOT NULL,
    nonce         VARCHAR(64)   NOT NULL,
    rustdesk_id   VARCHAR(64)   NOT NULL,
    device_uuid   VARCHAR(255)  NOT NULL,
    connection_id BIGINT        NULL,
    session_id    VARCHAR(128)  NULL,
    peer_id       VARCHAR(64)   NULL,
    action        VARCHAR(32)   NULL,
    event_code    SMALLINT      NULL,
    path          VARCHAR(2048) NULL,
    is_file       BOOLEAN       NULL,
    raw_payload   JSON          NOT NULL,
    received_at   TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_rustdesk_audit_events_kind_nonce UNIQUE (kind, nonce),
    INDEX idx_rustdesk_audit_events_device_received_at (device_uuid, received_at),
    INDEX idx_rustdesk_audit_events_kind_received_at (kind, received_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
