package com.serhii.bookstore.auth.dto;

public record LoginResponse(String userId, String accessToken, String refreshToken) {
}
