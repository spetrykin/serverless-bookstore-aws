package com.serhii.bookstore.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.serhii.bookstore.auth.domain.User;
import com.serhii.bookstore.auth.dto.LoginRequest;
import com.serhii.bookstore.auth.dto.LoginResponse;
import com.serhii.bookstore.auth.exception.InvalidCredentialsException;
import com.serhii.bookstore.auth.exception.UserBlockedException;
import com.serhii.bookstore.auth.repository.UserRepository;
import com.serhii.bookstore.auth.security.PasswordHasher;
import com.serhii.bookstore.common.security.Role;
import com.serhii.bookstore.common.token.TokenService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordHasher passwordHasher;
    @Mock
    private TokenService tokenService;

    private LoginService loginService;

    @BeforeEach
    void setUp() {
        loginService = new LoginService(userRepository, passwordHasher, tokenService);
    }

    private User activeUser() {
        return new User("user-1", "user@example.com", "hashed", "Test User", java.time.LocalDate.of(1990, 1, 1), "other", User.Status.ACTIVE, Role.USER, Instant.now());
    }

    @Test
    void logsInWithCorrectPassword() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(activeUser()));
        when(passwordHasher.verify("hashed", "password123")).thenReturn(true);
        when(tokenService.issueTokenPair("user-1", Role.USER))
                .thenReturn(new TokenService.TokenPair("access-token", "refresh-token"));

        LoginResponse response = loginService.login(new LoginRequest("user@example.com", "password123"));

        assertThat(response.userId()).isEqualTo("user-1");
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
    }

    @Test
    void rejectsUnknownEmail() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loginService.login(new LoginRequest("nobody@example.com", "password123")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void rejectsWrongPassword() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(activeUser()));
        when(passwordHasher.verify("hashed", "wrong-password")).thenReturn(false);

        assertThatThrownBy(() -> loginService.login(new LoginRequest("user@example.com", "wrong-password")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void rejectsBlockedUserEvenWithCorrectPassword() {
        User blockedUser = new User("user-1", "user@example.com", "hashed", "Test User", java.time.LocalDate.of(1990, 1, 1), "other", User.Status.BLOCKED, Role.USER, Instant.now());
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(blockedUser));
        when(passwordHasher.verify("hashed", "password123")).thenReturn(true);

        assertThatThrownBy(() -> loginService.login(new LoginRequest("user@example.com", "password123")))
                .isInstanceOf(UserBlockedException.class);
    }
}
