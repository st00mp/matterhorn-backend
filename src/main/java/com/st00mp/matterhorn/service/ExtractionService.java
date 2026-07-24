package com.st00mp.matterhorn.service;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.st00mp.matterhorn.entity.ExtractedVariable;
import com.st00mp.matterhorn.entity.IncomingMail;
import com.st00mp.matterhorn.entity.QuoteRequest;
import com.st00mp.matterhorn.llm.LlmClient;
import com.st00mp.matterhorn.repository.ExtractedVariableRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Extracts prospect variables from an incoming mail via the LLM and persists them
 * as {@link ExtractedVariable}s linked to the mail's {@link QuoteRequest}.
 * <p>
 * The extractor prompt is a versioned system component
 * ({@code src/main/resources/prompt/extractor.md}); the mail body is injected into
 * its {@code {{mail_body}}} placeholder. The LLM must answer with a JSON array of
 * {@code {key, value, quote}} objects.
 * <p>
 * Provenance is decided deterministically, never by the LLM (anti-hallucination
 * guard): a variable is {@code SOURCED} only if its quote exists verbatim as a
 * substring of the mail body. A value without a valid quote is downgraded to
 * {@code ASSUMED}; no value at all means {@code MISSING} (value persisted as null).
 */
@Service
public class ExtractionService {

    record LlmVariable(String key, String value, String quote) {}

    private final AssemblyService assemblyService;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final ExtractedVariableRepository extractedVariableRepository;
    private final String promptTemplate;

    public ExtractionService(
            AssemblyService assemblyService,
            LlmClient llmClient,
            ObjectMapper objectMapper,
            ExtractedVariableRepository extractedVariableRepository,
            @Value("classpath:prompt/extractor.md") Resource promptFile) {
        this.assemblyService = assemblyService;
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.extractedVariableRepository = extractedVariableRepository;
        try {
            this.promptTemplate = promptFile.getContentAsString(UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read extractor prompt", e);
        }
    }

    private ExtractedVariable.Provenance resolveProvenance(String mailBody, LlmVariable llmVariable) {

        if (llmVariable.value() == null || llmVariable.value().isBlank()) {
            return ExtractedVariable.Provenance.MISSING;
        }

        if (llmVariable.quote() != null && !llmVariable.quote().isBlank() && mailBody.contains(llmVariable.quote())) {
            return ExtractedVariable.Provenance.SOURCED;
        }
        return ExtractedVariable.Provenance.ASSUMED;
    }

    /**
     * Runs the extraction chain for one mail: assembles the prompt, calls the LLM,
     * parses the JSON answer, resolves each variable's provenance against the mail
     * body, then persists the variables.
     *
     * @param quoteRequest the quote request the variables are attached to
     * @param mail         the incoming mail whose body is analysed
     * @return the persisted variables, one per {@code {key, value, quote}} object
     *         returned by the LLM
     */
    public List<ExtractedVariable> extract(QuoteRequest quoteRequest, IncomingMail mail) {

        String prompt = assemblyService.assemble(promptTemplate, Map.of("mail_body", mail.getBody()));

        String rawResponse = llmClient.complete(prompt);

        List<LlmVariable> llmVariables =
                objectMapper.readValue(stripCodeFences(rawResponse), new TypeReference<List<LlmVariable>>() {});

        List<ExtractedVariable> results = new ArrayList<>();

        for (LlmVariable v : llmVariables) {

            ExtractedVariable extractedVariable = new ExtractedVariable();

            ExtractedVariable.Provenance provenance = resolveProvenance(mail.getBody(), v);

            extractedVariable.setKey(v.key());
            extractedVariable.setValue(provenance == ExtractedVariable.Provenance.MISSING ? null : v.value());
            extractedVariable.setCitation(v.quote());
            extractedVariable.setProvenance(provenance);
            extractedVariable.setQuoteRequestId(quoteRequest.getId());

            results.add(extractedVariable);
        }

        return extractedVariableRepository.saveAll(results);
    }

    /**
     * Strips a Markdown code fence ({@code ```json ... ```}) if the model wrapped
     * its answer in one, despite the prompt forbidding it.
     */
    private String stripCodeFences(String raw) {

        String trimmed = raw.trim();

        if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(trimmed.indexOf('\n') + 1);
            int closingFence = trimmed.lastIndexOf("```");
            if (closingFence >= 0) {
                trimmed = trimmed.substring(0, closingFence);
            }
        }

        return trimmed.trim();
    }
}
