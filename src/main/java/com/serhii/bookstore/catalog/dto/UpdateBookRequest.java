package com.serhii.bookstore.catalog.dto;

/**
 * PUT is a full replace (matches the wireframe's single edit form/Save
 * button, requirements.md Image 9) — unlike {@link CreateBookRequest},
 * {@code visible} is required here, not defaulted: silently defaulting an
 * omitted field on a full-replace endpoint risks un-hiding a book the admin
 * didn't mean to touch.
 */
public record UpdateBookRequest(String name, Long priceCents, Integer count, String photoUrl, Boolean visible) {
}