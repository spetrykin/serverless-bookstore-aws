package com.serhii.bookstore.catalog.repository;

import java.time.Instant;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

/**
 * {@code PK=BOOK#<bookId>, SK=PROFILE}. {@code gsi1Pk}/{@code gsi1Sk} are
 * only set when {@code visible=true} — a GSI only projects items carrying
 * both its key attributes, so leaving them {@code null} (not just flipping
 * {@code visible} to {@code false}) is what actually drops a hidden book out
 * of {@code GSI1-visible-books} (architecture-plan.md §1.3/§2, §6.2).
 */
@DynamoDbBean
public class BookItem {

    public static final String SORT_KEY = "PROFILE";
    public static final String GSI1_INDEX_NAME = "GSI1-visible-books";
    public static final String GSI1PK_VISIBLE_BOOKS = "CATALOG#BOOKS";

    private String pk;
    private String sk;
    private String bookId;
    private String name;
    private Long priceCents;
    private Integer count;
    private String photoUrl;
    private Boolean visible;
    private String gsi1Pk;
    private String gsi1Sk;
    private Instant createdAt;

    public static String partitionKey(String bookId) {
        return "BOOK#" + bookId;
    }

    public static String gsi1SortKey(String bookId) {
        return "BOOK#" + bookId;
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

    public String getBookId() {
        return bookId;
    }

    public void setBookId(String bookId) {
        this.bookId = bookId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getPriceCents() {
        return priceCents;
    }

    public void setPriceCents(Long priceCents) {
        this.priceCents = priceCents;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    public Boolean getVisible() {
        return visible;
    }

    public void setVisible(Boolean visible) {
        this.visible = visible;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = GSI1_INDEX_NAME)
    @DynamoDbAttribute("GSI1PK")
    public String getGsi1Pk() {
        return gsi1Pk;
    }

    public void setGsi1Pk(String gsi1Pk) {
        this.gsi1Pk = gsi1Pk;
    }

    @DynamoDbSecondarySortKey(indexNames = GSI1_INDEX_NAME)
    @DynamoDbAttribute("GSI1SK")
    public String getGsi1Sk() {
        return gsi1Sk;
    }

    public void setGsi1Sk(String gsi1Sk) {
        this.gsi1Sk = gsi1Sk;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}