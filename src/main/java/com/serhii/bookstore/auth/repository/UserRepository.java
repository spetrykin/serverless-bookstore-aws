package com.serhii.bookstore.auth.repository;

import com.serhii.bookstore.auth.domain.User;
import com.serhii.bookstore.common.web.Page;
import java.util.Optional;

public interface UserRepository {

    /** Atomically creates the user profile + email-uniqueness pointer. */
    void createUser(User user);

    Optional<User> findByEmail(String email);

    Optional<User> findById(String userId);

    /** Every user profile (Scan + filter, same precedent as {@code BookRepository.listAll} — architecture-plan.md §6.2). */
    Page<User> listAll(String cursor, int limit);

    /** @throws com.serhii.bookstore.auth.exception.UserNotFoundException if {@code userId} doesn't exist. */
    void updateStatus(String userId, User.Status status);

    /**
     * Atomically deletes the user profile + email-uniqueness pointer (mirrors {@link #createUser}
     * in reverse). Orders are deliberately left untouched — see {@code AdminUserDeleteFunction}.
     * @throws com.serhii.bookstore.auth.exception.UserNotFoundException if {@code userId} doesn't exist.
     */
    void delete(String userId);
}
