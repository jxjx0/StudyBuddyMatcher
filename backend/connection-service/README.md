# Connection service

Owns study-buddy match requests, established connections, and real-time chat.
Runs on port `8084`. The gateway routes `/api/match-requests/**`,
`/api/connections/**` and the `/ws` WebSocket upgrade here.

For the feature list see the [project README](../../README.md); for shared
backend configuration see the [backend README](../README.md).

## Responsibilities

| Area | Classes |
|---|---|
| Match requests | [`MatchRequestController`](src/main/java/com/studybuddy/connection/controller/MatchRequestController.java), [`MatchRequestService`](src/main/java/com/studybuddy/connection/service/MatchRequestService.java), [`MatchRequestRepository`](src/main/java/com/studybuddy/connection/repository/MatchRequestRepository.java) |
| Connections and contact reveal | [`ConnectionController`](src/main/java/com/studybuddy/connection/controller/ConnectionController.java), [`StudyBuddyConnectionRepository`](src/main/java/com/studybuddy/connection/repository/StudyBuddyConnectionRepository.java) |
| Chat | [`ChatController`](src/main/java/com/studybuddy/connection/controller/ChatController.java), [`ChatService`](src/main/java/com/studybuddy/connection/service/ChatService.java), [`ChatMessageRepository`](src/main/java/com/studybuddy/connection/repository/ChatMessageRepository.java) |
| WebSocket security | [`ChatAuthenticationInterceptor`](src/main/java/com/studybuddy/connection/config/ChatAuthenticationInterceptor.java), [`ChatWebSocketConfiguration`](src/main/java/com/studybuddy/connection/config/ChatWebSocketConfiguration.java) |
| Profile lookups | [`StudentClient`](src/main/java/com/studybuddy/connection/client/StudentClient.java) — calls user-service over HTTP |

## Request behaviour

- A request carries an optional message of up to 500 characters.
- A partial unique index allows only one `PENDING` request per sender/recipient
  pair, so a duplicate returns a conflict response rather than a server error.
- If the recipient already has a pending request from you, sending a reciprocal
  request accepts the existing one and establishes the connection.
- A sender can cancel a pending request; its status becomes `CANCELLED`.
- Contact numbers stay hidden until the connection is active.

## Database setup

The service uses the same Supabase settings as every other backend service —
`SUPABASE_JDBC_URL`, `SUPABASE_DB_USER` and `SUPABASE_DB_PASSWORD`, set on the
backend only. For local development the defaults point to
`jdbc:postgresql://localhost:5432/studybuddy`.

On startup it runs
[`src/main/resources/schema.sql`](src/main/resources/schema.sql), which creates
all four of its tables if they do not already exist:

| Table | Holds |
|---|---|
| `match_requests` | Requests and their `PENDING` / `ACCEPTED` / `DECLINED` / `CANCELLED` status |
| `study_buddy_connections` | Established connections and whether they are still active |
| `connection_chat_messages` | Persisted chat history |
| `connection_chat_deletions` | Which student has hidden which conversation |

Set `SPRING_SQL_INIT_MODE=never` to skip this. Note that this variable affects
**only this service** — see
[Schema initialization](../README.md#schema-initialization) in the backend
README for what the other services do.

Everything the service needs is in that one `schema.sql`; there are no separate
migration files to apply. If you are connecting to a database created by an
older version of this project, compare its `match_requests` status constraint
and check that `connection_chat_messages` and `connection_chat_deletions`
exist — re-running `schema.sql` is safe, but it will not alter an existing
table's constraints.

## Real-time chat

For a class-by-class explanation and request/data flow diagrams, see
[`CHAT_README.md`](./CHAT_README.md).

- Chat history stays available to both participants after a connection ends,
  but sending and live subscriptions require an **active** connection.
- A student can hide a conversation from their own history without deleting the
  other student's copy.
- Every STOMP session is authenticated with the student's JWT. The service
  checks participant membership before serving history, and active membership
  before allowing sends or subscriptions.
- Messages are limited to 2,000 characters; a conversation loads its most recent
  100 saved messages.
- The broker sends STOMP heartbeats every 10 seconds, using a dedicated
  scheduler so it does not collide with Spring's default scheduler bean.

### Deployment constraints

The built-in Spring message broker is **in-memory**, so run a single
connection-service instance with this setup. A multi-instance deployment needs
a shared broker, otherwise students connected to different instances will not
receive each other's messages immediately.

### WebSocket routing and origins

In local development Vite proxies `/ws` straight to connection-service while
`/api` calls go through the gateway. Set `VITE_CHAT_WS_TARGET` if
connection-service is not at `http://localhost:8084`. The gateway also has a
`/ws` route to this service, for deployments that do not use the Vite proxy.

Where the frontend and backend are served from separate hosts, route the public
`/ws` path here with a WebSocket-capable reverse proxy, or configure the
equivalent route in the deployment gateway.

Allowed browser origins default to `http://localhost:5173` and
`http://127.0.0.1:5173` — both are included because `scripts/start-cloud.py`
serves the frontend on `127.0.0.1`. Set `CHAT_ALLOWED_ORIGINS` to a
comma-separated list for other deployments. Do not use a wildcard in
production.
