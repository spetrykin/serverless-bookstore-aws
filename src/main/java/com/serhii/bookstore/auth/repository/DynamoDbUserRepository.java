package com.serhii.bookstore.auth.repository;

import com.serhii.bookstore.auth.domain.User;
import com.serhii.bookstore.auth.exception.DuplicateEmailException;
import com.serhii.bookstore.auth.exception.UserNotFoundException;
import com.serhii.bookstore.common.dynamodb.DynamoDbClientHolder;
import com.serhii.bookstore.common.dynamodb.TableNames;
import com.serhii.bookstore.common.security.Role;
import com.serhii.bookstore.common.web.Page;
import com.serhii.bookstore.common.web.PageCursor;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactDeleteItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactPutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.UpdateItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.CancellationReason;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

@Repository
public class DynamoDbUserRepository implements UserRepository {

    private static final Logger log = LoggerFactory.getLogger(DynamoDbUserRepository.class);
    private static final String CONDITIONAL_CHECK_FAILED = "ConditionalCheckFailed";

    // References the raw DynamoDB attribute name, not the Java bean property —
    // must match UserItem/EmailAccountItem's @DynamoDbAttribute("PK"), not "pk".
    private static final Expression NOT_EXISTS = Expression.builder()
            .expression("attribute_not_exists(PK)")
            .build();
    private static final Expression EXISTS = Expression.builder()
            .expression("attribute_exists(PK)")
            .build();
    // Second condition matters: without it, the scan also matches ORDER#.../REFRESH#... rows
    // living under the same USER#<id> partition.
    private static final Expression USER_PROFILE_FILTER = Expression.builder()
            .expression("begins_with(PK, :prefix) AND SK = :sk")
            .putExpressionValue(":prefix", AttributeValue.builder().s("USER#").build())
            .putExpressionValue(":sk", AttributeValue.builder().s(UserItem.SORT_KEY).build())
            .build();

    private static final TableSchema<UserItem> USER_SCHEMA = TableSchema.fromBean(UserItem.class);
    private static final TableSchema<EmailAccountItem> EMAIL_SCHEMA = TableSchema.fromBean(EmailAccountItem.class);

    private final DynamoDbClientHolder clientHolder;

    public DynamoDbUserRepository(DynamoDbClientHolder clientHolder) {
        this.clientHolder = clientHolder;
    }

    @Override
    public void createUser(User user) {
        UserItem userItem = toItem(user);

        EmailAccountItem emailItem = new EmailAccountItem();
        emailItem.setPk(EmailAccountItem.partitionKey(user.email()));
        emailItem.setSk(EmailAccountItem.SORT_KEY);
        emailItem.setUserId(user.userId());
        emailItem.setCreatedAt(user.createdAt());

        try {
            clientHolder.enhancedClient().transactWriteItems(TransactWriteItemsEnhancedRequest.builder()
                    .addPutItem(userTable(), TransactPutItemEnhancedRequest.builder(UserItem.class)
                            .item(userItem)
                            .conditionExpression(NOT_EXISTS)
                            .build())
                    .addPutItem(emailTable(), TransactPutItemEnhancedRequest.builder(EmailAccountItem.class)
                            .item(emailItem)
                            .conditionExpression(NOT_EXISTS)
                            .build())
                    .build());
        } catch (TransactionCanceledException e) {
            List<CancellationReason> reasons = e.cancellationReasons();
            log.error("createUser transaction cancelled for email={}: cancellationReasons={}",
                    user.email(), reasons, e);
            boolean conditionalCheckFailed = reasons != null && reasons.stream()
                    .anyMatch(reason -> CONDITIONAL_CHECK_FAILED.equals(reason.code()));
            if (conditionalCheckFailed) {
                throw new DuplicateEmailException(user.email());
            }
            throw e;
        }
    }

    @Override
    public Optional<User> findByEmail(String email) {
        EmailAccountItem emailItem = emailTable().getItem(Key.builder()
                .partitionValue(EmailAccountItem.partitionKey(email))
                .sortValue(EmailAccountItem.SORT_KEY)
                .build());
        if (emailItem == null) {
            return Optional.empty();
        }
        return findById(emailItem.getUserId());
    }

    @Override
    public Optional<User> findById(String userId) {
        UserItem item = userTable().getItem(Key.builder()
                .partitionValue(UserItem.partitionKey(userId))
                .sortValue(UserItem.SORT_KEY)
                .build());
        return Optional.ofNullable(item).map(DynamoDbUserRepository::toDomain);
    }

