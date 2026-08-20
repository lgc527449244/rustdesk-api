package com.rustdeskapi.server.audit;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "rustdesk_audit_events",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_rustdesk_audit_events_kind_nonce",
                columnNames = {"kind", "nonce"}))
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AuditKind kind;

    @Column(nullable = false, length = 64)
    private String nonce;

    @Column(name = "rustdesk_id", nullable = false, length = 64)
    private String rustdeskId;

    @Column(name = "device_uuid", nullable = false, length = 255)
    private String deviceUuid;

    @Column(name = "connection_id")
    private Long connectionId;

    @Column(name = "session_id", length = 128)
    private String sessionId;

    @Column(name = "peer_id", length = 64)
    private String peerId;

    @Column(length = 32)
    private String action;

    @Column(name = "event_code")
    private Short eventCode;

    @Column(length = 2048)
    private String path;

    @Column(name = "is_file")
    private Boolean file;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", nullable = false, columnDefinition = "json")
    private JsonNode rawPayload;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    protected AuditEvent() {
    }

    public AuditEvent(
            AuditKind kind,
            String nonce,
            String rustdeskId,
            String deviceUuid,
            Long connectionId,
            String sessionId,
            String peerId,
            String action,
            Short eventCode,
            String path,
            Boolean file,
            JsonNode rawPayload,
            Instant receivedAt) {
        this.kind = kind;
        this.nonce = nonce;
        this.rustdeskId = rustdeskId;
        this.deviceUuid = deviceUuid;
        this.connectionId = connectionId;
        this.sessionId = sessionId;
        this.peerId = peerId;
        this.action = action;
        this.eventCode = eventCode;
        this.path = path;
        this.file = file;
        this.rawPayload = rawPayload;
        this.receivedAt = receivedAt;
    }

    public Long getId() {
        return id;
    }

    public AuditKind getKind() {
        return kind;
    }

    public String getNonce() {
        return nonce;
    }

    public String getRustdeskId() {
        return rustdeskId;
    }

    public String getDeviceUuid() {
        return deviceUuid;
    }

    public Long getConnectionId() {
        return connectionId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getPeerId() {
        return peerId;
    }

    public String getAction() {
        return action;
    }

    public Short getEventCode() {
        return eventCode;
    }

    public String getPath() {
        return path;
    }

    public Boolean getFile() {
        return file;
    }

    public JsonNode getRawPayload() {
        return rawPayload;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
