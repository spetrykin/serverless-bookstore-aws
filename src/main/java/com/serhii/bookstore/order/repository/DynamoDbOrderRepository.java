package com.serhii.bookstore.order.repository;

import com.serhii.bookstore.catalog.repository.BookItem;
import com.serhii.bookstore.common.dynamodb.DynamoDbClientHolder;
import com.serhii.bookstore.common.dynamodb.TableNames;
import com.serhii.bookstore.common.web.Page;
import com.serhii.bookstore.common.web.PageCursor;
import com.serhii.bookstore.order.domain.Order;
import com.serhii.bookstore.order.domain.OrderLine;
import com.serhii.bookstore.order.exception.InsufficientStockException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.CancellationReason;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;
import software.amazon.awssdk.services.dynamodb.model.Update;

/**
 * Book stock decrement needs a true atomic {@code SET count = count - :qty}
 * (server-side arithmetic), not "read current count, compute new count,
 * write it" — the latter is a lost-update race even with a correct
 * quantity-available condition check (concurrent order A and B can both
 * read count=5, both pass "count >= 2", and both blindly SET count=3,
 * silently under-counting the real decrement). DynamoDbEnhancedClient's
 * bean-based {@code updateItem} can only SET the bean's absolute field
 * values, not "attribute = attribute - value" — so this repository drops to
 * the low-level {@link DynamoDbClientHolder#rawClient()} for the
 * transaction, unlike every other repository in this project.
 */
@Repository
public class DynamoDbOrderRepository implements OrderRepository {

    private static final Logger log = LoggerFactory.getLogger(DynamoDbOrderRepository.class);
    private static final String CONDITIONAL_CHECK_FAILED = "ConditionalCheckFailed";

    private static final TableSchema<OrderItem> ORDER_SCHEMA = TableSchema.fromBean(OrderItem.class);

    private final DynamoDbClientHolder clientHolder;

    public DynamoDbOrderRepository(DynamoDbClientHolder clientHolder) {
        this.clientHolder = clientHolder;
    }

    @Override
    public void placeOrder(Order order) {
        List<TransactWriteItem> transactItems = new ArrayList<>();
        for (OrderLine line : order.lines()) {
            transactItems.add(TransactWriteItem.builder()
                    .update(Update.builder()
                            .tableName(TableNames.BOOKSTORE)
                            .key(Map.of(
                                    "PK", AttributeValue.builder().s(BookItem.partitionKey(line.bookId())).build(),
                                    "SK", AttributeValue.builder().s(BookItem.SORT_KEY).build()))
                            .updateExpression("SET #count = #count - :qty")
                            .conditionExpression("attribute_exists(PK) AND #count >= :qty")
                            .expressionAttributeNames(Map.of("#count", "count"))
                            .expressionAttributeValues(Map.of(
                                    ":qty", AttributeValue.builder().n(String.valueOf(line.quantity())).build()))
                            .build())
                    .build());
        }
        transactItems.add(TransactWriteItem.builder()
                .put(Put.builder()
                        .tableName(TableNames.BOOKSTORE)
                        .item(ORDER_SCHEMA.itemToMap(toItem(order), false))
                        .conditionExpression("attribute_not_exists(PK)")
                        .build())
                .build());

        try {
            clientHolder.rawClient().transactWriteItems(TransactWriteItemsRequest.builder()
                    .transactItems(transactItems)
                    .build());
        } catch (TransactionCanceledException e) {
            List<CancellationReason> reasons = e.cancellationReasons();
            log.error("placeOrder transaction cancelled for userId={}, orderId={}: cancellationReasons={}",
                    order.userId(), order.orderId(), reasons, e);
            boolean stockFailure = reasons != null && reasons.stream()
                    .anyMatch(reason -> CONDITIONAL_CHECK_FAILED.equals(reason.code()));
            if (stockFailure) {
                throw new InsufficientStockException();
            }
            throw e;
        }
    }

    @Override
    public Page<Order> findByUserId(String userId, String cursor, int limit) {
        QueryConditional condition = QueryConditional.sortBeginsWith(Key.builder()
                .partitionValue(OrderItem.partitionKey(userId))
                .sortValue(OrderItem.SORT_KEY_PREFIX)
                .build());
        software.amazon.awssdk.enhanced.dynamodb.model.Page<OrderItem> page = table().query(QueryEnhancedRequest.builder()
                        .queryConditional(condition)
                        .scanIndexForward(false)
                        .exclusiveStartKey(PageCursor.decode(cursor))
                        .limit(limit)
                        .build())
                .iterator().next();
        List<Order> orders = page.items().stream().map(DynamoDbOrderRepository::toDomain).toList();
        return new Page<>(orders, PageCursor.encode(page.lastEvaluatedKey()));
    }

    private DynamoDbTable<OrderItem> table() {
        return clientHolder.enhancedClient().table(TableNames.BOOKSTORE, ORDER_SCHEMA);
    }

    private static OrderItem toItem(Order order) {
        OrderItem item = new OrderItem();
        item.setPk(OrderItem.partitionKey(order.userId()));
        item.setSk(OrderItem.sortKey(order.createdAt().toEpochMilli(), order.orderId()));
        item.setOrderId(order.orderId());
        item.setUserId(order.userId());
        item.setLines(order.lines().stream().map(DynamoDbOrderRepository::toLineItem).toList());
        item.setTotalCents(order.totalCents());
        item.setCreatedAt(order.createdAt());
        return item;
    }

    private static OrderLineItem toLineItem(OrderLine line) {
        OrderLineItem lineItem = new OrderLineItem();
        lineItem.setBookId(line.bookId());
        lineItem.setName(line.name());
        lineItem.setPriceCents(line.priceCents());
        lineItem.setQuantity(line.quantity());
        return lineItem;
    }

    private static Order toDomain(OrderItem item) {
        List<OrderLine> lines = item.getLines().stream()
                .map(lineItem -> new OrderLine(lineItem.getBookId(), lineItem.getName(), lineItem.getPriceCents(), lineItem.getQuantity()))
                .toList();
        return new Order(item.getOrderId(), item.getUserId(), lines, item.getTotalCents(), item.getCreatedAt());
    }
}