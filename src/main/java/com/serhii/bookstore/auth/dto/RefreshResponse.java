package com.serhii.bookstore.auth.dto;

/** No refresh-token rotation in week 1 — same refresh token stays valid until its own expiry. */
public record RefreshResponse(String accessToken) {
}
