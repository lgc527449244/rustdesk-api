package com.rustdeskapi.server.audit;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    boolean existsByKindAndNonce(AuditKind kind, String nonce);
}
