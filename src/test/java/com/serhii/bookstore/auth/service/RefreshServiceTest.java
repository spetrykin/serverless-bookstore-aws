package com.serhii.bookstore.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.serhii.bookstore.auth.domain.User;
import com.serhii.bookstore.auth.dto.RefreshRequest;
import com.serhii.bookstore.auth.dto.RefreshResponse;
import com.serhii.bookstore.auth.exception.InvalidTokenException;
import com.serhii.bookstore.auth.exception.UserBlockedException;
import com.serhii.bookstore.auth.repository.UserRepository;
import com.serhii.bookstore.common.security.Role;
import com.serhii.bookstore.common.token.DecodedToken;
import com.serhii.bookstore.common.token.JwtService;
import com.serhii.bookstore.common.token.RefreshTokenItem;
import com.serhii.bookstore.common.token.RefreshTokenRepository;
import io.jsonwebtoken.JwtException;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshServiceTest {

    @Mock
    private JwtService jwtService;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private UserRepository userRepository;

    private RefreshService refreshService;

    @BeforeEach
    void setUp() {
        refreshService = new RefreshService(jwtService, refreshTokenRepository, userRepository);
    }

    private static final String REFRESH_TOKEN = "refresh-jwt";

    private DecodedToken refreshClaims() {
        return new DecodedToken("user-1", "jti-1", JwtService.TOKEN_USE_REFRESH, null, Instant.now().plusSeconds(3600));
    }

    private User activeUser() {
        return new User("user-1", "user@example.com", "hashed", "Test User", java.time.LocalDate.of(1990, 1, 1), "other", User.Status.ACTIVE, Role.USER, Instant.now());
    }

    @Test
    void issuesNewAccessTokenForValidActiveRefreshToken() {
        when(jwtService.verify(REFRESH_TOKEN)).thenReturn(refreshClaims());
        when(refreshTokenRepository.findByUserIdAndJti("user-1", "jti-1"))
                .thenReturn(Optional.of(RefreshTokenItem.active("user-1", "jti-1", Instant.now().plusSeconds(3600))));
        when(userRepository.findById("user-1")).thenReturn(Optional.of(activeUser()));
        when(jwtService.issueAccessToken("user-1", Role.USER)).thenReturn("new-access-token");

        RefreshResponse response = refreshService.refresh(new RefreshRequest(REFRESH_TOKEN));

        assertThat(response.accessToken()).isEqualTo("new-access-token");
    }

    /**
     * Locks in architecture-plan.md §5.2's "role" addendum: refresh already
     * does a live {@code findById} for the blocked-status check, so a role
     * change (e.g. promoted to ADMIN since the last login) is reflected on
     * the very next {@code /refresh}, not just the next {@code /login}.
     */
    @Test
    void newAccessTokenReflectsCurrentRoleFromDatabaseNotOldToken() {
        when(jwtService.verify(REFRESH_TOKEN)).thenReturn(refreshClaims());
        when(refreshTokenRepository.findByUserIdAndJti("user-1", "jti-1"))
                .thenReturn(Optional.of(RefreshTokenItem.active("user-1", "jti-1", Instant.now().plusSeconds(3600))));
        User nowAdmin = new User("user-1", "user@example.com", "hashed", "Test User",
                java.time.LocalDate.of(1990, 1, 1), "other", User.Status.ACTIVE, Role.ADMIN, Instant.now());
        when(userRepository.findById("user-1")).thenReturn(Optional.of(nowAdmin));
        when(jwtService.issueAccessToken("user-1", Role.ADMIN)).thenReturn("new-admin-access-token");

        RefreshResponse response = refreshService.refresh(new RefreshRequest(REFRESH_TOKEN));

        assertThat(response.accessToken()).isEqualTo("new-admin-access-token");
    }

    @Test
    void rejectsInvalidOrExpiredJwt() {
        when(jwtService.verify(REFRESH_TOKEN)).thenThrow(new JwtException("bad signature"));

        assertThatThrownBy(() -> refreshService.refresh(new RefreshRequest(REFRESH_TOKEN)))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void rejectsAccessTokenPresentedAsRefreshToken() {
        when(jwtService.verify(REFRESH_TOKEN))
                .thenReturn(new DecodedToken("user-1", "jti-1", JwtService.TOKEN_USE_ACCESS, Role.USER, Instant.now().plusSeconds(3600)));

        assertThatThrownBy(() -> refreshService.refresh(new RefreshRequest(REFRESH_TOKEN)))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void rejectsUnrecognizedRefreshToken() {
        when(jwtService.verify(REFRESH_TOKEN)).thenReturn(refreshClaims());
        when(refreshTokenRepository.findByUserIdAndJti("user-1", "jti-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshService.refresh(new RefreshRequest(REFRESH_TOKEN)))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void rejectsRevokedRefreshToken() {
        RefreshTokenItem revoked = RefreshTokenItem.active("user-1", "jti-1", Instant.now().plusSeconds(3600));
        revoked.setStatus(RefreshTokenItem.STATUS_REVOKED);
        when(jwtService.verify(REFRESH_TOKEN)).thenReturn(refreshClaims());
        when(refreshTokenRepository.findByUserIdAndJti("user-1", "jti-1")).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> refreshService.refresh(new RefreshRequest(REFRESH_TOKEN)))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void rejectsBlockedUserOnRefresh() {
        when(jwtService.verify(REFRESH_TOKEN)).thenReturn(refreshClaims());
        when(refreshTokenRepository.findByUserIdAndJti("user-1", "jti-1"))
                .thenReturn(Optional.of(RefreshTokenItem.active("user-1", "jti-1", Instant.now().plusSeconds(3600))));
        User blocked = new User("user-1", "user@example.com", "hashed", "Test User", java.time.LocalDate.of(1990, 1, 1), "other", User.Status.BLOCKED, Role.USER, Instant.now());
        when(userRepository.findById("user-1")).thenReturn(Optional.of(blocked));

        assertThatThrownBy(() -> refreshService.refresh(new RefreshRequest(REFRESH_TOKEN)))
                .isInstanceOf(UserBlockedException.class);
    }
}
