# Matterhorn — Backend

**Automated quote generation for accounting firms, from incoming emails.**

A prospect writes to the firm. An LLM extracts the variables that drive the price.
The price is then computed **deterministically** from the firm's own grid.

> The LLM extracts; it never decides an amount.
> Every value carries its provenance: quoted verbatim from the mail, assumed, or missing.
> When a variable cannot be trusted, no amount is produced and a human follows up.

## Status

| | |
|---|---|
| ✅ **Working & tested** | Template registry · mail capture · LLM extraction with provenance · deterministic pricing |
| 🚧 **Next steps** | Expose the computed quote through the API · real classification · real mailbox intake · reply to the prospect |
| ⚠️ **Known issue** | Live extraction can fail with a `NullPointerException` when the model's first response block is not text ([#8](http://100.68.134.6:8929/st00mp/matterhorn-backend/-/issues/8)). |

Personal backend project (Spring Boot 4, Java 21). Work in progress: what is listed as working is covered by the test suite.

---

## Try it in 2 minutes

No API key needed. You only need **Java 21** — or only **Docker**.

```bash
./mvnw spring-boot:run
# or, without Java: docker compose up --build
```

The API listens on `http://localhost:8080`. Then, in another terminal:

```bash
# 1. Is it running?
curl http://localhost:8080/actuator/health
# {"status":"UP"}

# 2. A template is seeded at startup (« Agent lettre de mission »)
curl http://localhost:8080/templates

# 3. A firm instantiates it with its own values
curl -i -X POST http://localhost:8080/templates/1/instances \
  -H 'Content-Type: application/json' \
  -d '{
        "values": {
          "firm_name": "Fiduciaire Horizon SRL",
          "firm_company_number": "BE 0123.456.789",
          "firm_itaa_number": "ITAA 50.123.456",
          "representative_name": "Marie Lambert, gérante",
          "base_fee_company": "220.00",
          "base_fee_individual": "135.00",
          "price_per_document": "1.20",
          "included_documents": "15",
          "monthly_vat_surcharge": "36.00",
          "tone_of_voice": "professionnel et chaleureux",
          "signature": "Marie Lambert — Fiduciaire Horizon"
        }
      }'

# 4. The service assembles the final agent spec
curl http://localhost:8080/instances/1/output
# The system prompt with every {{placeholder}} resolved — no {{ left.
```

## See the mail → quote chain work

The extraction step calls the Anthropic API, so it needs a key to run live.
To see the whole chain without one, run the test suite:

```bash
./mvnw verify
```

It runs fully offline: the tests replace the real Anthropic client with a
programmable fake (`FakeLlmClient`, shared via `FakeLlmTestConfig`). Capture,
extraction, provenance and pricing are all exercised — no network, no key.

---

## How it works

```
POST /admin/incoming-mails
   │
   ├─ 1. Classification   stub: every mail becomes a QuoteRequest TO_VALIDATE
   │
   ├─ 2. Extraction (LLM) → ExtractedVariable   SOURCED · ASSUMED · MISSING
   │
   └─ 3. Pricing (no LLM) → QuoteLine           GRID · DETECTED · ASSUMED
```

**1. Classification.** For now a stub: every captured mail creates a
`QuoteRequest` with status `TO_VALIDATE`, linked to the mail.

**2. Extraction.** The mail body is sent to the LLM with a versioned prompt
(`src/main/resources/prompt/extractor.md`). It targets three variables:

- `client_legal_form` — constrained to `COMPANY` / `INDIVIDUAL`
- `client_vat_regime` — constrained to `MONTHLY` / `QUARTERLY`
- `client_document_volume` — kept as the mail's own wording

Provenance is decided by deterministic verification, never by the LLM:
`SOURCED` if the quote exists verbatim in the mail, `ASSUMED` if it cannot be
verified, `MISSING` if there is no value.

**3. Pricing.** No LLM here. Variables are normalized into typed values, then
priced against the firm's grid (the instance's `engine` fields): base fee,
per-document overflow beyond the included volume, VAT filing line. Each line
carries its provenance (`GRID`, `DETECTED`, `ASSUMED`) and its citation.
A single unusable variable produces no line at all: no amount is computed
rather than guessed.

Extraction and pricing failures are logged and never block the capture.

---

## Stack

- Java 21 · Spring Boot 4.1
- Spring Data JPA · H2 in-memory (dev/test) · PostgreSQL (Docker)
- Anthropic Messages API via Spring `RestClient` (plain HTTP, no SDK)
- Maven · Docker (multi-stage build)

## Configuration

Only needed to run extraction live. The key is never stored in the repository.

| Variable | Purpose | Default |
|---|---|---|
| `ANTHROPIC_API_KEY` | Anthropic API key used by the extractor | _(empty — extraction fails and is logged, capture still works)_ |
| `ANTHROPIC_MODEL` | Anthropic model used for extraction | `claude-sonnet-5` |

With Docker, `ANTHROPIC_API_KEY` is forwarded from your shell into the container if set.

## API

Every endpoint is documented and testable in **Swagger UI** once the app is running:
`http://localhost:8080/swagger-ui.html`

## License

MIT
