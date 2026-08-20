package com.rustdeskapi.server.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SensitiveJsonSanitizer {

    private static final ObjectMapper JSON_MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();
    private static final TextNode REDACTED = TextNode.valueOf("[REDACTED]");

    private SensitiveJsonSanitizer() {
    }

    public static JsonNode sanitize(JsonNode source) {
        JsonNode copy = source.deepCopy();
        return sanitizeNode(copy);
    }

    private static JsonNode sanitizeNode(JsonNode node) {
        if (node instanceof ObjectNode object) {
            List<String> fieldNames = new ArrayList<>();
            object.fieldNames().forEachRemaining(fieldNames::add);
            for (String fieldName : fieldNames) {
                if (isSensitive(fieldName)) {
                    object.set(fieldName, REDACTED);
                } else {
                    object.set(fieldName, sanitizeNode(object.get(fieldName)));
                }
            }
        } else if (node instanceof ArrayNode array) {
            for (int index = 0; index < array.size(); index++) {
                array.set(index, sanitizeNode(array.get(index)));
            }
        } else if (node instanceof TextNode text) {
            return sanitizeJsonString(text);
        }
        return node;
    }

    private static JsonNode sanitizeJsonString(TextNode text) {
        try {
            JsonNode parsed = JSON_MAPPER.readTree(text.textValue());
            if (parsed == null || (!parsed.isObject() && !parsed.isArray())) {
                return text;
            }
            return TextNode.valueOf(JSON_MAPPER.writeValueAsString(sanitizeNode(parsed)));
        } catch (JsonProcessingException exception) {
            return text;
        }
    }

    private static boolean isSensitive(String fieldName) {
        String normalized = fieldName.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
        return normalized.endsWith("password")
                || normalized.endsWith("secret")
                || normalized.endsWith("token")
                || normalized.endsWith("authorization")
                || normalized.endsWith("cookie")
                || normalized.endsWith("privatekey")
                || normalized.endsWith("apikey")
                || normalized.endsWith("credential")
                || normalized.endsWith("pwd")
                || normalized.endsWith("passphrase");
    }
}
