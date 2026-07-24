package com.st00mp.matterhorn.llm;

/** One content block of an Anthropic response; {@code text} holds the completion. */
public record ContentBlock(String type, String text) {}
