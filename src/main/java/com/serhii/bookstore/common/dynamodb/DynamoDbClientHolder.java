package com.serhii.bookstore.common.dynamodb;

import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * Mutable indirection in front of the DynamoDB clients. Repositories call
 * {@link #enhancedClient()} per-use instead of holding an injected
 * {@link DynamoDbEnhancedClient} directly, so that
 * {@link com.serhii.bookstore.common.crac.DynamoDbCracResource} can swap in
 * a rebuilt client after a SnapStart restore without any bean re-wiring.
 */
@Component
public class DynamoDbClientHolder {

    private final AtomicReference<DynamoDbClient> rawClient = new AtomicReference<>();
    private final AtomicReference<DynamoDbEnhancedClient> enhancedClient = new AtomicReference<>();

    public DynamoDbClient rawClient() {
        return rawClient.get();
    }

    public DynamoDbEnhancedClient enhancedClient() {
        return enhancedClient.get();
    }

    /**
     * Atomically replaces both clients and closes the previous raw client
     * (releasing its HTTP connection pool).
     */
    public void reset(DynamoDbClient newRawClient, DynamoDbEnhancedClient newEnhancedClient) {
        DynamoDbClient previous = rawClient.getAndSet(newRawClient);
        enhancedClient.set(newEnhancedClient);
        if (previous != null) {
            previous.close();
        }
    }
}