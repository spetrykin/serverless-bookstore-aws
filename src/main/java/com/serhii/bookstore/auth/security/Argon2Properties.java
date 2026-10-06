package com.serhii.bookstore.auth.security;

import org.springframework.stereotype.Component;

/**
 * OWASP's current Argon2id minimum (m=19456 KiB, t=2, p=1) rather than the
 * library's own default (64 MiB) — the Lambda only has 512 MB total
 * (template.yaml Globals), shared with the JVM heap and Spring context, so
 * headroom matters more here than on a dedicated server. Needs a real
 * cold-start/latency measurement once deployed; not just copy-pasted.
 */
@Component
public class Argon2Properties {

    public int memoryKb() {
        return 19456;
    }

    public int iterations() {
        return 2;
    }

    public int parallelism() {
        return 1;
    }
}
