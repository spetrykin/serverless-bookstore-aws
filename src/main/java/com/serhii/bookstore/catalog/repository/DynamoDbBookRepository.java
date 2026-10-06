package com.serhii.bookstore.catalog.repository;

import com.serhii.bookstore.catalog.domain.Book;
import com.serhii.bookstore.catalog.exception.BookNotFoundException;
import com.serhii.bookstore.common.dynamodb.DynamoDbClientHolder;
import com.serhii.bookstore.common.dynamodb.TableNames;
import com.serhii.bookstore.common.web.Page;
import com.serhii.bookstore.common.web.PageCursor;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.UpdateItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

@Repository
public class DynamoDbBookRepository implements BookRepository {

    // References the raw DynamoDB attribute name, not the Java bean property — same convention as
    // DynamoDbUserRepository (architecture-plan.md §5.11).
    private static final Expression NOT_EXISTS = Expression.builder()
            .expression("attribute_not_exists(PK)")
            .build();
    private static final Expression EXISTS = Expression.builder()
            .expression("attribute_exists(PK)")
            .build();
    private static final Expression BOOK_PREFIX_FILTER = Expression.builder()
            .expression("begins_with(PK, :prefix)")
            .putExpressionValue(":prefix", AttributeValue.builder().s("BOOK#").build())
            .build();

    private static final TableSchema<BookItem> SCHEMA = TableSchema.fromBean(BookItem.class);

    private final DynamoDbClientHolder clientHolder;

    public DynamoDbBookRepository(DynamoDbClientHolder clientHolder) {
        this.clientHolder = clientHolder;
    }

    @Override
    public void createBook(Book book) {
        table().putItem(PutItemEnhancedRequest.builder(BookItem.class)
                .item(toItem(book))
                .conditionExpression(NOT_EXISTS)
                .build());
    }

    @Override
    public Optional<Book> findById(String bookId) {
        BookItem item = table().getItem(Key.builder()
                .partitionValue(BookItem.partitionKey(bookId))
                .sortValue(BookItem.SORT_KEY)
                .build());
        return Optional.ofNullable(item).map(DynamoDbBookRepository::toDomain);
    }

    /**
     * Full replace of every non-key attribute. Reads the existing item first
     * — not purely for existence (the conditional {@code UpdateItem} below
     * still guards that too, as a race-defense) but because
     * {@code UpdateItemEnhancedRequest}'s default {@code ignoreNulls=false}
     * is required for the hide-a-book REMOVE mechanism on
     * {@code GSI1PK}/{@code GSI1SK} (see {@link #toItem}) — that same
     * default would otherwise strip {@code createdAt} too, since callers
     * never resupply it. Both paths — a missing item here, or a
     * same-request delete race caught by the conditional {@code UpdateItem}
     * — converge on the same {@link BookNotFoundException}.
     */
    @Override
    public void updateBook(Book book) {
        BookItem existing = table().getItem(Key.builder()
                .partitionValue(BookItem.partitionKey(book.bookId()))
                .sortValue(BookItem.SORT_KEY)
                .build());
        if (existing == null) {
            throw new BookNotFoundException(book.bookId());
        }
        BookItem updated = toItem(book);
        updated.setCreatedAt(existing.getCreatedAt());
        try {
            table().updateItem(UpdateItemEnhancedRequest.builder(BookItem.class)
                    .item(updated)
                    .conditionExpression(EXISTS)
                    .build());
        } catch (ConditionalCheckFailedException e) {
            throw new BookNotFoundException(book.bookId());
        }
    }

    @Override
    public Page<Book> listVisible(String cursor, int limit) {
        DynamoDbIndex<BookItem> index = table().index(BookItem.GSI1_INDEX_NAME);
        QueryConditional condition = QueryConditional.keyEqualTo(Key.builder()
                .partitionValue(BookItem.GSI1PK_VISIBLE_BOOKS)
                .build());
        software.amazon.awssdk.enhanced.dynamodb.model.Page<BookItem> page = index.query(QueryEnhancedRequest.builder()
                        .queryConditional(condition)
                        .exclusiveStartKey(decodeCursor(cursor))
                        .limit(limit)
                        .build())
                .iterator().next();
        return toDomainPage(page);
    }

    @Override
    public Page<Book> listAll(String cursor, int limit) {
        // Scan + filter, not a second GSI — deliberate for pet-project catalog scale, see architecture-plan.md §6.2.
        software.amazon.awssdk.enhanced.dynamodb.model.Page<BookItem> page = table().scan(ScanEnhancedRequest.builder()
                        .filterExpression(BOOK_PREFIX_FILTER)
                        .exclusiveStartKey(decodeCursor(cursor))
                        .limit(limit)
                        .build())
                .iterator().next();
        return toDomainPage(page);
    }

    private static Page<Book> toDomainPage(software.amazon.awssdk.enhanced.dynamodb.model.Page<BookItem> page) {
        List<Book> books = page.items().stream().map(DynamoDbBookRepository::toDomain).toList();
        return new Page<>(books, PageCursor.encode(page.lastEvaluatedKey()));
    }

    private static Map<String, AttributeValue> decodeCursor(String cursor) {
        return PageCursor.decode(cursor);
    }

    private DynamoDbTable<BookItem> table() {
        return clientHolder.enhancedClient().table(TableNames.BOOKSTORE, SCHEMA);
    }

    private static BookItem toItem(Book book) {
        BookItem item = new BookItem();
        item.setPk(BookItem.partitionKey(book.bookId()));
        item.setSk(BookItem.SORT_KEY);
        item.setBookId(book.bookId());
        item.setName(book.name());
        item.setPriceCents(book.priceCents());
        item.setCount(book.count());
        item.setPhotoUrl(book.photoUrl());
        item.setVisible(book.visible());
        if (book.visible()) {
            item.setGsi1Pk(BookItem.GSI1PK_VISIBLE_BOOKS);
            item.setGsi1Sk(BookItem.gsi1SortKey(book.bookId()));
        }
        // else: leave gsi1Pk/gsi1Sk null — UpdateItemEnhancedRequest's default ignoreNulls=false turns
        // that into a REMOVE, which is what actually drops the item out of GSI1-visible-books.
        item.setCreatedAt(book.createdAt());
        return item;
    }

    private static Book toDomain(BookItem item) {
        return new Book(
                item.getBookId(),
                item.getName(),
                item.getPriceCents(),
                item.getCount(),
                item.getPhotoUrl(),
                Boolean.TRUE.equals(item.getVisible()),
                item.getCreatedAt());
    }
}