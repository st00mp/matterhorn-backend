package com.st00mp.matterhorn.service;

import com.st00mp.matterhorn.entity.AgentInstance;
import com.st00mp.matterhorn.entity.ExtractedVariable;
import com.st00mp.matterhorn.entity.IncomingMail;
import com.st00mp.matterhorn.entity.QuoteLine;
import com.st00mp.matterhorn.entity.QuoteRequest;
import com.st00mp.matterhorn.repository.ExtractedVariableRepository;
import com.st00mp.matterhorn.repository.QuoteLineRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the pricing of a quote request: loads the extracted variables and
 * the accountant's grid, hands both to the {@link PricingEngine}, and persists
 * the resulting quote lines.
 *
 * <p>All the arithmetic lives in the engine; this service only wires the data.
 */
@Service
public class PricingService {

    private final ExtractedVariableRepository extractedVariableRepository;
    private final AgentInstanceService agentInstanceService;
    private final ExtractedVariableNormalizer normalizer;
    private final PricingEngine pricingEngine;
    private final QuoteLineRepository quoteLineRepository;

    public PricingService(
            ExtractedVariableRepository extractedVariableRepository,
            AgentInstanceService agentInstanceService,
            ExtractedVariableNormalizer normalizer,
            PricingEngine pricingEngine,
            QuoteLineRepository quoteLineRepository) {
        this.extractedVariableRepository = extractedVariableRepository;
        this.agentInstanceService = agentInstanceService;
        this.normalizer = normalizer;
        this.pricingEngine = pricingEngine;
        this.quoteLineRepository = quoteLineRepository;
    }

    /**
     * Prices a quote request and persists its lines.
     *
     * <p>Deliberately not {@code @Transactional}: the method runs inside the caller's
     * transaction, and its own transactional boundary would mark that transaction
     * rollback-only on failure — silently discarding the mail the caller took care to
     * persist before pricing. The single write is the final {@code saveAll}, so there
     * is nothing here to isolate.
     *
     * @param quoteRequest the request to price
     * @param mail         the mail the request comes from, which carries the agent instance
     * @return the persisted quote lines, empty when a variable is unusable
     */
    public List<QuoteLine> price(QuoteRequest quoteRequest, IncomingMail mail) {
        List<ExtractedVariable> variables = extractedVariableRepository.findByQuoteRequestId(quoteRequest.getId());

        AgentInstance instance = agentInstanceService.getById(mail.getInstanceId());
        PricingEngine.Grid grid = toGrid(instance.getValues());

        NormalizedVariables vars = normalizer.normalize(variables);

        List<QuoteLine> lines = pricingEngine.price(grid, vars);
        for (QuoteLine line : lines) {
            line.setQuoteRequestId(quoteRequest.getId());
        }

        return quoteLineRepository.saveAll(lines);
    }

    /** Reads the grid fields of the instance; euro amounts are stored as cents. */
    private PricingEngine.Grid toGrid(Map<String, String> values) {
        return new PricingEngine.Grid(
                cents(values, "base_fee_company"),
                cents(values, "base_fee_individual"),
                Integer.parseInt(values.get("included_documents")),
                cents(values, "price_per_document"),
                cents(values, "monthly_vat_surcharge"));
    }

    private static long cents(Map<String, String> values, String key) {
        return new BigDecimal(values.get(key)).movePointRight(2).longValueExact();
    }
}
