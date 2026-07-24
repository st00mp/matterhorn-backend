package com.st00mp.matterhorn.llm;

/**
 * Test double for {@link LlmClient}: returns a pre-programmed completion instead
 * of calling a real model, or throws a pre-programmed failure to simulate an
 * outage. Lets the suite exercise the extraction chain deterministically,
 * without network access or an API key.
 */
public class FakeLlmClient implements LlmClient {

    private String nextResponse;
    private RuntimeException nextFailure;
    private String lastPrompt;

    /** Programs the raw text the next {@link #complete(String)} call will return. */
    public void willReturn(String response) {
        this.nextResponse = response;
        this.nextFailure = null;
    }

    /** Programs an outage: the next {@link #complete(String)} call will throw. */
    public void willThrow(RuntimeException failure) {
        this.nextFailure = failure;
        this.nextResponse = null;
    }

    /** Clears the programmed response or failure and the recorded prompt, so each test starts clean. */
    public void reset() {
        this.nextResponse = null;
        this.nextFailure = null;
        this.lastPrompt = null;
    }

    @Override
    public String complete(String prompt) {
        this.lastPrompt = prompt;
        if (nextFailure != null) {
            throw nextFailure;
        }
        return nextResponse;
    }

    public String lastPrompt() {
        return lastPrompt;
    }
}
