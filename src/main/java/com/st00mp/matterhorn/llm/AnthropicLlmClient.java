package com.st00mp.matterhorn.llm;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * {@link LlmClient} adapter for the Anthropic Messages API.
 * <p>
 * Plain HTTP via Spring {@link RestClient} — deliberately no SDK, no AI framework:
 * a single POST to {@code /v1/messages} is all that is needed. The key is injected
 * through the {@code anthropic.api.key} property, which the committed configuration
 * resolves from the {@code ANTHROPIC_API_KEY} environment variable; local overrides
 * belong in the git-ignored {@code application-local.properties}. No key is ever
 * committed. The model is configurable through {@code anthropic.model}.
 */
@Component
public class AnthropicLlmClient implements LlmClient {

    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final int MAX_TOKENS = 1024;

    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public AnthropicLlmClient(
            RestClient.Builder restClientBuilder,
            @Value("${anthropic.api.key}") String apiKey,
            @Value("${anthropic.model}") String model) {
        this.restClient = restClientBuilder.baseUrl("https://api.anthropic.com").build();
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public String complete(String prompt) {
        AnthropicRequest request = new AnthropicRequest(model, MAX_TOKENS, List.of(new Message("user", prompt)));

        AnthropicResponse response = restClient
                .post()
                .uri("/v1/messages")
                .header("x-api-key", apiKey)
                .header("anthropic-version", ANTHROPIC_VERSION)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(AnthropicResponse.class);

        if (response == null || response.content() == null || response.content().isEmpty()) {
            throw new IllegalStateException("Empty response from Anthropic API");
        }
        return response.content().getFirst().text();
    }
}
