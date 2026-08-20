package com.rustdeskapi.server.device;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "rustdesk_devices")
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    @Column(name = "rustdesk_id", nullable = false, unique = true, length = 64)
    private String rustdeskId;

    @Column(name = "device_uuid", nullable = false, length = 255)
    private String deviceUuid;

    @Column(length = 255)
    private String hostname;

    @Column(length = 255)
    private String username;

    @Column(name = "operating_system", length = 1000)
    private String operatingSystem;

    @Column(length = 500)
    private String cpu;

    @Column(length = 100)
    private String memory;

    @Column(name = "client_version", length = 64)
    private String clientVersion;

    @Column(name = "last_client_ip", length = 45)
    private String lastClientIp;

    @Column(name = "protocol_version")
    private Long protocolVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "active_connections", columnDefinition = "json")
    private JsonNode activeConnections;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_sysinfo", columnDefinition = "json")
    private JsonNode rawSysinfo;

    @Column(name = "sysinfo_received", nullable = false)
    private boolean sysinfoReceived;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Device() {
    }

    public Device(String rustdeskId, String deviceUuid, Instant now) {
        this.rustdeskId = rustdeskId;
        this.deviceUuid = deviceUuid;
        this.lastSeenAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public boolean requiresSysinfo(String heartbeatUuid) {
        return !sysinfoReceived || !Objects.equals(deviceUuid, heartbeatUuid);
    }

    public void applyHeartbeat(String uuid, Long version, JsonNode connections, String clientIp, Instant now) {
        if (!Objects.equals(deviceUuid, uuid)) {
            sysinfoReceived = false;
        }
        deviceUuid = uuid;
        protocolVersion = version;
        activeConnections = connections;
        if (clientIp != null && !clientIp.isBlank()) {
            lastClientIp = clientIp;
        }
        lastSeenAt = now;
        updatedAt = now;
    }

    public void applySysinfo(
            String uuid,
            String hostname,
            String username,
            String operatingSystem,
            String cpu,
            String memory,
            String clientVersion,
            String clientIp,
            JsonNode rawSysinfo,
            Instant now) {
        this.deviceUuid = uuid;
        this.hostname = hostname;
        this.username = username;
        this.operatingSystem = operatingSystem;
        this.cpu = cpu;
        this.memory = memory;
        this.clientVersion = clientVersion;
        if (clientIp != null && !clientIp.isBlank()) {
            this.lastClientIp = clientIp;
        }
        this.rawSysinfo = rawSysinfo;
        this.sysinfoReceived = true;
        this.lastSeenAt = now;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getRustdeskId() {
        return rustdeskId;
    }

    public String getDeviceUuid() {
        return deviceUuid;
    }

    public String getHostname() {
        return hostname;
    }

    public String getUsername() {
        return username;
    }

    public String getOperatingSystem() {
        return operatingSystem;
    }

    public String getCpu() {
        return cpu;
    }

    public String getMemory() {
        return memory;
    }

    public String getClientVersion() {
        return clientVersion;
    }

    public String getLastClientIp() {
        return lastClientIp;
    }

    public Long getProtocolVersion() {
        return protocolVersion;
    }

    public JsonNode getActiveConnections() {
        return activeConnections;
    }

    public JsonNode getRawSysinfo() {
        return rawSysinfo;
    }

    public boolean isSysinfoReceived() {
        return sysinfoReceived;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
