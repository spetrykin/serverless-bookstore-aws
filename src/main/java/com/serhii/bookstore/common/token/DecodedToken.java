package com.serhii.bookstore.common.token;

import com.serhii.bookstore.common.security.Role;
import java.time.Instant;

/** {@code role} is only present on access tokens (see {@link JwtService#issueAccessToken}); {@code null} for refresh tokens. */
public record DecodedToken(String userId, String jti, String tokenUse, Role role, Instant expiresAt) {
}
