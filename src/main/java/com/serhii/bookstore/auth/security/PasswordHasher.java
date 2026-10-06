package com.serhii.bookstore.auth.security;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import org.springframework.stereotype.Component;

@Component
public class PasswordHasher {

    private final Argon2 argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id);
    private final Argon2Properties properties;

    public PasswordHasher(Argon2Properties properties) {
        this.properties = properties;
    }

    /** Returns an encoded hash (Argon2id params embedded, self-describing). */
    public String hash(String rawPassword) {
        char[] chars = rawPassword.toCharArray();
        try {
            return argon2.hash(properties.iterations(), properties.memoryKb(), properties.parallelism(), chars);
        } finally {
            argon2.wipeArray(chars);
        }
    }

    public boolean verify(String encodedHash, String rawPassword) {
        char[] chars = rawPassword.toCharArray();
        try {
            return argon2.verify(encodedHash, chars);
        } finally {
            argon2.wipeArray(chars);
        }
    }
}
