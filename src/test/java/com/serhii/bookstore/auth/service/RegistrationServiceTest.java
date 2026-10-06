package com.serhii.bookstore.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.serhii.bookstore.auth.domain.User;
import com.serhii.bookstore.auth.dto.RegisterRequest;
import com.serhii.bookstore.auth.dto.RegisterResponse;
import com.serhii.bookstore.auth.exception.DuplicateEmailException;
import com.serhii.bookstore.auth.exception.ValidationException;
import com.serhii.bookstore.auth.repository.UserRepository;
import com.serhii.bookstore.auth.security.PasswordHasher;
import com.serhii.bookstore.common.security.Role;
import com.serhii.bookstore.common.token.TokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordHasher passwordHasher;
    @Mock
    private TokenService tokenService;

    private RegistrationService registrationService;

    private static RegisterRequest validRequest() {
        return new RegisterRequest("Test User", "user@example.com", "password123", "password123", "1990-01-01", "other");
    }

    @Test
    void registersAndReturnsTokenPair() {
        registrationService = new RegistrationService(userRepository, passwordHasher, tokenService);
        when(passwordHasher.hash("password123")).thenReturn("hashed");
        when(tokenService.issueTokenPair(anyString(), any(Role.class)))
                .thenReturn(new TokenService.TokenPair("access-token", "refresh-token"));

        RegisterResponse response = registrationService.register(validRequest());

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.userId()).isNotBlank();
        assertThat(response.name()).isEqualTo("Test User");
        verify(userRepository).createUser(any(User.class));
    }

    @Test
    void rejectsMissingName() {
        registrationService = new RegistrationService(userRepository, passwordHasher, tokenService);

        assertThatThrownBy(() -> registrationService.register(
                new RegisterRequest("  ", "user@example.com", "password123", "password123", "1990-01-01", "other")))
                .isInstanceOf(ValidationException.class);
        verifyNoInteractions(userRepository, passwordHasher, tokenService);
    }

    @Test
    void rejectsInvalidEmail() {
        registrationService = new RegistrationService(userRepository, passwordHasher, tokenService);

        assertThatThrownBy(() -> registrationService.register(
                new RegisterRequest("Test User", "not-an-email", "password123", "password123", "1990-01-01", "other")))
                .isInstanceOf(ValidationException.class);
        verifyNoInteractions(userRepository, passwordHasher, tokenService);
    }

    @Test
    void rejectsShortPassword() {
        registrationService = new RegistrationService(userRepository, passwordHasher, tokenService);

        assertThatThrownBy(() -> registrationService.register(
                new RegisterRequest("Test User", "user@example.com", "short", "short", "1990-01-01", "other")))
                .isInstanceOf(ValidationException.class);
        verifyNoInteractions(userRepository, passwordHasher, tokenService);
    }

    @Test
    void rejectsPasswordConfirmationMismatch() {
        registrationService = new RegistrationService(userRepository, passwordHasher, tokenService);

        assertThatThrownBy(() -> registrationService.register(
                new RegisterRequest("Test User", "user@example.com", "password123", "different-password", "1990-01-01", "other")))
                .isInstanceOf(ValidationException.class);
        verifyNoInteractions(userRepository, passwordHasher, tokenService);
    }

    @Test
    void rejectsMissingBirthday() {
        registrationService = new RegistrationService(userRepository, passwordHasher, tokenService);

        assertThatThrownBy(() -> registrationService.register(
                new RegisterRequest("Test User", "user@example.com", "password123", "password123", null, "other")))
                .isInstanceOf(ValidationException.class);
        verifyNoInteractions(userRepository, passwordHasher, tokenService);
    }

    @Test
    void rejectsInvalidBirthdayFormat() {
        registrationService = new RegistrationService(userRepository, passwordHasher, tokenService);

        assertThatThrownBy(() -> registrationService.register(
                new RegisterRequest("Test User", "user@example.com", "password123", "password123", "01/01/1990", "other")))
                .isInstanceOf(ValidationException.class);
        verifyNoInteractions(userRepository, passwordHasher, tokenService);
    }

    @Test
    void rejectsMissingGender() {
        registrationService = new RegistrationService(userRepository, passwordHasher, tokenService);

        assertThatThrownBy(() -> registrationService.register(
                new RegisterRequest("Test User", "user@example.com", "password123", "password123", "1990-01-01", " ")))
                .isInstanceOf(ValidationException.class);
        verifyNoInteractions(userRepository, passwordHasher, tokenService);
    }

    @Test
    void propagatesDuplicateEmail() {
        registrationService = new RegistrationService(userRepository, passwordHasher, tokenService);
        when(passwordHasher.hash(anyString())).thenReturn("hashed");
        org.mockito.Mockito.doThrow(new DuplicateEmailException("user@example.com"))
                .when(userRepository).createUser(any(User.class));

        assertThatThrownBy(() -> registrationService.register(validRequest()))
                .isInstanceOf(DuplicateEmailException.class);
    }
}
