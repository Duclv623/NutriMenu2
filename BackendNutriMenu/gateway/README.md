# NutriMenu API Gateway

Java 21 / Spring Cloud Gateway verifies User Service JWTs before routing requests
to the configured HTTP service addresses.

## Run

```powershell
.\mvnw.cmd spring-boot:run
```

Default: http://127.0.0.1:8080. Set PORT / GATEWAY_HOST to override.
Export variables in the terminal; Spring does not load .env automatically.

| Path | Target variable (default port) |
|---|---|
| /api/auth/**, /api/users/me, /api/users/me/profile | USER_SERVICE_URL (8083) |
| /api/me/profile, /api/me/shopping-list, /api/me/check-ins/**, /api/internal/identity | USER_SERVICE_URL (8083) |
| /api/foods/**, /api/me/favorites/** | FOOD_CATALOG_URL (8081) |
| /api/meal-plans/**, /api/me/meal-logs, /api/internal/me/meal-context | MEAL_OPTIMIZER_URL (8082) |
| /api/chatbot/** | CHATBOT_URL (8000), rewritten to /api/** |

Routes live in `src/main/resources/application.yml`. The following methods and paths
are public at the gateway. User Service still enforces CSRF on register and login.

- `GET /api/auth/csrf` and `GET /api/auth/jwks`
- `POST /api/auth/register` and `POST /api/auth/login`

Every other routed request requires a User Service RS256 JWT. The gateway reads
public keys from `USER_SERVICE_URL/api/auth/jwks`. Set `JWT_ISSUER` and
`JWT_AUDIENCE` to the same values as User Service (defaults are
`http://localhost:8083` and `menufood`). Keep the signing key on User Service.

Send `Authorization: Bearer <token>`. For GET requests under `/api/chatbot/`,
the gateway also accepts the scoped HttpOnly cookie when the header is absent.
It forwards the verified token as a Bearer header. Missing, invalid or expired
credentials return HTTP 401. A key lookup that fails because User Service is
unreachable returns HTTP 503. The chatbot route removes Origin for the same-origin
reverse proxy. Use Vite's /api proxy for the local frontend.

## Integration responsibilities

User Service owns account identity, nutrition profiles, check-ins and shopping-list selections.
Catalog owns dish details and favorites. Meal Optimizer owns meal plans, optimistic
versions, validated swaps and the chatbot meal snapshot. Catalog and Optimizer resolve
the account from the forwarded Bearer token using USER_SERVICE_URL. The native
`/api/v1` endpoints used between services stay on the private network.

The chatbot reads identity from User Service and meal context through the gateway.
For existing chatbot installations set MEAL_API_BASE_URL to http://127.0.0.1:8080/api/internal/.
Set AUTH_SECURE_COOKIE=true on User Service when using HTTPS.

## Build

```powershell
.\mvnw.cmd -DskipTests package
java -jar target/gateway-0.0.1-SNAPSHOT.jar
```

Old Python gateway state is not deleted. If migrating an existing installation,
copy its verified team_identities/account IDs and team_ui_state into workspace_accounts
before serving existing users; never guess ownership of numeric optimizer IDs.
