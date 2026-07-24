package com.st00mp.matterhorn.llm;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Shared test configuration substituting the real {@link LlmClient} with a
 * {@link FakeLlmClient}. Import with {@code @Import(FakeLlmTestConfig.class)},
 * then autowire the fake to program its responses.
 */
@TestConfiguration
public class FakeLlmTestConfig {

    @Bean
    @Primary
    FakeLlmClient fakeLlmClient() {
        return new FakeLlmClient();
    }
}
