package com.serhii.bookstore.events.repository;

import com.serhii.bookstore.common.dynamodb.DynamoDbClientHolder;
import com.serhii.bookstore.common.dynamodb.TableNames;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

@Repository
public class ProcessedEventRepository {

    // References the raw DynamoDB attribute name, not the Java bean property — same convention as
    // DynamoDbUserRepository/DynamoDbBookRepository (architecture-plan.md §5.11).
    private static final Expression NOT_EXISTS = Expression.builder()
            .expression("attribute_not_exists(PK)")
            .build();
    private static final TableSchema<ProcessedEventItem> SCHEMA = TableSchema.fromBean(ProcessedEventItem.class);
    // Retention for idempotency guard rows only — not a business requirement, just table hygiene so
    // these don't accumulate forever (reuses the table's existing TTL attribute/mechanism).
    private static final long RETENTION_DAYS = 30;

    private final DynamoDbClientHolder clientHolder;

    public ProcessedEventRepository(DynamoDbClientHolder clientHolder) {
        this.clientHolder = clientHolder;
    }

    /**
     * @return {@code true} if this call marked the event processed (first delivery seen);
     *         {@code false} if it was already marked (this is a redelivery — the caller must not
     *         reprocess).
     */
    public boolean markProcessedIfFirstDelivery(String orderId, String detailType) {
        ProcessedEventItem item = new ProcessedEventItem();
        item.setPk(ProcessedEventItem.partitionKey(orderId));
        item.setSk(ProcessedEventItem.sortKey(detailType));
        item.setProcessedAt(Instant.now());
        item.setExpiresAt(Instant.now().plus(RETENTION_DAYS, ChronoUnit.DAYS).getEpochSecond());
        try {
            table().putItem(PutItemEnhancedRequest.builder(ProcessedEventItem.class)
                    .item(item)
                    .conditionExpression(NOT_EXISTS)
                    .build());
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }

    private DynamoDbTable<ProcessedEventItem> table() {
        return clientHolder.enhancedClient().table(TableNames.BOOKSTORE, SCHEMA);
    }
}