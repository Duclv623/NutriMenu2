# NutriGuide chatbot

Separate Python/FastAPI service for nutrition RAG, real meal-plan context and
account-scoped PostgreSQL conversation history. Gateway itself is Java.

## Setup and run

Use Python 3.12. From this directory:

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.lock.txt
Copy-Item .env.example .env
```

Copy the example only when no .env exists; preserve existing credentials.
Configure POSTGRES_DSN, provider credentials and storage settings. Java integration:

```dotenv
MEAL_SERVICE_MODE=http
GATEWAY_AUTH_URL=http://127.0.0.1:8083/api/internal/identity
MEAL_API_BASE_URL=http://127.0.0.1:8080/api/internal/
MEAL_API_TIMEOUT=30
PUBLIC_API_PREFIX=/api/chatbot
```

JWTs are validated by User Service. No shared GATEWAY_SERVICE_KEY or Python gateway
session is used. The backend derives the conversation owner from the verified UUID.
GET document navigation also supports the scoped HttpOnly JWT cookie.

```powershell
.\.venv\Scripts\python.exe -m uvicorn rag_app.main:create_app --factory --host 127.0.0.1 --port 8000
```

The service initializes its history schema/tables when it starts. PostgreSQL must
be reachable and the configured role must have the required schema privileges.
Frontend calls /api/chatbot through Java Gateway. Use one browser hostname consistently.

## Corpus

```powershell
.\.venv\Scripts\python.exe -m rag_app.cli ingest --mode hybrid
```

Ingestion can call a paid embedding provider. Run it when creating/updating the
corpus; existing indexes are not silently replaced on each startup.
Keep rag_app/, web/, config/ and data/: they contain runtime code, source-preview
assets, manifests, original references and reproducible catalog inputs.
Chroma stores corpus/vectors; PostgreSQL stores account-scoped conversation history.
Do not delete database files or configured storage as part of source cleanup.

scripts/, evals/ and contracts/ contain offline data collection/evaluation utilities,
not request-time gateway code. requirements-data.txt is for offline data processing;
requirements-dev.txt supplies lint tooling. Unit-test source has been removed.

Provider keys and indexed documents are needed for knowledge answers. Meal-context
readback uses real Java data. Autonomous chat swaps require complete backend policy
evidence; current chat edits hand off to the checked frontend meal editor.

See [TEAM_INTEGRATION.md](docs/TEAM_INTEGRATION.md) and [gateway](../BackendNutriMenu/gateway/README.md).
