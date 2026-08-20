package com.rustdeskapi.server.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.rustdeskapi.server.audit.AuditIngestionService;
import com.rustdeskapi.server.audit.AuditKind;
import com.rustdeskapi.server.device.DeviceIngestionService;
import com.rustdeskapi.server.device.HeartbeatRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class RustdeskIngestionController {

    private static final Logger log = LoggerFactory.getLogger(RustdeskIngestionController.class);

    private final DeviceIngestionService deviceIngestionService;
    private final AuditIngestionService auditIngestionService;

    public RustdeskIngestionController(
            DeviceIngestionService deviceIngestionService,
            AuditIngestionService auditIngestionService) {
        this.deviceIngestionService = deviceIngestionService;
        this.auditIngestionService = auditIngestionService;
    }

    @GetMapping(value = "/login-options", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<String> loginOptions() {
        return List.of();
    }

    @PostMapping(
            value = "/heartbeat",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> heartbeat(@Valid @RequestBody HeartbeatRequest request) {
        log.info("heartbeat received id={} uuid={} conns={}",
                request.id(), request.uuid(),
                request.conns() == null ? 0 : request.conns().size());
        if (deviceIngestionService.heartbeat(request)) {
            return Map.of("sysinfo", true);
        }
        return Map.of();
    }

    @PostMapping(
            value = "/sysinfo",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_PLAIN_VALUE)
    public String sysinfo(@RequestBody JsonNode payload) {
        log.info("sysinfo received id={} uuid={} version={}",
                payload.path("id").asText(),
                payload.path("uuid").asText(),
                payload.path("version").asText());
        deviceIngestionService.updateSysinfo(payload);
        return "SYSINFO_UPDATED";
    }

    @PostMapping(value = "/sysinfo_ver", produces = MediaType.TEXT_PLAIN_VALUE)
    public String sysinfoVersion(@RequestBody(required = false) String ignoredBody) {
        log.info("sysinfo_ver requested -> {}", deviceIngestionService.getSysinfoVersion());
        return deviceIngestionService.getSysinfoVersion();
    }

    @PostMapping(value = "/audit/{kind}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> audit(@PathVariable String kind, @RequestBody JsonNode payload) {
        log.info("audit received kind={} id={} uuid={} action={} nonce={}",
                kind,
                payload.path("id").asText(),
                payload.path("uuid").asText(),
                payload.path("action").asText(),
                payload.path("nonce").asText());
        auditIngestionService.store(AuditKind.fromPath(kind), payload);
        return ResponseEntity.ok().build();
    }
}
