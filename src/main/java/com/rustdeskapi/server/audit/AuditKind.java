package com.rustdeskapi.server.audit;

import com.rustdeskapi.server.common.InvalidPayloadException;

public enum AuditKind {
    CONN,
    FILE,
    ALARM;

    public static AuditKind fromPath(String value) {
        return switch (value) {
            case "conn" -> CONN;
            case "file" -> FILE;
            case "alarm" -> ALARM;
            default -> throw new InvalidPayloadException("unsupported audit type '" + value + "'");
        };
    }
}
