package com.serhii.bookstore.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

class PageCursorTest {

    @Test
    void roundTripsAKey() {
        Map<String, AttributeValue> key = Map.of(
                "PK", AttributeValue.builder().s("BOOK#book-1").build(),
                "SK", AttributeValue.builder().s("PROFILE").build());

        String cursor = PageCursor.encode(key);
        Map<String, AttributeValue> decoded = PageCursor.decode(cursor);

        assertThat(decoded).isEqualTo(key);
    }

    @Test
    void encodeReturnsNullForEmptyOrNullKey() {
        assertThat(PageCursor.encode(null)).isNull();
        assertThat(PageCursor.encode(Map.of())).isNull();
    }

    @Test
    void decodeReturnsNullForBlankCursor() {
        assertThat(PageCursor.decode(null)).isNull();
        assertThat(PageCursor.decode("")).isNull();
    }

    @Test
    void decodeRejectsMalformedCursor() {
        assertThatThrownBy(() -> PageCursor.decode("not-valid-base64!!!"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}