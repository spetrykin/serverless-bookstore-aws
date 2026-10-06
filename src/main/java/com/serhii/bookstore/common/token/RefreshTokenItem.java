package com.serhii.bookstore.common.token;

import java.time.Instant;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

/**
 * {@code PK=USER#<userId>, SK=REFRESH#<jti>}. The DynamoDB row backing one
 * issued refresh token; {@code status} is the revocation control point
 * (flipped to {@code REVOKED} on user blocking, week 3).
 */
@DynamoDbBean
public class RefreshTokenItem {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_REVOKED = "REVOKED";

    private String pk;
    private String sk;
    private String status;
    private Instant createdAt;
    private long expiresAt;

    public static String partitionKey(String userId) {
        return "USER#" + userId;
    }

    public static String sortKey(String jti) {
        return "REFRESH#" + jti;
    }

    public static RefreshTokenItem active(String userId, String jti, Instant expiresAt) {
        RefreshTokenItem item = new RefreshTokenItem();
        item.setPk(partitionKey(userId));
        item.setSk(sortKey(jti));
        item.setStatus(STATUS_ACTIVE);
        item.setCreatedAt(Instant.now());
        item.setExpiresAt(expiresAt.getEpochSecond());
        return item;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    /** Epoch seconds — matches template.yaml's TimeToLiveSpecification on {@code expiresAt}. */
    public long getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(long expiresAt) {
        this.expiresAt = expiresAt;
    }
}
