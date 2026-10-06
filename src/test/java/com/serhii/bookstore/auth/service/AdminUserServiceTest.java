package com.serhii.bookstore.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.serhii.bookstore.auth.domain.User;
import com.serhii.bookstore.auth.dto.AdminUserResponse;
import com.serhii.bookstore.auth.repository.UserRepository;
import com.serhii.bookstore.common.security.Role;
import com.serhii.bookstore.common.token.RefreshTokenRepository;
import com.serhii.bookstore.common.web.Page;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private AdminUserService adminUserService;

    private static User user(String userId) {
        return new User(userId, userId + "@example.com", "hash", "Some Name",
                LocalDate.of(1990, 1, 1), "OTHER", User.Status.ACTIVE, Role.USER, Instant.now());
    }

    @BeforeEach
    void setUp() {
        adminUserService = new AdminUserService(userRepository, refreshTokenRepository);
    }

    @Test
    void listUsersMapsToResponseWithoutPasswordHash() {
        when(userRepository.listAll(null, 20)).thenReturn(new Page<>(List.of(user("user-1")), null));

        Page<AdminUserResponse> page = adminUserService.listUsers(null);

        assertThat(page.items()).hasSize(1);
        AdminUserResponse response = page.items().get(0);
        assertThat(response.userId()).isEqualTo("user-1");
        assertThat(response.status()).isEqualTo("ACTIVE");
        // AdminUserResponse has no passwordHash field at all — compile-time guarantee, not just an
        // assertion, but the fixture uses a distinctive hash value as a sanity anchor regardless.
    }

    @Test
    void blocksUserAndRevokesRefreshTokens() {
        adminUserService.blockUser("admin-1", "user-1");

        verify(userRepository).updateStatus("user-1", User.Status.BLOCKED);
        verify(refreshTokenRepository).revokeAllActive("user-1");
    }

    @Test
    void rejectsSelfBlock() {
        assertThatThrownBy(() -> adminUserService.blockUser("admin-1", "admin-1"))
                .isInstanceOf(com.serhii.bookstore.auth.exception.ValidationException.class);
        verifyNoInteractions(userRepository, refreshTokenRepository);
    }

    @Test
    void unblocksUserWithoutTouchingRefreshTokens() {
        adminUserService.unblockUser("admin-1", "user-1");

        verify(userRepository).updateStatus("user-1", User.Status.ACTIVE);
        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    void rejectsSelfUnblock() {
        assertThatThrownBy(() -> adminUserService.unblockUser("admin-1", "admin-1"))
                .isInstanceOf(com.serhii.bookstore.auth.exception.ValidationException.class);
        verifyNoInteractions(userRepository);
    }

    @Test
    void deletesUser() {
        adminUserService.deleteUser("admin-1", "user-1");

        verify(userRepository).delete("user-1");
    }

    @Test
    void rejectsSelfDelete() {
        assertThatThrownBy(() -> adminUserService.deleteUser("admin-1", "admin-1"))
                .isInstanceOf(com.serhii.bookstore.auth.exception.ValidationException.class);
        verifyNoInteractions(userRepository);
    }
}