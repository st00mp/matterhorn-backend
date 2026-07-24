package com.st00mp.matterhorn.llm;

import java.util.List;

/** Response body of the Anthropic Messages API; only the content blocks are mapped. */
public record AnthropicResponse(List<ContentBlock> content) {}
