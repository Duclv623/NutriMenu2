# NutriGuide frontend

React 19, TypeScript, Vite and React Router. Requires Node.js 22.12+.

## Run

```powershell
npm ci
npm run dev
```

Open http://127.0.0.1:5173. Default API configuration:

```dotenv
VITE_API_MODE=api
VITE_API_BASE_URL=/api
VITE_RAG_BASE_URL=/api/chatbot
```

Vite forwards /api to Java Gateway on port 8080. Override API_PROXY_TARGET if
needed. Feature pages and API clients live under src/features; shared HTTP code
lives in src/lib and routing in src/routes. Each page loads its required domain APIs.
Chatbot requests use /api/chatbot through the same gateway.

Registration/login use User Service CSRF + JWT. Account and token are held in
sessionStorage. An HttpOnly JWT cookie is used for opening cited documents.
Backend APIs derive identity from JWT; the frontend does not supply X-User-Id.
No API failure silently falls back to demo data.

## Build

```powershell
npm run typecheck
npm run build
npm run preview
```

Build output is dist/. For deployment, serve index.html for frontend routes and
proxy /api to the gateway. Never rewrite API errors as frontend HTML.

## Supported integration

Accounts, profiles, catalog, favorites, four-meal CP-SAT generation, checked swap
preview/apply, item locks/completion, shopping state, check-ins and history.
Chatbot supports real meal context, citations, optional saved conversations,
feedback and the checked frontend meal editor. Knowledge responses require a
configured provider and indexed corpus; no API keys belong in VITE_* variables.

## Demo mode

Set VITE_API_MODE=demo for the existing browser-only preview. Its fixture catalog,
PBKDF2 demo accounts and random sample plans are UI demonstration data. They do
not run CP-SAT and always report NOT_RUN. Keep demo and real accounts separate.
Demo mode is still used by the interface and is not dead code.

See [API_CONTRACT.md](API_CONTRACT.md), [gateway](../BackendNutriMenu/gateway/README.md) and
[chatbot](../ai-service/README.md) for service configuration and current limits.
