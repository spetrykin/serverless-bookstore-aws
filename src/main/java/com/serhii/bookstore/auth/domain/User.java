package com.serhii.bookstore.auth.domain;

import com.serhii.bookstore.common.security.Role;
import java.time.Instant;
import java.time.LocalDate;

public record User(
        String userId,
        String email,
        String passwordHash,
        String name,
        LocalDate birthday,
        String gender,
        Status status,
        Role role,
        Instant createdAt) {

    public enum Status {
        ACTIVE, BLOCKED
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }
}
