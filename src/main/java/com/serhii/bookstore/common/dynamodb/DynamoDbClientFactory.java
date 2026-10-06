package com.serhii.bookstore.common.dynamodb;

import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * Builds fresh DynamoDB clients on demand. Used both at normal Spring
 * startup and again by {@link com.serhii.bookstore.common.crac.DynamoDbCracResource}
 * after a SnapStart restore, where a brand-new client/credentials-provider
 * pair is required rather than reusing frozen state.
 */
@Component
public class DynamoDbClientFactory {

    public DynamoDbClient createClient() {
        return DynamoDbClient.builder()
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    public DynamoDbEnhancedClient createEnhancedClient(DynamoDbClient client) {
        return DynamoDbEnhancedClient.builder()
                .dynamoDbClient(client)
                .build();
    }
}
