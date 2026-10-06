package com.serhii.bookstore.common.dynamodb;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@Component
public class DynamoDbConfig {

    private final DynamoDbClientFactory factory;
    private final DynamoDbClientHolder holder;

    public DynamoDbConfig(DynamoDbClientFactory factory, DynamoDbClientHolder holder) {
        this.factory = factory;
        this.holder = holder;
    }

    @PostConstruct
    void initClients() {
        DynamoDbClient client = factory.createClient();
        holder.reset(client, factory.createEnhancedClient(client));
    }
}