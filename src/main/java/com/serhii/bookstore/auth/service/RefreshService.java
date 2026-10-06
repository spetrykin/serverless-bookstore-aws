package com.serhii.bookstore.auth.service;

import com.serhii.bookstore.auth.domain.User;
import com.serhii.bookstore.auth.dto.RefreshRequest;
import com.serhii.bookstore.auth.dto.RefreshResponse;
import com.serhii.bookstore.auth.exception.InvalidTokenException;
import com.serhii.bookstore.auth.exception.UserBlockedException;
import com.serhii.bookstore.auth.repository.UserRepository;
import com.serhii.bookstore.common.token.DecodedToken;
import com.serhii.bookstore.common.token.JwtService;
import com.serhii.bookstore.common.token.RefreshTokenItem;
import com.serhii.bookstore.common.token.RefreshTokenRepository;
import io.jsonwebtoken.JwtException;
import org.springframework.stereotype.Component;

@Component
public class RefreshService {

    private final JwtService jwtService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    public RefreshService(JwtService jwtService, RefreshTokenRepository refreshTokenRepository, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
    }

    /** No rotation in week 1 — mints a new access token only, refresh token is reused until it naturally expires. */
    public RefreshResponse refresh(RefreshRequest request) {
        DecodedToken decoded;
        try {
            decoded = jwtService.verify(request.refreshToken());
        } catch (JwtException e) {
            throw new InvalidTokenException("Refresh token is invalid or expired");
        }
        if (!JwtService.TOKEN_USE_REFRESH.equals(decoded.tokenUse())) {
            throw new InvalidTokenException("Not a refresh token");
        }

        RefreshTokenItem item = refreshTokenRepository.findByUserIdAndJti(decoded.userId(), decoded.jti())
                .orElseThrow(() -> new InvalidTokenException("Refresh token not recognized"));
        if (!RefreshTokenItem.STATUS_ACTIVE.equals(item.getStatus())) {
            throw new InvalidTokenException("Refresh token has been revoked");
        }

        // Extension point for week-3 blocking: this lookup is what makes a
        // block take effect on the next refresh, without any extra plumbing.
        User user = userRepository.findById(decoded.userId())
                .orElseThrow(() -> new InvalidTokenException("User not found"));
        if (!user.isActive()) {
            throw new UserBlockedException();
        }

        return new RefreshResponse(jwtService.issueAccessToken(user.userId(), user.role()));
    }
}
