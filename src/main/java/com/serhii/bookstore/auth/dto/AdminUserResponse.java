package com.serhii.bookstore.auth.dto;

import com.serhii.bookstore.auth.domain.User;

/**
 * Deliberately excludes {@code passwordHash} — {@link User} the domain
 * record carries it, this response DTO never does. Matches the
 * requirements.md Users-table wireframe columns (Username/Gender/Email)
 * plus {@code userId}/{@code status} so the admin UI has something to
 * target block/unblock/delete against and can show current block state.
 */
public record AdminUserResponse(String userId, String name, String email, String gender, String status) {

    public static AdminUserResponse from(User user) {
        return new AdminUserResponse(user.userId(), user.name(), user.email(), user.gender(), user.status().name());
    }
}