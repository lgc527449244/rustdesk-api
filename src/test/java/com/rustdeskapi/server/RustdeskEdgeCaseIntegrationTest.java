package com.rustdeskapi.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rustdeskapi.server.audit.AuditEvent;
import com.rustdeskapi.server.audit.AuditEventRepository;
import com.rustdeskapi.server.device.Device;
import com.rustdeskapi.server.device.DeviceRepository;
import java.util.stream.IntStream;
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
class RustdeskEdgeCaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    void heartbeatAcceptsMoreThanOneHundredConnections() throws Exception {
        ObjectNode payload = objectMapper.createObjectNode()
                .put("id", "many-connections-device")
                .put("uuid", "many-connections-uuid")
                .put("ver", 1_409_000);
        ArrayNode connections = payload.putArray("conns");
        IntStream.rangeClosed(0, 100).forEach(connections::add);

        mockMvc.perform(post("/api/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(payload)))
                .andExpect(status().isOk());

        Device device = deviceRepository.findByRustdeskId("many-connections-device").orElseThrow();
        assertThat(device.getActiveConnections()).hasSize(101);
    }

    @Test
    void fileAuditTruncatesExtractedPathButKeepsFullRawPath() throws Exception {
        String path = "p".repeat(2050);
        ObjectNode payload = auditPayload("long-path-nonce")
                .put("path", path)
                .put("is_file", true);

        postAudit("file", payload);

        AuditEvent event = eventWithNonce("long-path-nonce");
        assertThat(event.getPath()).isEqualTo(path.substring(0, 2048));
        assertThat(event.getRawPayload().path("path").asText()).isEqualTo(path);
    }

    @Test
    void auditSanitizesNestedFieldsAndJsonEncodedStrings() throws Exception {
        ObjectNode payload = auditPayload("sensitive-nonce");
        ObjectNode metadata = payload.putObject("metadata");
        metadata.put("Authorization", "Bearer value");
        metadata.put("safe", "visible");

        ArrayNode entries = payload.putArray("entries");
        entries.addObject()
                .put("session-cookie", "cookie-value")
                .put("private_key", "private-value");
        entries.addObject()
                .put("API-KEY", "api-value")
                .put("client_credential", "credential-value");

        ObjectNode encodedInfo = objectMapper.createObjectNode();
        encodedInfo.put("access_token", "token-value");
        encodedInfo.putArray("nested")
                .addObject()
                .put("PWD", "pwd-value")
                .put("pass_phrase", "phrase-value")
                .put("message", "kept");
        payload.put("info", objectMapper.writeValueAsString(encodedInfo));
        payload.put("ordinary_text", "not JSON");
        payload.put("invalid_json", "{\"password\":");

        postAudit("alarm", payload);

        JsonNode rawPayload = eventWithNonce("sensitive-nonce").getRawPayload();
        assertThat(rawPayload.path("metadata").path("Authorization").asText()).isEqualTo("[REDACTED]");
        assertThat(rawPayload.path("metadata").path("safe").asText()).isEqualTo("visible");
        assertThat(rawPayload.path("entries").path(0).path("session-cookie").asText())
                .isEqualTo("[REDACTED]");
        assertThat(rawPayload.path("entries").path(0).path("private_key").asText())
                .isEqualTo("[REDACTED]");
        assertThat(rawPayload.path("entries").path(1).path("API-KEY").asText())
                .isEqualTo("[REDACTED]");
        assertThat(rawPayload.path("entries").path(1).path("client_credential").asText())
                .isEqualTo("[REDACTED]");

        assertThat(rawPayload.path("info").isTextual()).isTrue();
        JsonNode sanitizedInfo = objectMapper.readTree(rawPayload.path("info").asText());
        assertThat(sanitizedInfo.path("access_token").asText()).isEqualTo("[REDACTED]");
        assertThat(sanitizedInfo.path("nested").path(0).path("PWD").asText()).isEqualTo("[REDACTED]");
        assertThat(sanitizedInfo.path("nested").path(0).path("pass_phrase").asText())
                .isEqualTo("[REDACTED]");
        assertThat(sanitizedInfo.path("nested").path(0).path("message").asText()).isEqualTo("kept");
        assertThat(rawPayload.path("ordinary_text").asText()).isEqualTo("not JSON");
        assertThat(rawPayload.path("invalid_json").asText()).isEqualTo("{\"password\":");
    }

    private ObjectNode auditPayload(String nonce) {
        return objectMapper.createObjectNode()
                .put("id", "edge-device")
                .put("uuid", "edge-uuid")
                .put("nonce", nonce);
    }

    private void postAudit(String kind, JsonNode payload) throws Exception {
        mockMvc.perform(post("/api/audit/{kind}", kind)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(payload)))
                .andExpect(status().isOk());
    }

    private AuditEvent eventWithNonce(String nonce) {
        return auditEventRepository.findAll().stream()
                .filter(event -> event.getNonce().equals(nonce))
                .findFirst()
                .orElseThrow();
    }
}
