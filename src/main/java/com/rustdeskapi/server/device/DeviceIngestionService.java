package com.rustdeskapi.server.device;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rustdeskapi.server.common.JsonPayload;
import com.rustdeskapi.server.common.SensitiveJsonSanitizer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class DeviceIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DeviceIngestionService.class);

    private final DeviceRepository deviceRepository;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String sysinfoVersion;
    private final TransactionTemplate transactionTemplate;

    public DeviceIngestionService(
            DeviceRepository deviceRepository,
            EntityManager entityManager,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager,
            @Value("${rustdesk.sysinfo-version:1}") String sysinfoVersion) {
        this.deviceRepository = deviceRepository;
        this.entityManager = entityManager;
        this.objectMapper = objectMapper;
        this.clock = Clock.systemUTC();
        this.sysinfoVersion = sysinfoVersion;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public boolean heartbeat(HeartbeatRequest request) {
        log.debug("heartbeat persist id={} uuid={} ver={}",
                request.id(), request.uuid(), request.ver());
        JsonNode connections = objectMapper.valueToTree(
                request.conns() == null ? List.of() : request.conns());
        return executeWithRetry(() -> {
            Instant now = clock.instant();
            Device device = findByRustdeskIdForUpdate(request.id())
                    .orElseGet(() -> new Device(request.id(), request.uuid(), now));
            boolean requiresSysinfo = device.requiresSysinfo(request.uuid());
            device.applyHeartbeat(request.uuid(), request.ver(), connections, now);
            deviceRepository.save(device);
            return requiresSysinfo;
        });
    }

    public void updateSysinfo(JsonNode payload) {
        log.info("sysinfo persist id={} uuid={} version={} hostname={} os={}",
                JsonPayload.requiredText(payload, "id", 64),
                JsonPayload.requiredText(payload, "uuid", 255),
                payload.path("version").asText(),
                payload.path("hostname").asText(),
                payload.path("os").asText());
        JsonPayload.requireObject(payload);
        String rustdeskId = JsonPayload.requiredText(payload, "id", 64);
        String uuid = JsonPayload.requiredText(payload, "uuid", 255);
        String hostname = JsonPayload.optionalText(payload, "hostname", 255);
        String username = JsonPayload.optionalText(payload, "username", 255);
        String operatingSystem = JsonPayload.optionalText(payload, "os", 1000);
        String cpu = JsonPayload.optionalText(payload, "cpu", 500);
        String memory = JsonPayload.optionalText(payload, "memory", 100);
        String clientVersion = JsonPayload.optionalText(payload, "version", 64);
        JsonNode rawSysinfo = SensitiveJsonSanitizer.sanitize(payload);

        executeWithRetry(() -> {
            Instant now = clock.instant();
            Device device = findByRustdeskIdForUpdate(rustdeskId)
                    .orElseGet(() -> new Device(rustdeskId, uuid, now));
            device.applySysinfo(
                    uuid,
                    hostname,
                    username,
                    operatingSystem,
                    cpu,
                    memory,
                    clientVersion,
                    rawSysinfo,
                    now);
            deviceRepository.save(device);
            return null;
        });
    }

    public String getSysinfoVersion() {
        return sysinfoVersion;
    }

    private Optional<Device> findByRustdeskIdForUpdate(String rustdeskId) {
        Optional<Device> device = deviceRepository.findByRustdeskId(rustdeskId);
        device.ifPresent(value -> entityManager.refresh(value, LockModeType.PESSIMISTIC_WRITE));
        return device;
    }

    private <T> T executeWithRetry(Supplier<T> operation) {
        for (int attempt = 0; ; attempt++) {
            try {
                return transactionTemplate.execute(status -> operation.get());
            } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException exception) {
                if (attempt == 1) {
                    throw exception;
                }
            }
        }
    }
}
