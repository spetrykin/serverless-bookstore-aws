package com.serhii.bookstore.common.token;

import com.serhii.bookstore.common.security.Role;
import org.springframework.stereotype.Component;

/**
 * Shared token-pair issuance, used by both registration and login so the
 * two Lambdas don't duplicate "what a token pair looks like."
 */
@Component
public class TokenService {

    private final JwtService jwtService;
    private final RefreshTokenRepository refreshTokenRepository;

    public TokenService(JwtService jwtService, RefreshTokenRepository refreshTokenRepository) {
        this.jwtService = jwtService;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    public TokenPair issueTokenPair(String userId, Role role) {
        String accessToken = jwtService.issueAccessToken(userId, role);
        JwtService.IssuedRefreshToken refreshToken = jwtService.issueRefreshToken(userId);
        refreshTokenRepository.save(
                RefreshTokenItem.active(userId, refreshToken.jti(), refreshToken.expiresAt()));
        return new TokenPair(accessToken, refreshToken.jwt());
    }

    public record TokenPair(String accessToken, String refreshToken) {
    }
}
