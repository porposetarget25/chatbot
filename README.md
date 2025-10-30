# java-genai-llm-starter

Minimal Spring Boot Java starter for LLM chat, streaming, and tiny RAG.

## Prereqs
- Java 17+
- Maven 3.9+
- Choose a provider:
  - **OpenAI**: set env var `OPENAI_API_KEY` and keep profile `openai` (default)
  - **Ollama**: `ollama run llama3.1`, then run with `--spring.profiles.active=ollama`

## Run
```bash
# OpenAI
add env variable when running in intillej : OPENAI_API_KEY=sk-proj......
add vm options : -Dspring.profiles.active=openai,insecure-ssl
mvn spring-boot:run -Dspring-boot.run.profiles="openai,insecure-ssl"

# or Ollama
mvn spring-boot:run -Dspring-boot.run.profiles=ollama
```

## Test
```bash
# one-shot
curl -s -X POST http://localhost:8080/api/chat   -H 'Content-Type: application/json'   -d '{"prompt":"Explain Java records in 3 lines"}' | jq

# streaming SSE (prints tokens)
curl -N -X POST http://localhost:8080/api/chat/stream   -H 'Content-Type: application/json'   -d '{"prompt":"Give me a haiku about Spring Boot"}'

# RAG: upsert docs
curl -s -X POST http://localhost:8080/api/rag/upsert   -H 'Content-Type: application/json'   -d '{"id":"doc1","text":"Spring Boot is an opinionated framework built on Spring."}'

# RAG: query
curl -s -X POST http://localhost:8080/api/rag/query   -H 'Content-Type: application/json'   -d '{"prompt":"What is Spring Boot?"}' | jq
```

## Notes
- The streaming parser for OpenAI is intentionally simple; for production, parse JSON chunks robustly.
- Add retries/timeouts and proper error handling before shipping.
- Swap models by editing `src/main/resources/application.yml`.
