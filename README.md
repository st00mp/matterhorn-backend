# Matterhorn — Backend

Registry of agent templates: an author defines instruction templates with
placeholders, a client customizes them through a wizard, and the service
assembles the final spec by substituting the values.

REST API built with Spring Boot. First backend service of the Matterhorn project.

## Stack

- Java 21
- Spring Boot 4.1
- Spring Data JPA · H2 in-memory (dev/test) · PostgreSQL (Docker)
- Anthropic Messages API via Spring `RestClient` (plain HTTP, no SDK)
- Maven · Docker

## Run locally

```bash
./mvnw spring-boot:run
```

The application starts on `http://localhost:8080`.

To enable LLM extraction on incoming mails, export your Anthropic API key first
(see [Configuration](#configuration)):

```bash
export ANTHROPIC_API_KEY=sk-ant-...
```

Check it's running:

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

## Run with Docker

No Java or Maven required — only Docker:

```bash
docker compose up --build
```

Builds the image (multi-stage) and starts the app with a PostgreSQL database
on `http://localhost:8080`. `ANTHROPIC_API_KEY` is forwarded from your shell
into the container if set.

## Configuration

LLM extraction is configured through environment variables — the API key is
never stored in the repository:

| Variable | Purpose | Default |
|---|---|---|
| `ANTHROPIC_API_KEY` | Anthropic API key used by the extractor | _(empty — extraction fails and is logged, capture still works)_ |
| `ANTHROPIC_MODEL` | Anthropic model used for extraction | `claude-sonnet-5` |

## Quickstart

On startup, the app seeds the **engagement-letter template** (« Agent lettre de
mission », version `1`) from `src/main/resources/seed/engagement-letter/`. Seeding
is idempotent: a template is identified by its `(name, version)` pair, so restarts
never create duplicates. The seeded template gets id `1` on an empty database.

The full chain in three calls: list the seeded template, instantiate it with values,
then assemble the output. The `values` keys must match the template's field `key`s —
all of them, including `engine` fields (instance validation covers every field).

```bash
# 1. The seeded template is already there
curl http://localhost:8080/templates

# 2. Create a test instance from it (all 11 fields, engine fields included)
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

# 3. Assemble the output (use the instance id from step 2)
curl http://localhost:8080/instances/1/output
# The system prompt with every {{placeholder}} resolved — no {{ left.
```

You can also create your own templates from scratch:

```bash
curl -i -X POST http://localhost:8080/templates \
  -H 'Content-Type: application/json' \
  -d '{
        "name": "Quote Agent",
        "category": "Sales",
        "description": "Generates a personalised quote.",
        "instructions": "You are a quote assistant for {{company_name}}. Bill at {{hourly_rate}} €/h.",
        "fields": [
          {"key": "company_name", "label": "Company name", "type": "text",   "help": ""},
          {"key": "hourly_rate",  "label": "Hourly rate",  "type": "number", "help": "e.g. 65"}
        ],
        "version": "1.0.0"
      }'
```

## API

The API is documented and browsable via Swagger UI once the app is running:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

### Endpoints

**Templates**

- `POST /templates` — create an agent template (201 with `Location` header, 400 if validation fails)
- `GET /templates` — list all agent templates (empty array if none)
- `GET /templates/{id}` — get an agent template by ID (404 if not found)
- `PUT /templates/{id}` — update an agent template (400 if validation fails, 404 if not found)
- `DELETE /templates/{id}` — delete an agent template (204 on success, 404 if not found)

**Instances**

- `POST /templates/{templateId}/instances` — create an agent instance from a template
  (201 with `Location` header, 400 if `values` is missing or required field values are
  missing/blank, 404 if the template does not exist)
- `GET /instances/{id}` — get an agent instance by ID, including its template
  reference and stored field values (404 if not found)
- `GET /instances/{id}/output` — assemble and return the instance's instructions as
  plain text, substituting each `{{placeholder}}` with its stored value (200 `text/plain`,
  404 if the instance does not exist, 422 if one or more placeholders cannot be resolved)

**Incoming mails (admin)**

- `POST /admin/incoming-mails` — capture an incoming mail attached to an existing
  instance, stored exactly as received with a server-set `receivedAt` (201 with
  `Location` header, 400 if `sender` or `body` is missing or blank, 404 if the
  instance does not exist). Each captured mail then goes through a synchronous
  chain:
  1. **Classification** — for now a stub that always creates a `QuoteRequest`
     with status `TO_VALIDATE`, linked to the mail.
  2. **Extraction** — the mail body is sent to the LLM with the versioned
     extractor prompt (`src/main/resources/prompt/extractor.md`), which targets
     three prospect variables: `client_legal_form` and `client_vat_regime`,
     constrained to the tokens `COMPANY`/`INDIVIDUAL` and `MONTHLY`/`QUARTERLY`,
     and `client_document_volume`, kept as the mail's own wording. Each returned
     `{key, value, quote}` is persisted as an `ExtractedVariable` linked to the
     `QuoteRequest`, with a provenance decided by deterministic verification,
     never by the LLM: `SOURCED` if the quote exists verbatim in the mail body,
     `ASSUMED` if the value has no verifiable quote, `MISSING` if there is no
     value. Extraction failures are logged and never block the capture.
  3. **Pricing** — no LLM at this stage. The extracted variables are normalized
     into typed values, then priced against the instance's `engine` fields (the
     accountant's grid) into `QuoteLine`s: base fee, per-document overflow beyond
     the included volume, and VAT filing line. Every line carries its own
     provenance — `GRID` for a grid-only amount, `DETECTED` when computed from a
     variable quoted in the mail, `ASSUMED` otherwise — and its citation. A single
     unusable variable produces no line at all: no amount is computed rather than
     guessed, and a human follows up. Pricing failures are logged and never block
     the capture.
- `GET /admin/incoming-mails/{id}` — get an incoming mail by ID, exactly as stored
  (404 if not found)

## Build & tests

```bash
./mvnw verify
```

The suite runs fully offline: tests substitute the real Anthropic client with a
programmable fake (`FakeLlmClient`, shared via `FakeLlmTestConfig`), so no
network access or API key is required.