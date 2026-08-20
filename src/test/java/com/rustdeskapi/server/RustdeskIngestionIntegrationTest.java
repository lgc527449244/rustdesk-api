package com.rustdeskapi.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rustdeskapi.server.audit.AuditEvent;
import com.rustdeskapi.server.audit.AuditEventRepository;
import com.rustdeskapi.server.audit.AuditKind;
import com.rustdeskapi.server.device.Device;
import com.rustdeskapi.server.device.DeviceRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RustdeskIngestionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @BeforeEach
    void clearDatabase() {
        auditEventRepository.deleteAllInBatch();
        deviceRepository.deleteAllInBatch();
    }

    @Test
    void loginOptionsAreEmpty() throws Exception {
        mockMvc.perform(get("/api/login-options"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string("[]"));
    }

    @Test
    void sysinfoReturnsRustdeskMarkerAndPersistsKnownAndUnknownFields() throws Exception {
        mockMvc.perform(post("/api/sysinfo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": "device-100",
                                  "uuid": "base64-device-uuid",
                                  "hostname": "workstation-01",
                                  "username": "alice",
                                  "os": "Windows / 11 Pro",
                                  "cpu": "Intel Core, 8/4 cores",
                                  "memory": "16.0GB",
                                  "version": "1.4.9",
                                  "future_extension": {"enabled": true},
                                  "preset-note": "accounting",
                                  "preset-address-book-password": "do-not-store"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string("SYSINFO_UPDATED"));

        Device device = deviceRepository.findByRustdeskId("device-100").orElseThrow();
        assertThat(device.getDeviceUuid()).isEqualTo("base64-device-uuid");
        assertThat(device.getHostname()).isEqualTo("workstation-01");
        assertThat(device.getUsername()).isEqualTo("alice");
        assertThat(device.getOperatingSystem()).isEqualTo("Windows / 11 Pro");
        assertThat(device.getCpu()).isEqualTo("Intel Core, 8/4 cores");
        assertThat(device.getMemory()).isEqualTo("16.0GB");
        assertThat(device.getClientVersion()).isEqualTo("1.4.9");
        assertThat(device.isSysinfoReceived()).isTrue();
        assertThat(device.getRawSysinfo().path("future_extension").path("enabled").asBoolean()).isTrue();
        assertThat(device.getRawSysinfo().path("preset-note").asText()).isEqualTo("accounting");
        assertThat(device.getRawSysinfo().path("preset-address-book-password").asText())
                .isEqualTo("[REDACTED]");
    }

    @Test
    void heartbeatCreatesStubThenStopsRequestingSysinfoAfterRegistration() throws Exception {
        mockMvc.perform(post("/api/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": "device-200",
                                  "uuid": "uuid-200",
                                  "ver": 1409000,
                                  "conns": [3],
                                  "modified_at": 0
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sysinfo").value(true));

        Device stub = deviceRepository.findByRustdeskId("device-200").orElseThrow();
        assertThat(stub.getDeviceUuid()).isEqualTo("uuid-200");
        assertThat(stub.isSysinfoReceived()).isFalse();

        mockMvc.perform(post("/api/sysinfo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": "device-200",
                                  "uuid": "uuid-200",
                                  "hostname": "registered-host",
                                  "version": "1.4.9"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().string("SYSINFO_UPDATED"));

        mockMvc.perform(post("/api/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": "device-200",
                                  "uuid": "uuid-200",
                                  "ver": 1409001,
                                  "conns": [7, 9],
                                  "modified_at": 12
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().string("{}"));

        Device registered = deviceRepository.findByRustdeskId("device-200").orElseThrow();
        assertThat(registered.isSysinfoReceived()).isTrue();
        assertThat(registered.getProtocolVersion()).isEqualTo(1_409_001L);
        assertThat(registered.getActiveConnections().isArray()).isTrue();
        assertThat(registered.getActiveConnections()).hasSize(2);
        assertThat(registered.getActiveConnections().get(0).asInt()).isEqualTo(7);
        assertThat(registered.getActiveConnections().get(1).asInt()).isEqualTo(9);
    }

    @Test
    void sysinfoVersionIsOne() throws Exception {
        mockMvc.perform(post("/api/sysinfo_ver"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string("1"));
    }

    @Test
    void auditEndpointsExtractFieldsDeduplicateNonceAndAcceptLegacyPayloads() throws Exception {
        String connPayload = """
                {
                  "id": "controlled-device",
                  "uuid": "controlled-uuid",
                  "conn_id": 41,
                  "session_id": 9001,
                  "nonce": "conn-nonce",
                  "peer": ["peer-100", "Alice"],
                  "type": 2,
                  "action": "new",
                  "future_extension": "kept"
                }
                """;

        postAudit("conn", connPayload);
        postAudit("conn", connPayload);
        postAudit("file", """
                {
                  "id": "controlled-device",
                  "uuid": "controlled-uuid",
                  "conn_id": "42",
                  "nonce": "file-nonce",
                  "peer_id": "peer-200",
                  "type": 1,
                  "path": "/tmp/report.pdf",
                  "is_file": true,
                  "info": "{\\\"num\\\":1}"
                }
                """);
        postAudit("alarm", """
                {
                  "id": "controlled-device",
                  "uuid": "controlled-uuid",
                  "conn_id": 43,
                  "nonce": "alarm-nonce",
                  "typ": 6,
                  "info": "{\\\"ip\\\":\\\"192.0.2.10\\\"}"
                }
                """);
        postAudit("conn", """
                {
                  "id": "legacy-device",
                  "session_id": 9002,
                  "note": "legacy session note"
                }
                """);

        assertThat(auditEventRepository.count()).isEqualTo(4);

        AuditEvent conn = eventWithNonce("conn-nonce");
        assertThat(conn.getKind()).isEqualTo(AuditKind.CONN);
        assertThat(conn.getRustdeskId()).isEqualTo("controlled-device");
        assertThat(conn.getDeviceUuid()).isEqualTo("controlled-uuid");
        assertThat(conn.getConnectionId()).isEqualTo(41L);
        assertThat(conn.getSessionId()).isEqualTo("9001");
        assertThat(conn.getPeerId()).isEqualTo("peer-100");
        assertThat(conn.getAction()).isEqualTo("new");
        assertThat(conn.getEventCode()).isEqualTo((short) 2);
        assertThat(conn.getRawPayload().path("future_extension").asText()).isEqualTo("kept");

        AuditEvent file = eventWithNonce("file-nonce");
        assertThat(file.getKind()).isEqualTo(AuditKind.FILE);
        assertThat(file.getConnectionId()).isEqualTo(42L);
        assertThat(file.getPeerId()).isEqualTo("peer-200");
        assertThat(file.getEventCode()).isEqualTo((short) 1);
        assertThat(file.getPath()).isEqualTo("/tmp/report.pdf");
        assertThat(file.getFile()).isTrue();

        AuditEvent alarm = eventWithNonce("alarm-nonce");
        assertThat(alarm.getKind()).isEqualTo(AuditKind.ALARM);
        assertThat(alarm.getConnectionId()).isEqualTo(43L);
        assertThat(alarm.getEventCode()).isEqualTo((short) 6);

        List<AuditEvent> legacyEvents = auditEventRepository.findAll().stream()
                .filter(event -> event.getRustdeskId().equals("legacy-device"))
                .toList();
        assertThat(legacyEvents).hasSize(1);
        assertThat(legacyEvents.get(0).getNonce()).isNotBlank();
        assertThat(legacyEvents.get(0).getDeviceUuid()).isEmpty();
        assertThat(legacyEvents.get(0).getSessionId()).isEqualTo("9002");
        assertThat(legacyEvents.get(0).getRawPayload().path("note").asText())
                .isEqualTo("legacy session note");
    }

    @Test
    void auditPayloadWithoutDeviceIdIsRejected() throws Exception {
        mockMvc.perform(post("/api/audit/conn")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "uuid": "missing-id-uuid",
                                  "nonce": "invalid-nonce"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));

        assertThat(auditEventRepository.count()).isZero();
    }

    private void postAudit(String kind, String payload) throws Exception {
        mockMvc.perform(post("/api/audit/{kind}", kind)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
    }

    private AuditEvent eventWithNonce(String nonce) {
        return auditEventRepository.findAll().stream()
                .filter(event -> event.getNonce().equals(nonce))
                .findFirst()
                .orElseThrow();
    }
}
