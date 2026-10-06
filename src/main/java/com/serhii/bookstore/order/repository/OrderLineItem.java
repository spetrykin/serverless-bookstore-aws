package com.serhii.bookstore.order.repository;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

/** Nested document, not a top-level item — no PK/SK, embedded as a list inside {@link OrderItem}. */
@DynamoDbBean
public class OrderLineItem {

    private String bookId;
    private String name;
    private Long priceCents;
    private Integer quantity;

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

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}