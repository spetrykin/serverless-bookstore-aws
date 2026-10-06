package com.serhii.bookstore.auth.service;

import com.serhii.bookstore.auth.domain.User;
import com.serhii.bookstore.auth.dto.AdminUserResponse;
import com.serhii.bookstore.auth.exception.ValidationException;
import com.serhii.bookstore.auth.repository.UserRepository;
import com.serhii.bookstore.common.token.RefreshTokenRepository;
import com.serhii.bookstore.common.web.Page;
import org.springframework.stereotype.Component;

/** Admin user management — list, block, unblock, delete (architecture-plan.md §5.2). */
@Component
public class AdminUserService {

    private static final int PAGE_SIZE = 20;

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    public AdminUserService(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    public Page<AdminUserResponse> listUsers(String cursor) {
        Page<User> page;
        try {
            page = userRepository.listAll(cursor, PAGE_SIZE);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Invalid pagination cursor");
        }
        return new Page<>(page.items().stream().map(AdminUserResponse::from).toList(), page.nextCursor());
    }

    public void blockUser(String requestingAdminUserId, String targetUserId) {
        requireNotSelf(requestingAdminUserId, targetUserId, "block");
        userRepository.updateStatus(targetUserId, User.Status.BLOCKED);
        // Closes §5.2's week-3 flag: makes every outstanding refresh token unusable immediately,
        // not just the next refresh attempt (RefreshService's live user.isActive() check already
        // covers that incidentally — this makes the revocation state explicit/authoritative in
        // its own right).
        refreshTokenRepository.revokeAllActive(targetUserId);
    }

    public void unblockUser(String requestingAdminUserId, String targetUserId) {
        requireNotSelf(requestingAdminUserId, targetUserId, "unblock");
        userRepository.updateStatus(targetUserId, User.Status.ACTIVE);
        // Deliberately does NOT restore the REVOKED refresh-token rows the block step left behind
        // — an unblocked user logs in fresh and gets new tokens; resurrecting old revoked sessions
        // isn't the expected semantics of "unblock".
    }

    public void deleteUser(String requestingAdminUserId, String targetUserId) {
        requireNotSelf(requestingAdminUserId, targetUserId, "delete");
        userRepository.delete(targetUserId);
    }

    private void requireNotSelf(String requestingAdminUserId, String targetUserId, String action) {
        if (requestingAdminUserId.equals(targetUserId)) {
            throw new ValidationException("Cannot " + action + " your own account");
        }
    }
}