    @Override
    public Page<User> listAll(String cursor, int limit) {
        // Scan + filter, not a GSI — same deliberate pet-project-scale precedent as
        // BookRepository.listAll (architecture-plan.md §6.2).
        software.amazon.awssdk.enhanced.dynamodb.model.Page<UserItem> page = userTable().scan(ScanEnhancedRequest.builder()
                        .filterExpression(USER_PROFILE_FILTER)
                        .exclusiveStartKey(PageCursor.decode(cursor))
                        .limit(limit)
                        .build())
                .iterator().next();
        List<User> users = page.items().stream().map(DynamoDbUserRepository::toDomain).toList();
        return new Page<>(users, PageCursor.encode(page.lastEvaluatedKey()));
    }

    /**
     * Sets only {@code status}, nothing else — {@code ignoreNulls(true)} is required here, the
     * opposite default from {@code DynamoDbBookRepository.updateBook}'s full-replace pattern.
     * Without it, every other null field on this partial {@link UserItem} (email, passwordHash,
     * name, birthday, gender, role, createdAt) would be sent as a DynamoDB {@code REMOVE},
     * wiping the rest of the profile — this method only ever intends to touch one attribute.
     */
    @Override
    public void updateStatus(String userId, User.Status status) {
        UserItem key = new UserItem();
        key.setPk(UserItem.partitionKey(userId));
        key.setSk(UserItem.SORT_KEY);
        key.setStatus(status.name());
        try {
            userTable().updateItem(UpdateItemEnhancedRequest.builder(UserItem.class)
                    .item(key)
                    .ignoreNulls(true)
                    .conditionExpression(EXISTS)
                    .build());
        } catch (ConditionalCheckFailedException e) {
            throw new UserNotFoundException(userId);
        }
    }

    /**
     * {@code GetItem}-then-transact — same TOCTOU shape as {@code DynamoDbBookRepository.updateBook}
     * (architecture-plan.md §6.5): a delete racing between the two calls is caught by the
     * conditional {@code Delete} inside the transaction below, not left unguarded.
     */
    @Override
    public void delete(String userId) {
        UserItem existing = userTable().getItem(Key.builder()
                .partitionValue(UserItem.partitionKey(userId))
                .sortValue(UserItem.SORT_KEY)
                .build());
        if (existing == null) {
            throw new UserNotFoundException(userId);
        }
        try {
            clientHolder.enhancedClient().transactWriteItems(TransactWriteItemsEnhancedRequest.builder()
                    .addDeleteItem(userTable(), TransactDeleteItemEnhancedRequest.builder()
                            .key(Key.builder()
                                    .partitionValue(UserItem.partitionKey(userId))
                                    .sortValue(UserItem.SORT_KEY)
                                    .build())
                            .conditionExpression(EXISTS)
                            .build())
                    .addDeleteItem(emailTable(), TransactDeleteItemEnhancedRequest.builder()
                            .key(Key.builder()
                                    .partitionValue(EmailAccountItem.partitionKey(existing.getEmail()))
                                    .sortValue(EmailAccountItem.SORT_KEY)
                                    .build())
                            .build())
                    .build());
        } catch (TransactionCanceledException e) {
            List<CancellationReason> reasons = e.cancellationReasons();
            log.error("delete user transaction cancelled for userId={}: cancellationReasons={}", userId, reasons, e);
            boolean conditionalCheckFailed = reasons != null && reasons.stream()
                    .anyMatch(reason -> CONDITIONAL_CHECK_FAILED.equals(reason.code()));
            if (conditionalCheckFailed) {
                throw new UserNotFoundException(userId);
            }
            throw e;
        }
    }

    private DynamoDbTable<UserItem> userTable() {
        return clientHolder.enhancedClient().table(TableNames.BOOKSTORE, USER_SCHEMA);
    }

    private DynamoDbTable<EmailAccountItem> emailTable() {
        return clientHolder.enhancedClient().table(TableNames.BOOKSTORE, EMAIL_SCHEMA);
    }

    private static UserItem toItem(User user) {
        UserItem item = new UserItem();
        item.setPk(UserItem.partitionKey(user.userId()));
        item.setSk(UserItem.SORT_KEY);
        item.setUserId(user.userId());
        item.setEmail(user.email());
        item.setPasswordHash(user.passwordHash());
        item.setName(user.name());
        item.setBirthday(user.birthday());
        item.setGender(user.gender());
        item.setStatus(user.status().name());
        item.setRole(user.role().name());
        item.setCreatedAt(user.createdAt());
        return item;
    }

    private static User toDomain(UserItem item) {
        return new User(
                item.getUserId(),
                item.getEmail(),
                item.getPasswordHash(),
                item.getName(),
                item.getBirthday(),
                item.getGender(),
                User.Status.valueOf(item.getStatus()),
                Role.valueOf(item.getRole()),
                item.getCreatedAt());
    }
}
