package com.st00mp.matterhorn.llm;

/** One conversation message sent to the Anthropic Messages API (role: user/assistant). */
public record Message(String role, String content) {}
