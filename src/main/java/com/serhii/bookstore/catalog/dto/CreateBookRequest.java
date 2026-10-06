package com.serhii.bookstore.catalog.dto;

/** {@code visible} defaults to {@code true} when omitted (a newly added book is shown unless explicitly hidden). */
public record CreateBookRequest(String name, Long priceCents, Integer count, String photoUrl, Boolean visible) {
}