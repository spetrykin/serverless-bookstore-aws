package com.serhii.bookstore.auth.service;

import com.serhii.bookstore.auth.domain.User;
import com.serhii.bookstore.auth.dto.RegisterRequest;
import com.serhii.bookstore.auth.dto.RegisterResponse;
import com.serhii.bookstore.auth.exception.ValidationException;
import com.serhii.bookstore.auth.repository.UserRepository;
import com.serhii.bookstore.auth.security.PasswordHasher;
import com.serhii.bookstore.common.security.Role;
import com.serhii.bookstore.common.token.TokenService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RegistrationService {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final TokenService tokenService;

    public RegistrationService(UserRepository userRepository, PasswordHasher passwordHasher, TokenService tokenService) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.tokenService = tokenService;
    }

    /** Auto-login: returns a token pair immediately, no separate /login call needed. */
    public RegisterResponse register(RegisterRequest request) {
        validate(request);

        String userId = UUID.randomUUID().toString();
        User user = new User(
                userId,
                request.email(),
                passwordHasher.hash(request.password()),
                request.name(),
                LocalDate.parse(request.birthday()),
                request.gender(),
                User.Status.ACTIVE,
                Role.USER,
                Instant.now());

        userRepository.createUser(user);

        TokenService.TokenPair tokens = tokenService.issueTokenPair(userId, user.role());
        return new RegisterResponse(userId, user.name(), tokens.accessToken(), tokens.refreshToken());
    }

    /**
     * {@code birthday} is checked for format only, per docs/requirements.md
     * ("correct format") — a "must be in the past" business rule is
     * deliberately deferred, not omitted by oversight (see
     * architecture-plan.md §5.15).
     */
    private void validate(RegisterRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new ValidationException("Name is required");
        }
        if (request.email() == null || !request.email().contains("@")) {
            throw new ValidationException("A valid email is required");
        }
        if (request.password() == null || request.password().length() < 8) {
            throw new ValidationException("Password must be at least 8 characters");
        }
        if (!request.password().equals(request.confirmPassword())) {
            throw new ValidationException("Password and confirmation do not match");
        }
        if (request.birthday() == null || request.birthday().isBlank()) {
            throw new ValidationException("Birthday is required");
        }
        try {
            LocalDate.parse(request.birthday());
        } catch (DateTimeParseException e) {
            throw new ValidationException("Birthday must be a valid date (yyyy-MM-dd)");
        }
        if (request.gender() == null || request.gender().isBlank()) {
            throw new ValidationException("Gender is required");
        }
    }
}
