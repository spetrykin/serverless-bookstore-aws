package com.serhii.bookstore.common.crac;

import com.serhii.bookstore.common.dynamodb.DynamoDbClientFactory;
import com.serhii.bookstore.common.dynamodb.DynamoDbClientHolder;
import com.serhii.bookstore.common.dynamodb.TableNames;
import jakarta.annotation.PostConstruct;
import org.crac.Context;
import org.crac.Core;
import org.crac.Resource;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;

/**
 * SnapStart state-freezing hook for the DynamoDB client. A live TCP/TLS
 * connection frozen into a snapshot is meaningless after restore (different
 * network state), and a frozen credentials-provider's background refresh
 * scheduling (computed from monotonic time) can wake up wrong. Both are
 * rebuilt from scratch on restore, never reused.
 */
@Component
public class DynamoDbCracResource implements Resource {

    private final DynamoDbClientFactory factory;
    private final DynamoDbClientHolder holder;

    public DynamoDbCracResource(DynamoDbClientFactory factory, DynamoDbClientHolder holder) {
        this.factory = factory;
        this.holder = holder;
    }

    @PostConstruct
    void register() {
        Core.getGlobalContext().register(this);
    }

    @Override
    public void beforeCheckpoint(Context<? extends Resource> context) {
        DynamoDbClient client = holder.rawClient();
        if (client != null) {
            client.close();
        }
    }

    @Override
    public void afterRestore(Context<? extends Resource> context) {
        DynamoDbClient client = factory.createClient();
        holder.reset(client, factory.createEnhancedClient(client));

        // Force credential resolution now so the first real invocation can't
        // race an expired/near-expired token, then prime a fresh connection
        // with a cheap real call so the customer's first request doesn't pay
        // for DNS + TLS + credential validation.
        client.describeTable(DescribeTableRequest.builder()
                .tableName(TableNames.BOOKSTORE)
                .build());
    }
}