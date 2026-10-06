package com.serhii.bookstore.recs.engine;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Empirically verifies the claim in {@link MockRecommendationEngine}'s and
 * {@link BedrockRecommendationEngine}'s javadoc — that
 * {@code @ConditionalOnProperty} means only one of the two is ever
 * constructed, never both — rather than trusting Spring's documented
 * behavior secondhand (same discipline as the rest of this project's
 * framework-behavior claims, e.g. architecture-plan.md §5.1's
 * {@code IamPolicyResponse} investigation). Directly answers "is
 * bedrock:InvokeModel ever exercised while RECS_MODE=mock" with a real
 * Spring context, not an inference from reading the annotation.
 */
class RecommendationEngineWiringTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(MockRecommendationEngine.class, BedrockRecommendationEngine.class);

    @Test
    void defaultModeUnsetConstructsOnlyTheMockEngine() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(MockRecommendationEngine.class);
            assertThat(context).doesNotHaveBean(BedrockRecommendationEngine.class);
        });
    }

    @Test
    void explicitMockModeConstructsOnlyTheMockEngine() {
        contextRunner.withPropertyValues("bookstore.recs.mode=mock").run(context -> {
            assertThat(context).hasSingleBean(MockRecommendationEngine.class);
            assertThat(context).doesNotHaveBean(BedrockRecommendationEngine.class);
        });
    }

    @Test
    void bedrockModeConstructsOnlyTheBedrockEngineNotMock() {
        // aws.region is required here for a reason worth recording: BedrockRecommendationEngine's
        // real @PostConstruct runs in this test (unlike BedrockRecommendationEngineTest, which
        // bypasses it via setClientForTesting) — BedrockRuntimeClient.builder().build() resolves
        // its region eagerly at build time, before any real network call, and fails fast with
        // SdkClientException if none is available. Confirmed empirically: this test failed with
        // exactly that exception before this property was added, on this dev machine (no
        // AWS_REGION/profile region set locally) — not a guess. In Lambda this is a non-issue,
        // AWS_REGION is always present in the execution environment; setting it here only
        // reproduces that same condition for this local JVM. No credentials are resolved at
        // build time (DefaultCredentialsProvider resolves lazily on first real call), so this
        // does not make the test perform any real AWS call.
        contextRunner.withSystemProperties("aws.region=eu-central-1")
                .withPropertyValues("bookstore.recs.mode=bedrock")
                .run(context -> {
                    assertThat(context).hasSingleBean(BedrockRecommendationEngine.class);
                    assertThat(context).doesNotHaveBean(MockRecommendationEngine.class);
                });
    }
}
