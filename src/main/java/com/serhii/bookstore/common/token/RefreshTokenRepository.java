package com.serhii.bookstore.common.token;

import com.serhii.bookstore.common.dynamodb.DynamoDbClientHolder;
import com.serhii.bookstore.common.dynamodb.TableNames;
import java.util.Optional;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

@Component
public class RefreshTokenRepository {

    private static final TableSchema<RefreshTokenItem> SCHEMA = TableSchema.fromBean(RefreshTokenItem.class);
    private static final String REFRESH_SORT_KEY_PREFIX = "REFRESH#";

    private final DynamoDbClientHolder clientHolder;

    public RefreshTokenRepository(DynamoDbClientHolder clientHolder) {
        this.clientHolder = clientHolder;
    }

    public void save(RefreshTokenItem item) {
        table().putItem(item);
    }

    public Optional<RefreshTokenItem> findByUserIdAndJti(String userId, String jti) {
        RefreshTokenItem item = table().getItem(Key.builder()
                .partitionValue(RefreshTokenItem.partitionKey(userId))
                .sortValue(RefreshTokenItem.sortKey(jti))
                .build());
        return Optional.ofNullable(item);
    }

    /**
     * Flips every {@code ACTIVE} refresh-token row for this user to {@code REVOKED} — closes
     * §5.2's week-3 flag: a block now invalidates every outstanding refresh token immediately,
     * not just the next refresh attempt. Bounded by how many sessions one user can plausibly
     * have open (single digits in practice) — not the kind of unbounded loop CLAUDE.md's AWS-call
     * guardrails are aimed at, since this runs once per block, inside one Lambda invocation, not
     * as a retry loop issuing repeated AWS calls — the case covered by the agent's guardrails (CLAUDE.md).
     */
    public void revokeAllActive(String userId) {
        QueryConditional condition = QueryConditional.sortBeginsWith(Key.builder()
                .partitionValue(RefreshTokenItem.partitionKey(userId))
                .sortValue(REFRESH_SORT_KEY_PREFIX)
                .build());
        table().query(condition).items().forEach(item -> {
            if (RefreshTokenItem.STATUS_ACTIVE.equals(item.getStatus())) {
                item.setStatus(RefreshTokenItem.STATUS_REVOKED);
                table().updateItem(item);
            }
        });
    }

    private DynamoDbTable<RefreshTokenItem> table() {
        return clientHolder.enhancedClient().table(TableNames.BOOKSTORE, SCHEMA);
    }
}
