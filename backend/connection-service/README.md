# Connection service database setup

The service uses the same Supabase PostgreSQL connection settings as the other
backend services:

- `SUPABASE_JDBC_URL`
- `SUPABASE_DB_USER`
- `SUPABASE_DB_PASSWORD`

Set these on the backend service only. For local development, the defaults point
to `jdbc:postgresql://localhost:5432/studybuddy`.

On startup the service runs
[`src/main/resources/schema.sql`](./src/main/resources/schema.sql), which
creates the request, connection, chat message, and chat deletion tables if they
do not exist. Set `SPRING_SQL_INIT_MODE=never` to skip this.

If `match_requests` was created before cancellation support was added, apply
[`migrations/002_allow_cancelled_match_requests.sql`](./migrations/002_allow_cancelled_match_requests.sql)
once in the Supabase SQL Editor to update its status constraint.

## Real-time chat

For a class-by-class explanation and request/data flow diagrams, see
[`CHAT_README.md`](./CHAT_README.md).

Chat history remains available to both participants after a study buddy
connection ends, but sending and live subscriptions require an active
connection. Students can hide a chat from their own history without deleting
the other student's copy. Apply
[`migrations/004_create_chat_deletions.sql`](./migrations/004_create_chat_deletions.sql)
once in Supabase to enable this feature. The service authenticates each STOMP
WebSocket session using the student's JWT and checks participant membership
before allowing history access, and active membership before allowing sends or
subscriptions. Messages are stored in `connection_chat_messages`.
The built-in Spring message broker is in-memory; run one connection-service
instance for this setup. Multi-instance deployments need a shared message
broker so students connected to different instances still receive messages
immediately. The broker sends STOMP heartbeats every 10 seconds to help detect
stale WebSocket connections.

In local development, Vite proxies `/ws` directly to connection-service while
regular `/api` calls continue through the API gateway. Configure
`VITE_CHAT_WS_TARGET` if connection-service is not at `http://localhost:8084`.
For deployments that serve the frontend and backend on separate hosts, route
the public `/ws` path to connection-service with a WebSocket-capable reverse
proxy, or set up the equivalent WebSocket route in the deployment gateway.
Connection-service accepts browser origins from localhost ports 5173 by
default; set `CHAT_ALLOWED_ORIGINS` to a comma-separated list of allowed
origins for other deployments. Do not use a wildcard in production.
