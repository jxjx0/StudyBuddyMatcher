# Connection service database setup

The service uses the same Supabase PostgreSQL connection settings as the other
backend services:

- `SUPABASE_JDBC_URL`
- `SUPABASE_DB_USER`
- `SUPABASE_DB_PASSWORD`

Set these on the backend service only. For local development, the defaults point
to `jdbc:postgresql://localhost:5432/studybuddy`.

Before starting the updated service against Supabase, apply
[`migrations/001_create_connection_tables.sql`](./migrations/001_create_connection_tables.sql)
in the Supabase SQL Editor using a database-owner connection. If
`match_requests` existed before cancellation support was added, also apply
[`migrations/002_allow_cancelled_match_requests.sql`](./migrations/002_allow_cancelled_match_requests.sql)
to update its status constraint. Apply
[`migrations/003_create_chat_messages.sql`](./migrations/003_create_chat_messages.sql)
to create persistent chat history. The migrations create the request,
connection, and chat message tables, enable row-level security, and keep the
tables inaccessible to browser roles. The application accesses them through
its server-side JDBC connection.

## Real-time chat

For a class-by-class explanation and request/data flow diagrams, see
[`CHAT_README.md`](./CHAT_README.md).

Chat is available only for active study buddy connections. The service
authenticates each STOMP WebSocket session using the student's JWT and checks
connection membership before allowing message history, sending, or subscribing
to a conversation. Messages are stored in `connection_chat_messages`.
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
