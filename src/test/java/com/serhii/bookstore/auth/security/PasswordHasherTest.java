package com.serhii.bookstore.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordHasherTest {

    private final PasswordHasher hasher = new PasswordHasher(new Argon2Properties());

    @Test
    void verifiesCorrectPassword() {
        String hash = hasher.hash("correct-horse-battery-staple");

        assertThat(hasher.verify(hash, "correct-horse-battery-staple")).isTrue();
    }

    @Test
    void rejectsWrongPassword() {
        String hash = hasher.hash("correct-horse-battery-staple");

        assertThat(hasher.verify(hash, "wrong-password")).isFalse();
    }

    @Test
    void producesDifferentHashesForSamePassword() {
        String first = hasher.hash("same-password");
        String second = hasher.hash("same-password");

        assertThat(first).isNotEqualTo(second);
        assertThat(hasher.verify(first, "same-password")).isTrue();
        assertThat(hasher.verify(second, "same-password")).isTrue();
    }
}
