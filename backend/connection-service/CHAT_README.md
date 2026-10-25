# Student chat

This document describes the real-time chat available to students with an active
study buddy connection: the Java classes involved, the user-facing flow, and
how messages move between the frontend, connection-service, and PostgreSQL.

## How students use chat

1. A student opens **My Connections** and selects **Chat** on an active
   connection.
2. The chat dialog connects to the `/ws` WebSocket endpoint and authenticates
   its STOMP session with the student's bearer JWT.
3. It loads the latest 100 messages from the persisted conversation.
4. It subscribes to that connection's live topic. New messages are displayed
   immediately for both connected students.
5. The sender's initials avatar is shown beside each message. Messages are
   limited to 2,000 characters.

Only active connections can read history, subscribe to a conversation, or send
messages. Ending a connection disables chat access. Saved messages remain in
the database unless the connection row is deleted (the chat-message foreign
key cascades on deletion).

## Java classes

All Java paths below are relative to
`src/main/java/com/studybuddy/connection/`.

| Class | Responsibility |
|---|---|
| `config/ChatWebSocketConfiguration.java` | Enables Spring's STOMP-over-WebSocket support; registers `/ws`; configures `/app` application destinations and `/topic` broadcasts; installs the chat inbound interceptor; configures allowed browser origins and a dedicated heartbeat scheduler. |
| `config/ChatAuthenticationInterceptor.java` | Validates the bearer JWT on STOMP `CONNECT`, requires the student role, and binds the JWT `uid` to the WebSocket `Principal`. Before subscription, it checks that the principal belongs to the active connection. It also restricts client `SEND` messages to the supported chat destination. |
| `controller/ChatController.java` | Receives STOMP sends at `/app/connections/{connectionId}/send`, asks `ChatService` to authorize and persist the message, then broadcasts the saved message to `/topic/connections/{connectionId}`. |
| `controller/ConnectionController.java` | Exposes `GET /api/connections/{connectionId}/messages` to load conversation history using the authenticated JWT identity. |
| `service/ChatService.java` | Applies chat business rules: validates UUIDs and message contents, checks that the connection is active and includes the requesting student, and coordinates history reads and message persistence. |
| `repository/ChatMessageRepository.java` | Uses Spring `JdbcTemplate` to insert chat messages and fetch the latest 100 messages in chronological order. |
| `dto/SendChatMessageRequest.java` | Validates incoming message content as nonblank and no longer than 2,000 characters. |
| `dto/ChatMessage.java` | Defines the saved message returned by the repository and broadcast to subscribers: message ID, connection ID, sender ID, content, and timestamp. |
| `repository/StudyBuddyConnectionRepository.java` and `model/StudyBuddyConnection.java` | Supply the connection record used by `ChatService` to verify active status and participant membership. |
| `config/SecurityConfiguration.java` | Secures the HTTP API and JWT decoder. The WebSocket handshake endpoint is permitted at the HTTP filter layer; STOMP authentication is then required by `ChatAuthenticationInterceptor`. |

## How Java and the database communicate

Messages are stored in the `connection_chat_messages` table created by
[`migrations/003_create_chat_messages.sql`](./migrations/003_create_chat_messages.sql).
The table has:

- `message_id`: message UUID primary key.
- `connection_id`: UUID referencing `study_buddy_connections`.
- `sender_id`: UUID taken from the authenticated JWT principal.
- `content`: nonblank text up to 2,000 characters.
- `sent_at`: database-generated timestamp.

The migration enables row-level security and revokes access from browser roles.
The Vue app does not connect to Supabase directly. Instead, connection-service
uses its configured server-side JDBC datasource (`SUPABASE_JDBC_URL`,
`SUPABASE_DB_USER`, and `SUPABASE_DB_PASSWORD`) to access the table.

### Loading history

```text
ConnectionChatDialog.vue
  -> GET /api/connections/{connectionId}/messages
  -> API gateway
  -> ConnectionController.findMessages()
  -> ChatService.history(connectionId, JWT uid)
  -> StudyBuddyConnectionRepository.findById()
  -> verify active connection and participant
  -> ChatMessageRepository.findRecent()
  -> JdbcTemplate SELECT from connection_chat_messages (latest 100)
  -> ChatMessage DTO list returned to the dialog
```

The request identity comes from the JWT, not a student ID supplied by the
browser.

### Sending a live message

```text
ConnectionChatDialog.vue
  -> STOMP SEND /app/connections/{connectionId}/send
  -> ChatAuthenticationInterceptor validates JWT principal and destination
  -> ChatController.send()
  -> ChatService.send(connectionId, Principal name, content)
  -> verify connection is active and includes sender
  -> ChatMessageRepository.save()
  -> JdbcTemplate INSERT ... RETURNING from connection_chat_messages
  -> broadcast saved ChatMessage to /topic/connections/{connectionId}
  -> both subscribed clients add the message to the conversation
```

The server saves before broadcasting. The broadcast therefore contains the
database-assigned timestamp and generated message ID. During history loading,
the frontend merges messages by ID to avoid duplicates if a live broadcast
arrives at the same time as the history response.

### Access checks

- At STOMP `CONNECT`, the interceptor decodes the bearer JWT with the same
  signing configuration as the other services, requires `ROLE_STUDENT`, and
  sets the principal name from the JWT `uid`.
- Before a `/topic/connections/{connectionId}` subscription is accepted, the
  interceptor calls `ChatService` to verify active membership.
- Sending is checked again in `ChatService` before persistence. The sender ID
  comes from the STOMP principal, never from the request payload.
- The HTTP history endpoint also uses the authenticated JWT `uid` and performs
  the same active-membership check.
- The service returns a not-found response for missing, ended, or unrelated
  connections so chat data is not exposed to nonparticipants.

## Routing and configuration

- HTTP API requests use the Vite `/api` proxy to the API gateway at
  `http://localhost:8080`; the gateway routes `/api/connections/**` to
  connection-service.
- In local development, Vite proxies `/ws` directly to connection-service at
  `http://localhost:8084`. Set `VITE_CHAT_WS_TARGET` if it runs elsewhere.
- The gateway also defines a `/ws` route to connection-service for deployments
  where the gateway handles the WebSocket upgrade. A WebSocket-capable proxy
  must preserve the upgrade handshake.
- Connection-service allows localhost frontend origins on port 5173 by
  default. Set `CHAT_ALLOWED_ORIGINS` to a comma-separated list of the exact
  frontend origins in another environment; avoid wildcard origins in
  production.
- The simple Spring broker is in-memory and intended for one connection-service
  instance. If multiple service instances are deployed, configure a shared
  broker so subscribers connected to different instances receive each other's
  messages.

## Applying the database migration

Apply migration 003 once in the Supabase SQL Editor using a database-owner
connection. The application runtime should use its backend JDBC credentials,
not the database-owner credentials. The connection-service setup and
environment variables are documented in
[`README.md`](./README.md).

## Related implementation and tests

- Frontend dialog and WebSocket client:
  [`frontend/src/components/ConnectionChatDialog.vue`](../../frontend/src/components/ConnectionChatDialog.vue)
- Active connection Chat action:
  [`frontend/src/pages/student/ConnectionsPage.vue`](../../frontend/src/pages/student/ConnectionsPage.vue)
- Chat service authorization and persistence tests:
  [`ChatServiceTest.java`](./src/test/java/com/studybuddy/connection/service/ChatServiceTest.java)
- Chat dialog UI tests:
  [`ConnectionChatDialog.spec.js`](../../frontend/tests/ui/ConnectionChatDialog.spec.js)
