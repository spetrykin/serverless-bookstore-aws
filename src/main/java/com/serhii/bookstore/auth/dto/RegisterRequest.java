package com.serhii.bookstore.auth.dto;

/** {@code birthday} stays a raw wire-format string ("1990-01-01") — parsed/validated in RegistrationService. */
public record RegisterRequest(String name, String email, String password, String confirmPassword, String birthday, String gender) {
}
