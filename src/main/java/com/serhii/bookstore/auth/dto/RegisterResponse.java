package com.serhii.bookstore.auth.dto;

public record RegisterResponse(String userId, String name, String accessToken, String refreshToken) {
}
