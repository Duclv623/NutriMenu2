# NutriMenu

## Applications

| Directory | Runtime | Default port | Responsibility |
|---|---|---|---|
| frontend | React / TypeScript / Vite | 5173 | Web UI |
| BackendNutriMenu/gateway | Java 21 / Spring Cloud Gateway | 8080 | JWT verification and HTTP routing |
| BackendNutriMenu/Userservice | Java 21 / Spring Boot | 8083 | JWT accounts, profiles and personal activity |
| BackendNutriMenu/foodcatalogservice | Java 21 / Spring Boot | 8081 | Catalog, recipe nutrition |
| BackendNutriMenu/mealoptimizerservice | Java 21 / Spring Boot / OR-Tools | 8082 | Plans and checked meal changes |
| ai-service | Python / FastAPI / ChromaDB | 8000 | Nutrition RAG and real meal-plan context |

No Eureka is required. Gateway does not run Python or store application state.

## Setup

Use Java 21, Maven (or included wrappers), Node 22.12+, Python 3.12 and PostgreSQL.
Create databases: menufood_users, diabetes_food_catalog_db,
diabetes_meal_optimizer_db, nutriguide.

For local Java services, copy `BackendNutriMenu/.env.example` to `BackendNutriMenu/.env` and set `DB_PASSWORD` to your PostgreSQL password. Start each service from its module directory.

User Service uses Flyway V1-V3; V3 adds workspace account IDs and UI state.
Catalog and Optimizer initialize their tables/defaults. Do not point the integrated
User Service at a database with the old, different V2 migration without a migration plan.

Open a terminal in each Java application directory and run:

```powershell
.\mvnw.cmd spring-boot:run
```

No startup/test PowerShell scripts are required. No gateway .env or Python packages
are required; override service addresses using environment variables if needed.

In frontend: `npm ci`, then `npm run dev`. Its .env.local settings are:

```dotenv
VITE_API_MODE=api
VITE_API_BASE_URL=/api
VITE_RAG_BASE_URL=/api/chatbot
VITE_RAG_ENABLED=true
```

For chatbot, create a Python environment, install ai-service/requirements.lock.txt,
copy ai-service/.env.example to ai-service/.env and configure POSTGRES_DSN (nutriguide),
provider keys and corpus storage. Keep MEAL_SERVICE_MODE=http,
GATEWAY_AUTH_URL=http://127.0.0.1:8083/api/internal/identity,
MEAL_API_BASE_URL=http://127.0.0.1:8080/api/internal/.
Start it with `python -m uvicorn rag_app.main:create_app --factory --host 127.0.0.1 --port 8000`
from ai-service/. GATEWAY_SERVICE_KEY is no longer required for JWT introspection.

Open http://127.0.0.1:5173. Use one hostname consistently for JWT document cookies.
Configure JWT_PRIVATE_KEY_PATH/JWT_PUBLIC_KEY_PATH for persistent signing keys;
development-generated JWTs expire when User Service restarts.

## Flow and limits

Frontend features call their own APIs through the gateway. Profile and activity go to User Service,
foods and favorites to Catalog, and plans and meal logs to Optimizer. Each service resolves the
authenticated account from the User Service JWT. Existing numeric account IDs, shopping and
check-in data remain in the V3 table. New plans store the source profile version in Optimizer.
Plans created on the earlier integration branch can still read their historical metadata from User Service.

Generation currently supports four meals, no cooking-time filter or regenerate-overwrite.
Swap preview/apply uses Optimizer validation and signatures. Chat edits open the frontend
meal editor; direct autonomous chat swaps are not enabled without full policy evidence.
Knowledge answers require configured provider keys and an indexed corpus.

See [gateway](BackendNutriMenu/gateway/README.md), [User Service](BackendNutriMenu/Userservice/README.md),
[chatbot](ai-service/README.md), [frontend API contract](frontend/API_CONTRACT.md).
