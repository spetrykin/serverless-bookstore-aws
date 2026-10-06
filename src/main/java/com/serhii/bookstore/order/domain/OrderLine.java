package com.serhii.bookstore.order.domain;

/** {@code name}/{@code priceCents} are snapshots taken at order time — never re-derived from the live Book item (requirements.md: order prices are immutable). */
public record OrderLine(String bookId, String name, long priceCents, int quantity) {

    public long lineTotalCents() {
        return priceCents * quantity;
    }
}