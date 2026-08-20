package com.rustdeskapi.server.common;

import com.fasterxml.jackson.databind.JsonNode;

public final class JsonPayload {

    private JsonPayload() {
    }

    public static void requireObject(JsonNode payload) {
        if (payload == null || !payload.isObject()) {
            throw new InvalidPayloadException("request body must be a JSON object");
        }
    }

    public static String requiredText(JsonNode payload, String field, int maxLength) {
        String value = optionalText(payload, field, maxLength);
        if (value == null || value.isBlank()) {
            throw new InvalidPayloadException("field '" + field + "' must not be blank");
        }
        return value;
    }

    public static String optionalText(JsonNode payload, String field, int maxLength) {
        JsonNode value = payload.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw new InvalidPayloadException("field '" + field + "' must be a string");
        }
        String text = value.textValue();
        if (text.length() > maxLength) {
            throw new InvalidPayloadException(
                    "field '" + field + "' must be at most " + maxLength + " characters");
        }
        return text;
    }

    public static Long optionalLong(JsonNode payload, String field) {
        JsonNode value = payload.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.canConvertToLong() && value.isIntegralNumber()) {
            return value.longValue();
        }
        if (value.isTextual()) {
            try {
                return Long.valueOf(value.textValue());
            } catch (NumberFormatException ignored) {
                // Handled by the common validation error below.
            }
        }
        throw new InvalidPayloadException("field '" + field + "' must be an integer");
    }

    public static Short optionalShort(JsonNode payload, String field) {
        Long value = optionalLong(payload, field);
        if (value == null) {
            return null;
        }
        if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
            throw new InvalidPayloadException("field '" + field + "' is outside the supported range");
        }
        return value.shortValue();
    }

    public static Boolean optionalBoolean(JsonNode payload, String field) {
        JsonNode value = payload.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isBoolean()) {
            throw new InvalidPayloadException("field '" + field + "' must be a boolean");
        }
        return value.booleanValue();
    }

    public static String optionalScalarText(JsonNode payload, String field, int maxLength) {
        JsonNode value = payload.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isValueNode()) {
            throw new InvalidPayloadException("field '" + field + "' must be a scalar value");
        }
        String text = value.asText();
        if (text.length() > maxLength) {
            throw new InvalidPayloadException(
                    "field '" + field + "' must be at most " + maxLength + " characters");
        }
        return text;
    }
}
