package com.rustdeskapi.server.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.rustdeskapi.server.common.InvalidPayloadException;
import com.rustdeskapi.server.common.JsonPayload;
import com.rustdeskapi.server.common.SensitiveJsonSanitizer;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class AuditIngestionService {

    private static final Logger log = LoggerFactory.getLogger(AuditIngestionService.class);

    private static final int MAX_STORED_PATH_LENGTH = 2048;

    private final AuditEventRepository auditEventRepository;
    private final Clock clock;

    @Autowired
    public AuditIngestionService(AuditEventRepository auditEventRepository) {
        this(auditEventRepository, Clock.systemUTC());
    }

    AuditIngestionService(AuditEventRepository auditEventRepository, Clock clock) {
        this.auditEventRepository = auditEventRepository;
        this.clock = clock;
    }

    public void store(AuditKind kind, JsonNode payload) {
        String rustdeskId = JsonPayload.requiredText(payload, "id", 64);
        String action = JsonPayload.optionalText(payload, "action", 32);
        log.info("audit persist kind={} id={} action={}", kind, rustdeskId, action);
        JsonPayload.requireObject(payload);
        String uuid = auditUuid(kind, payload);
        String nonce = JsonPayload.optionalText(payload, "nonce", 64);
        if (nonce == null || nonce.isBlank()) {
            nonce = UUID.randomUUID().toString();
        }
        if (auditEventRepository.existsByKindAndNonce(kind, nonce)) {
            log.info("audit duplicate skipped kind={} id={} nonce={}", kind, rustdeskId, nonce);
            return;
        }

        AuditEvent event = new AuditEvent(
                kind,
                nonce,
                rustdeskId,
                uuid,
                JsonPayload.optionalLong(payload, "conn_id"),
                JsonPayload.optionalScalarText(payload, "session_id", 128),
                peerId(payload),
                JsonPayload.optionalText(payload, "action", 32),
                eventCode(kind, payload),
                path(payload),
                JsonPayload.optionalBoolean(payload, "is_file"),
                SensitiveJsonSanitizer.sanitize(payload),
                Instant.now(clock));
        saveIdempotently(event, kind, nonce);
    }

    private void saveIdempotently(AuditEvent event, AuditKind kind, String nonce) {
        try {
            auditEventRepository.saveAndFlush(event);
        } catch (DataIntegrityViolationException exception) {
            if (!auditEventRepository.existsByKindAndNonce(kind, nonce)) {
                throw exception;
            }
        }
    }

    private Short eventCode(AuditKind kind, JsonNode payload) {
        return JsonPayload.optionalShort(payload, kind == AuditKind.ALARM ? "typ" : "type");
    }

    private String path(JsonNode payload) {
        String path = JsonPayload.optionalText(payload, "path", Integer.MAX_VALUE);
        if (path == null || path.length() <= MAX_STORED_PATH_LENGTH) {
            return path;
        }
        return path.substring(0, MAX_STORED_PATH_LENGTH);
    }

    private String auditUuid(AuditKind kind, JsonNode payload) {
        String uuid = JsonPayload.optionalText(payload, "uuid", 255);
        if (uuid != null && !uuid.isBlank()) {
            return uuid;
        }
        if (kind == AuditKind.CONN && payload.hasNonNull("note") && payload.hasNonNull("session_id")) {
            return "";
        }
        throw new InvalidPayloadException("field 'uuid' must not be blank");
    }

    private String peerId(JsonNode payload) {
        String peerId = JsonPayload.optionalText(payload, "peer_id", 64);
        if (peerId != null) {
            return peerId;
        }
        JsonNode peer = payload.get("peer");
        if (peer == null || peer.isNull()) {
            return null;
        }
        if (!peer.isArray() || peer.isEmpty() || !peer.get(0).isValueNode()) {
            throw new InvalidPayloadException("field 'peer' must be an array whose first item is a peer ID");
        }
        String value = peer.get(0).asText();
        if (value.length() > 64) {
            throw new InvalidPayloadException("peer ID must be at most 64 characters");
        }
        return value;
    }
}
