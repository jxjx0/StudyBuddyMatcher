# API gateway

The single entry point for the frontend. Runs on port `8080`. It verifies the
JWT on every request, enforces the coarse role rules, and forwards the request
to the service that owns it.

For the system overview see the [project README](../../README.md); for shared
configuration see the [backend README](../README.md).

## Why a gateway

The browser only ever talks to one origin, so the frontend needs no CORS
configuration in development and no knowledge of which service owns which
endpoint. Services can move or change port without touching frontend code.

The gateway is **not** the only line of defence: every downstream service
verifies the same JWT itself, so calling a service directly on its own port
still requires a valid token.

## Routes

Declared in [`application.yml`](src/main/resources/application.yml) rather than
in Java, so a service can be repointed with an environment variable and no
rebuild.

| Path predicate | Routed to | Override with |
|---|---|---|
| `/api/auth/**`, `/api/users/**`, `/api/students/**`, `/api/courses/**`, `/api/admin/**` | user-service, `:8081` | `USER_SERVICE_URL` |
| `/api/matching/**` | matching-service, `:8083` | `MATCHING_SERVICE_URL` |
| `/api/match-requests/**`, `/api/connections/**` | connection-service, `:8084` | `CONNECTION_SERVICE_URL` |
| `/api/groups/**` | group-service, `:8085` | `GROUP_SERVICE_URL` |
| `/ws` | connection-service, `:8084` (WebSocket upgrade) | `CONNECTION_SERVICE_URL` |

Note that `/api/students/internal/**` falls under the `/api/students/**`
predicate, so it *is* forwarded. It is not protected by being unroutable —
reaching it needs a valid JWT at the gateway and then the
`X-Contact-Service-Secret` header that only connection-service holds. A browser
request without that header is rejected by user-service.

## Authorization

[`SecurityConfiguration`](src/main/java/com/studybuddy/gateway/SecurityConfiguration.java)
builds an HS256 `JwtDecoder` from the shared `JWT_SECRET` and maps the token's
`roles` claim onto Spring authorities. Sessions are stateless and CSRF is
disabled, which is appropriate for a token-authenticated API with no cookies.

| Rule | Paths |
|---|---|
| Public | `POST /api/auth/login`, `POST /api/auth/register`, `GET /api/courses/**`, `/actuator/health` |
| Public at the gateway, authenticated at the service | `/ws` — the STOMP CONNECT frame carries the JWT, so connection-service authenticates the session itself |
| Admin only (`ROLE_ADMIN`) | `/api/admin/**`, `/api/matching/config/**`, `POST`/`PUT` on `/api/courses/**` |
| Authenticated | Everything else |

These are coarse rules. Finer checks — that a student searches only with their
own ID, or is a member of the connection whose chat they are reading — belong
to the owning service and are enforced there.

The gateway **fails to start** if `JWT_SECRET` is missing or shorter than 32
characters. That is deliberate: a gateway running without token verification
would silently expose every service.

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `GATEWAY_PORT` | `8080` | Listen port |
| `JWT_SECRET` | — | **Required**, ≥32 characters, identical across all services |
| `USER_SERVICE_URL` | `http://localhost:8081` | Downstream service |
| `MATCHING_SERVICE_URL` | `http://localhost:8083` | Downstream service |
| `CONNECTION_SERVICE_URL` | `http://localhost:8084` | Downstream service |
| `GROUP_SERVICE_URL` | `http://localhost:8085` | Downstream service |

The gateway holds no database connection of its own.

## Run

From `backend`:

```powershell
.\mvnw.cmd -pl api-gateway spring-boot:run
```

On macOS/Linux use `./mvnw`. Check it is up with
`http://localhost:8080/actuator/health`.
