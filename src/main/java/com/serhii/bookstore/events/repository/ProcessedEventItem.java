package com.serhii.bookstore.events.repository;

import java.time.Instant;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

/**
 * {@code PK=EVENT#<orderId>, SK=PROCESSED#<detailType>}. Idempotency guard
 * row for EventBridge consumers — EventBridge + Lambda is at-least-once
 * delivery (architecture-plan.md §1.4), so a conditional {@code PutItem} on
 * this key is what actually prevents double-processing a redelivered event,
 * not just "unlikely to happen in practice."
 */
@DynamoDbBean
public class ProcessedEventItem {

    private String pk;
    private String sk;
    private Instant processedAt;
    private long expiresAt;

    public static String partitionKey(String orderId) {
        return "EVENT#" + orderId;
    }

    public static String sortKey(String detailType) {
        return "PROCESSED#" + detailType;
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

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }

    /** Epoch seconds — matches template.yaml's TimeToLiveSpecification on {@code expiresAt}. */
    public long getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(long expiresAt) {
        this.expiresAt = expiresAt;
    }
}