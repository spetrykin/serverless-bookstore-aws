package com.serhii.bookstore.common.web;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/**
 * Opaque pagination cursor — base64 round-trip of DynamoDB's
 * LastEvaluatedKey/ExclusiveStartKey. Every key attribute this project
 * paginates over (PK, SK, GSI1PK, GSI1SK) is string-typed (see
 * template.yaml AttributeDefinitions), so this only needs to handle
 * String-valued keys, not the general AttributeValue union.
 */
public final class PageCursor {

    private PageCursor() {
    }

    public static String encode(Map<String, AttributeValue> lastEvaluatedKey) {
        if (lastEvaluatedKey == null || lastEvaluatedKey.isEmpty()) {
            return null;
        }
        StringBuilder raw = new StringBuilder();
        lastEvaluatedKey.forEach((attributeName, value) -> raw.append(attributeName).append('=').append(value.s()).append('\n'));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** @throws IllegalArgumentException if {@code cursor} isn't a value this class produced. */
    public static Map<String, AttributeValue> decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
        Map<String, AttributeValue> key = new LinkedHashMap<>();
        for (String line : raw.split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            int separator = line.indexOf('=');
            if (separator < 0) {
                throw new IllegalArgumentException("Malformed pagination cursor");
            }
            key.put(line.substring(0, separator), AttributeValue.builder().s(line.substring(separator + 1)).build());
        }
        return key;
    }
}