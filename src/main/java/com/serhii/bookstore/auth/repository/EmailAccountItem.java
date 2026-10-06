package com.serhii.bookstore.auth.repository;

import java.time.Instant;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

/**
 * {@code PK=EMAIL#<lowercased-email>, SK=ACCOUNT}. Exists purely for atomic
 * email-uniqueness (via TransactWriteItems alongside {@link UserItem}) and a
 * strongly-consistent GetItem lookup path for login, avoiding a GSI query on
 * a security-sensitive path.
 */
@DynamoDbBean
public class EmailAccountItem {

    public static final String SORT_KEY = "ACCOUNT";

    private String pk;
    private String sk;
    private String userId;
    private Instant createdAt;

    public static String partitionKey(String email) {
        return "EMAIL#" + email.toLowerCase();
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

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
