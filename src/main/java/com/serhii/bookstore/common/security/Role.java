package com.serhii.bookstore.common.security;

/**
 * Shared across {@code auth} (issuance) and {@code catalog}/{@code order}
 * (enforcement) — lives in {@code common}, not {@code auth.domain}, so
 * non-auth domains don't have to depend on the auth package just to check
 * a role.
 */
public enum Role {
    USER, ADMIN
}