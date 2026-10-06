package com.serhii.bookstore.order.service;

import com.serhii.bookstore.order.domain.Order;
import com.serhii.bookstore.order.event.OrderCreatedEvent;
import jakarta.annotation.PostConstruct;
import java.util.concurrent.atomic.AtomicReference;
import org.crac.Context;
import org.crac.Core;
import org.crac.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResultEntry;
import tools.jackson.databind.ObjectMapper;

/**
 * Publishes {@code OrderCreated} to the default EventBridge bus — a
 * best-effort side channel, never the source of truth for the order itself
 * (architecture-plan.md §1.2/§2: by the time this runs, {@code OrderService}
 * has already committed the conditional stock decrement + order write). A
 * publish failure is logged and swallowed here, never rethrown — the order
 * already succeeded from the caller's point of view, and EventBridge is not
 * allowed to roll that back.
 *
 * <p>Owns its own {@link EventBridgeClient} lifecycle (construction +
 * SnapStart rebuild via {@link Resource}) directly, rather than going
 * through a shared Holder/Factory/Config trio the way
 * {@code common.dynamodb.DynamoDbClientHolder} does — that indirection
 * exists there because five different repositories share one DynamoDB
 * client; exactly one class in this project ever talks to EventBridge, so
 * the extra layer isn't earning its keep yet (revisit if a second
 * EventBridge caller shows up).
 *
 * <p>Deliberately skips {@code DynamoDbCracResource}'s eager post-restore
 * primer call (a real API call made on every restore purely to force
 * credential resolution and warm the connection before the first real
 * request). That primer call is what actually requires an IAM permission on
 * every function's cold start (architecture-plan.md §5.10) — DynamoDB's
 * {@code describe-table} earns that cost because literally every function
 * touches DynamoDB one way or another; an EventBridge equivalent would only
 * ever be exercised by {@code OrderCreateFunction}, so paying for it on
 * every function's restore (this class is still an unconditional
 * {@code @Component}, scanned into every function's shared Spring context)
 * isn't justified. The client is still rebuilt from scratch on restore for
 * correctness — a frozen TCP connection/credentials-refresh scheduler is as
 * unusable here as it is for DynamoDB — only the eager warm-up call is
 * skipped.
 *
 * <p><b>Trade-off accepted, not just a gap left implicit:</b> {@link #publish}
 * runs synchronously inside {@code OrderService.placeOrder()}, before the
 * response is returned — so skipping the primer means the first {@code
 * publish()} after a SnapStart restore pays TCP/TLS handshake + credential
 * resolution inline in that specific {@code POST /orders} request (once per
 * snapshot lifetime, not per request; every later call on that same warm
 * container reuses the already-open client). Acceptable specifically
 * because {@link #publish} already swallows every failure (see above) — the
 * worst case from skipping the primer is one order's response being a bit
 * slower, never a wrong or dropped result. Also relies on an unverified
 * assumption carried over from {@code DynamoDbCracResource}'s reasoning:
 * that Lambda's container-credential provider doesn't need the same eager
 * "force resolution now" treatment a longer-lived, independently-refreshing
 * credentials cache would — this hasn't been independently confirmed
 * against AWS's actual Lambda credential-provider behavior, same caveat
 * class as architecture-plan.md §5.15's X-Ray verification step.
 *
 * <p><b>Concrete revisit trigger, not just "needs verification" left
 * abstract:</b> {@link #publish} logs its own {@code durationMicros} on
 * every call (measured with {@code System.nanoTime()} around the SDK call
 * itself, not the whole {@code POST /orders} request) — added at the same
 * time as this decision specifically so the trigger below is checkable
 * from day one, not deferred until §4's X-Ray work lands. After deploy,
 * compare {@code durationMicros} on the first {@code OrderCreated
 * publish succeeded}/{@code failed} log line following a SnapStart
 * {@code RESTORE} against the typical value on an already-warm container
 * (CloudWatch Logs Insights: filter on {@code orderId}/timing around a
 * known restore, or just eyeball consecutive values after a quiet period).
 * If the restore-path call is on the order of 10x (or more) the
 * warm-container baseline — not just "somewhat slower," a clear
 * order-of-magnitude gap — that's the signal this assumption was wrong,
 * and an eager primer call (accepting the IAM cost on
 * {@code OrderCreateFunction} specifically, not project-wide) should be
 * added. Anything short of that order-of-magnitude gap is expected
 * TCP/TLS-handshake cost, not evidence the assumption failed.
 */
@Component
public class OrderEventPublisher implements Resource {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);
    private static final String EVENT_SOURCE = "bookstore.orders";
    private static final String DETAIL_TYPE_ORDER_CREATED = "OrderCreated";

    private final AtomicReference<EventBridgeClient> client = new AtomicReference<>();
    private final ObjectMapper objectMapper;

    public OrderEventPublisher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // Package-private setter, not a second constructor: this class is an unconditional
    // @Component (every function's Spring context constructs it), and a second constructor with
    // no @Autowired to disambiguate breaks Spring's implicit single-constructor injection —
    // confirmed the hard way on a real deploy (NoSuchMethodException: <init>()), not a guess; see
    // docs/incident-log.md. A setter sidesteps the whole ambiguity class instead of requiring an
    // annotation no other class in this project needs.
    /** Test-only: bypasses {@code @PostConstruct}/CRaC registration, injects a client directly. */
    void setClientForTesting(EventBridgeClient client) {
        this.client.set(client);
    }

    @PostConstruct
    void init() {
        client.set(buildClient());
        Core.getGlobalContext().register(this);
    }

    public void publish(Order order) {
        OrderCreatedEvent event = OrderCreatedEvent.from(order);
        // Cheap now, not deferred to §4's X-Ray work: this is the measurement the "concrete revisit
        // trigger" paragraph below needs. publish() goes live with today's deploy; without a number
        // to check, the very first post-restore call this project ever sees — the one moment the
        // trigger could actually fire — would go unmeasured while X-Ray is still just planned.
        long startNanos = System.nanoTime();
        try {
            String detail = objectMapper.writeValueAsString(event);
            PutEventsResponse response = client.get().putEvents(PutEventsRequest.builder()
                    .entries(PutEventsRequestEntry.builder()
                            .source(EVENT_SOURCE)
                            .detailType(DETAIL_TYPE_ORDER_CREATED)
                            .detail(detail)
                            .build())
                    .build());
            long durationMicros = (System.nanoTime() - startNanos) / 1_000;
            if (response.failedEntryCount() != null && response.failedEntryCount() > 0) {
                PutEventsResultEntry failed = response.entries().get(0);
                log.error("OrderCreated publish failed, orderId={}, errorCode={}, errorMessage={}, durationMicros={}",
                        event.orderId(), failed.errorCode(), failed.errorMessage(), durationMicros);
            } else {
                log.info("OrderCreated publish succeeded, orderId={}, durationMicros={}", event.orderId(), durationMicros);
            }
        } catch (Exception e) {
            long durationMicros = (System.nanoTime() - startNanos) / 1_000;
            // Best-effort: the order write already succeeded (see OrderService), never fail the
            // request over a publish problem.
            log.error("OrderCreated publish threw, orderId={}, durationMicros={}", event.orderId(), durationMicros, e);
        }
    }

    @Override
    public void beforeCheckpoint(Context<? extends Resource> context) {
        EventBridgeClient previous = client.get();
        if (previous != null) {
            previous.close();
        }
    }

    @Override
    public void afterRestore(Context<? extends Resource> context) {
        client.set(buildClient());
    }

    private static EventBridgeClient buildClient() {
        return EventBridgeClient.builder()
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
