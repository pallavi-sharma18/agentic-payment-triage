# AI-Powered Payment Failure Triage System

A multi-agent backend that diagnoses failed payments, explains the relevant card-network
rules via RAG, and proposes — then, with human approval, executes — a remediation plan.
Built with **Spring AI**, and integrated end-to-end over the **Model Context Protocol (MCP)**
in both directions: it is an MCP **server** (exposing payment operations to MCP clients) and
an MCP **client** (calling GitHub to open incident issues).

![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0-green)
![Spring AI](https://img.shields.io/badge/Spring%20AI-2.0-brightgreen)
![pgvector](https://img.shields.io/badge/Postgres-pgvector-blue)
![MCP](https://img.shields.io/badge/MCP-server%20%2B%20client-purple)

---

## Overview

When a payment fails, answering "what happened and what should we do?" needs three different
kinds of expertise: reading the transaction data, knowing the network rules, and deciding on
safe next steps. This project models that as **specialized AI agents** coordinated by a
deterministic orchestrator, plus a genuine **agentic feedback loop** for ambiguous cases.

A single call to `POST /triage` runs the pipeline and returns a structured diagnosis, a
rules explanation grounded in network documentation, and a propose-only remediation plan.
A follow-up `POST /triage/{paymentId}/execute` runs the human-approved steps and records the
incident in GitHub via MCP.

## Architecture

```
POST /triage?paymentId=pay_1002
        │
        ▼
  TriageService  (deterministic orchestrator — plain Java, no LLM)
        │  ├─ 404 guard if payment not found
        │  └─ short-circuits healthy payments
        │
        ├─1─▶ DiagnosticsAgent ──▶ LLM classifies the failure         ─▶ Diagnosis
        │        │
        │        └─ AGENTIC LOOP: if low-confidence/ambiguous, gather recent-failure
        │           evidence and re-diagnose (bounded, confidence-driven) until confident
        │
        ├─2─▶ RulesAgent (RAG) ──▶ LLM + pgvector over visa.pdf        ─▶ RuleExplanation
        │
        └─3─▶ RemediationAgent ─▶ LLM judgment (propose-only)          ─▶ ActionPlan
        │
        ▼
  TriageResult { diagnosis, ruleExplanation, actionPlan }

POST /triage/{paymentId}/execute?approveSteps=1,2
        │
        ▼
  RemediationExecutor ──▶ GitHub MCP client (create_issue) ──▶ incident issue opened
        (runs only steps that need no approval OR are explicitly approved)
```

### Agents — how the LLM and RAG are used

| Agent | Responsibility | LLM | RAG |
|-------|----------------|:---:|:---:|
| **Diagnostics** | Classify the failure; iterate with evidence when uncertain | ✅ | ❌ |
| **Rules** | Explain *why* it failed and what retries/reversals are allowed | ✅ | ✅ (visa.pdf via pgvector) |
| **Remediation** | Propose an ordered, safe action plan (propose-only) | ✅ | ➖ (consumes Rules output) |
| **TriageService** | Deterministic orchestration, guards, short-circuit | ❌ | ❌ |
| **RemediationExecutor** | Execute human-approved steps via MCP tools | ✅ | ❌ |

## Tech Stack

- **Java 21**, **Spring Boot 4**, **Spring AI 2.0**
- **OpenAI** `gpt-4o-mini` (chat) + `text-embedding-3-small` (embeddings)
- **PostgreSQL + pgvector** for the vector store
- **MCP**: `spring-ai-starter-mcp-server-webmvc` (server) + `spring-ai-starter-mcp-client` (client → GitHub MCP)
- **Spring Data JPA**, **Lombok**, **ModelMapper**, Spring caching

## Features

- 🤖 **Multi-agent pipeline** with role-scoped prompts and structured (typed) outputs
- 🔁 **Agentic re-diagnosis loop** — detects low-confidence diagnoses, gathers evidence, and re-diagnoses until confident (bounded, with a deterministic fallback)
- 📚 **RAG** over card-network documentation with similarity-filtered retrieval
- 🔌 **Bidirectional MCP** — exposes triage tools to MCP clients *and* opens GitHub incidents as an MCP client
- 🧑‍⚖️ **Human-in-the-loop** execution — money-moving steps require explicit approval (`approveSteps`)
- ⚡ **Caching + latency tuning** — rule lookups cached by failure reason; removed a redundant tool round-trip
- 🛡️ **Robust errors** — 404 for unknown payments via a global exception handler

## API

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/triage?paymentId={id}` | Run the full multi-agent failure triage |
| `POST` | `/triage/{paymentId}/execute?approveSteps={n,...}` | Execute approved remediation steps (opens a GitHub incident) |
| `POST` | `/chat?paymentId={id}&userId={id}` | Conversational assistant (tools + memory + RAG) |

### Example

```bash
# 1. Triage
curl -X POST "http://localhost:8080/triage?paymentId=pay_1002"

# 2. Execute approved steps (opens a GitHub incident issue)
curl -X POST "http://localhost:8080/triage/pay_1002/execute?approveSteps=2" \
  -H "Content-Type: application/json" \
  -d '{ "actions": [ ... ], "summary": "..." }'
```

## Getting Started

### Prerequisites
- Java 21+ (developed on OpenJDK 22)
- Maven (wrapper included)
- PostgreSQL with the `pgvector` extension
- Node.js / `npx` (the GitHub MCP server is launched via `npx`)
- OpenAI API key; a GitHub fine-grained PAT with **Issues: Read and write** on the incident repo

### 1. Database
```sql
CREATE DATABASE pgvector_payment;
CREATE EXTENSION IF NOT EXISTS vector;
```

### 2. Configuration — secrets via environment variables
All secrets are read from env vars (never commit them). Create a local `.env` (gitignored):
```bash
export OPENAI_API_KEY=sk-proj-...
export ANTHROPIC_API_KEY=sk-ant-...
export GITHUB_TOKEN=github_pat_...
```
Set the incident repo in `application.yaml`:
```yaml
app:
  incident:
    owner: <your-github-username>
    repo:  <your-repo>
```

### 3. Run
```bash
source .env && ./mvnw spring-boot:run
```
Schema is managed with `ddl-auto: update`; seed data loads from `data.sql` on startup.

### 4. Ingest the knowledge base (one time)
Ingest `visa.pdf` into pgvector so the Rules agent can retrieve it (via the ingestion
entrypoint, e.g. `RAGService.ingestVectorStore()` / the admin ingest endpoint).

### 5. Try it
```bash
curl -X POST "http://localhost:8080/triage?paymentId=pay_1001"
```

## Project Structure
```
src/main/java/com/flourish/payment_backend
├── agents/        # DiagnosticsAgent, RulesAgent, RemediationAgent, RemediationExecutor
├── service/       # TriageService (orchestrator), PaymentService, RAGService
├── tools/         # @Tool methods exposed to the LLM and MCP
├── controller/    # TriageController, ChatController
├── dtos/          # Diagnosis, RuleExplanation, ActionPlan, TriageResult
├── config/        # AIConfig, McpServerConfig
├── exceptions/    # PaymentNotFoundException, GlobalExceptionHandler
└── entities/      # Payment, PaymentStatus
```

## Design Highlights

- **Deterministic orchestration over LLM orchestration** — predictable, debuggable, cheaper; the LLM does the fuzzy work *inside* each agent.
- **A real agentic loop** — re-diagnosis fires only on genuine ambiguity (e.g. a `do_not_honor` decline that turns out to be a systemic outage), with a bounded iteration cap and deterministic fallback.
- **Structured outputs** — each agent emits a typed record, so the next stage gets clean data, not free text.
- **Bidirectional MCP** — the same service is both an MCP server and an MCP client, with human approval gating any side-effecting action.


## Security

Secrets are provided via environment variables (`${OPENAI_API_KEY}`, `${ANTHROPIC_API_KEY}`,
`${GITHUB_TOKEN}`) — no credentials are committed. `.env` and log files are gitignored.
Rotate any key that has ever been exposed.
