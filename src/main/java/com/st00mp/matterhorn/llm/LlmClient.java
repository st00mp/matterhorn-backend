package com.st00mp.matterhorn.llm;

/**
 * Port towards a large language model: one prompt in, the raw text completion out.
 * <p>
 * Abstracting the HTTP call behind this interface lets tests substitute a fake
 * returning controlled JSON, so the suite runs without network access or API key.
 */
public interface LlmClient {

    /**
     * Sends the prompt to the model and returns its raw text answer.
     *
     * @param prompt the fully assembled prompt
     * @return the model's completion, as-is (no parsing)
     */
    String complete(String prompt);
}
