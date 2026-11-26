# Java backend integration

Java Spring Cloud Gateway verifies the User Service JWT before forwarding chatbot requests.
User Service owns authentication, profiles and account-specific activity. Meal Optimizer remains authoritative for plans.

Set chatbot environment values:

```dotenv
MEAL_SERVICE_MODE=http
GATEWAY_AUTH_URL=http://127.0.0.1:8083/api/internal/identity
MEAL_API_BASE_URL=http://127.0.0.1:8080/api/internal/
MEAL_API_TIMEOUT=30
PUBLIC_API_PREFIX=/api/chatbot
```

Requests carry User Service RS256 JWTs. The chatbot calls the configured identity
endpoint to validate the token and obtain a stable user:UUID owner. No shared gateway
service key or Python gateway session is required. For GET document navigation,
a scoped HttpOnly JWT cookie from User Service is supported.

The HTTP meal adapter reads /api/internal/me/meal-context from Meal Optimizer through the gateway.
It checks ownership and builds the existing MealSnapshot contract using real Catalog
recipes and Optimizer plan data. Catalog recipe versions and complete policy evidence
remain unknown; they are not invented. Chat meal edits use the frontend's checked
meal editor, whose preview/apply endpoints invoke Optimizer CP-SAT validation.
Autonomous natural-language apply-swap is not enabled without full backend evidence.

Configure POSTGRES_DSN and provider credentials separately in ai-service/.env.
Use the same browser hostname for frontend/API so scoped document cookies work.
Existing conversation owners remain user:UUID after the switch to JWT introspection.
