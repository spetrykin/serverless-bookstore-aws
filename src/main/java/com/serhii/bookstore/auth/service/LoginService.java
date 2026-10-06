package com.serhii.bookstore.auth.service;

import com.serhii.bookstore.auth.domain.User;
import com.serhii.bookstore.auth.dto.LoginRequest;
import com.serhii.bookstore.auth.dto.LoginResponse;
import com.serhii.bookstore.auth.exception.InvalidCredentialsException;
import com.serhii.bookstore.auth.exception.UserBlockedException;
import com.serhii.bookstore.auth.repository.UserRepository;
import com.serhii.bookstore.auth.security.PasswordHasher;
import com.serhii.bookstore.common.token.TokenService;
import org.springframework.stereotype.Component;

@Component
public class LoginService {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final TokenService tokenService;

    public LoginService(UserRepository userRepository, PasswordHasher passwordHasher, TokenService tokenService) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.tokenService = tokenService;
    }

    public LoginResponse login(LoginRequest request) {
        // Same InvalidCredentialsException for "no such email" and "bad password" — no user enumeration.
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordHasher.verify(user.passwordHash(), request.password())) {
            throw new InvalidCredentialsException();
        }
        if (!user.isActive()) {
            throw new UserBlockedException();
        }

        TokenService.TokenPair tokens = tokenService.issueTokenPair(user.userId(), user.role());
        return new LoginResponse(user.userId(), tokens.accessToken(), tokens.refreshToken());
    }
}
