package com.serhii.bookstore.order.repository;

import java.time.Instant;
import java.util.List;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

/**
 * {@code PK=USER#<userId>, SK=ORDER#<epochMillis>#<uuid>}. The timestamp
 * prefix keeps a plain {@code Query(PK=USER#<id>)} naturally sorted by
 * recency with {@code ScanIndexForward(false)} — no separate GSI needed
 * since orders are only ever listed by their own owner.
 */
@DynamoDbBean
public class OrderItem {

    public static final String SORT_KEY_PREFIX = "ORDER#";

    private String pk;
    private String sk;
    private String orderId;
    private String userId;
    private List<OrderLineItem> lines;
    private Long totalCents;
    private Instant createdAt;

    public static String partitionKey(String userId) {
        return "USER#" + userId;
    }

    public static String sortKey(long epochMillis, String orderId) {
        return SORT_KEY_PREFIX + epochMillis + "#" + orderId;
    }

    @DynamoDbPartitionKey
    @DynamoDbAttribute("PK")
    public String getPk() {
        return pk;
    }

    public void setPk(String pk) {
        this.pk = pk;
    }

    @DynamoDbSortKey
    @DynamoDbAttribute("SK")
    public String getSk() {
        return sk;
    }

    public void setSk(String sk) {
        this.sk = sk;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public List<OrderLineItem> getLines() {
        return lines;
    }

    public void setLines(List<OrderLineItem> lines) {
        this.lines = lines;
    }

    public Long getTotalCents() {
        return totalCents;
    }

    public void setTotalCents(Long totalCents) {
        this.totalCents = totalCents;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}