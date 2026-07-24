package com.st00mp.matterhorn.llm;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Request body for the Anthropic Messages API ({@code POST /v1/messages}). */
public record AnthropicRequest(
        String model, @JsonProperty("max_tokens") int maxTokens, List<Message> messages) {}
