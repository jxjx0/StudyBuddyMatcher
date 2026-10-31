# Student chat

This document describes student chat: the Java classes involved, the
user-facing flow, and how messages move between the frontend,
connection-service, and PostgreSQL.

## How students use chat

1. When a student signs in, the app opens one WebSocket connection to `/ws`
   and authenticates its STOMP session with the student's bearer JWT. It
   subscribes to the live topic of every active connection, so unread badges
   update on any page.
2. The **Messages** tab lists active chats and ended connections that have
   visible history, newest activity first. Ended chats are marked read-only.
   The nav item shows the total unread count. **Chat** on a My Connections
   card opens that conversation.
3. Opening a conversation loads its latest 100 messages. Active conversations
   are marked read, and new messages are displayed immediately for both
   connected students.
4. A typed message stays in the box until the server broadcasts it back, so a
   rejected send shows an error instead of losing the text. Messages are
   limited to 2,000 characters.

Students in a connection can read its history after it ends, but only active
connections can subscribe or send messages. Ending a connection disables
communication without deleting saved messages. Students can delete a chat from
their own history; this hides messages sent up to that point only for that
student, while the other student's history remains unchanged. Later messages
make the chat visible again. Deleting a chat does not end an active connection.

## Java classes

All Java paths below are relative to
`src/main/java/com/studybuddy/connection/`.

| Class | Responsibility |
|---|---|
| `config/ChatWebSocketConfiguration.java` | Enables Spring's STOMP-over-WebSocket support; registers `/ws`; configures `/app` application destinations and `/topic` broadcasts; installs the chat inbound interceptor; configures allowed browser origins and a dedicated heartbeat scheduler. |
| `config/ChatAuthenticationInterceptor.java` | Validates the bearer JWT on STOMP `CONNECT`, requires the student role, and binds the JWT `uid` to the WebSocket `Principal`. Before subscription, it checks that the principal belongs to the active connection. It also restricts client `SEND` messages to the supported chat destination. |
| `controller/ChatController.java` | Receives STOMP sends at `/app/connections/{connectionId}/send`, asks `ChatService` to authorize and persist the message, then broadcasts the saved message to `/topic/connections/{connectionId}`. |
| `controller/ConnectionController.java` | Exposes `GET /api/connections/{connectionId}/messages` (history), `GET /api/connections/chats` (visible conversations and unread counts), `POST /api/connections/{connectionId}/read` (mark read), and `DELETE /api/connections/{connectionId}/chat` (hide this student's history), all using the authenticated JWT identity. |
| `service/ChatService.java` | Applies chat business rules: validates UUIDs and message contents, checks participant membership for history and deletion, requires active membership for read tracking and sending, and coordinates persistence. |
| `repository/ChatMessageRepository.java` | Uses Spring `JdbcTemplate` to insert chat messages, fetch the latest 100 visible messages in chronological order, build the conversation list with unread counts, record per-student deletion timestamps, and record when a student last read a conversation. |
| `dto/ChatSummary.java` | One conversation in the chat list: connection and partner IDs, active/hidden state, latest message (or null), and unread count. |
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

Unread counts use the `connection_chat_reads` table, which holds one row per
student per conversation: `connection_id`, `student_id`, and `last_read_at`.
A message is unread when the partner sent it after the student's
`last_read_at`; a student with no row has never opened the conversation. This
table already exists in the shared Supabase database; no migration file in this
repository creates it.

The `connection_chat_deletions` table stores a per-student history cutoff.
History and summaries only include messages sent after that cutoff. A new
message makes the chat visible again, while the other participant's cutoff and
history remain unchanged.

Migration 003 enables row-level security and revokes access from browser roles.
The Vue app does not connect to Supabase directly. Instead, connection-service
uses its configured server-side JDBC datasource (`SUPABASE_JDBC_URL`,
`SUPABASE_DB_USER`, and `SUPABASE_DB_PASSWORD`) to access the table.

### Loading history

```text
MessagesPage.vue (useChat.js)
  -> GET /api/connections/{connectionId}/messages
  -> API gateway
  -> ConnectionController.findMessages()
  -> ChatService.history(connectionId, JWT uid)
  -> StudyBuddyConnectionRepository.findById()
  -> verify connection participant
  -> ChatMessageRepository.findRecent()
  -> JdbcTemplate SELECT from connection_chat_messages (latest 100)
  -> ChatMessage DTO list returned to the Messages page
```

The request identity comes from the JWT, not a student ID supplied by the
browser.

### Sending a live message

```text
MessagesPage.vue (useChat.js)
  -> STOMP SEND /app/connections/{connectionId}/send
  -> ChatAuthenticationInterceptor validates JWT principal and destination
  -> ChatController.send()
  -> ChatService.send(connectionId, Principal name, content)
  -> verify connection is active and includes sender
  -> ChatMessageRepository.save()
  -> JdbcTemplate INSERT ... RETURNING from connection_chat_messages
  -> broadcast saved ChatMessage to /topic/connections/{connectionId}
  -> both subscribed clients add the message to the conversation
     and update the conversation list; the recipient's unread count
     increases unless that conversation is open on screen
```

### Unread counts

```text
useChat.js
  -> GET /api/connections/chats
  -> ChatService.summaries(JWT uid)
  -> ChatMessageRepository.findSummaries()
  -> per active connection: latest message, and count of partner messages
     sent after connection_chat_reads.last_read_at
```

Opening an active conversation, or receiving a message while it is open, calls
`POST /api/connections/{connectionId}/read`, which upserts the student's
`last_read_at` to the current database time. Ended conversations remain
readable but do not update unread state.

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
- The HTTP history and delete endpoints use the authenticated JWT `uid` and
  verify participant membership. Mark-read, sending, and STOMP subscriptions
  additionally require the connection to remain active.
- The service returns a not-found response for missing or unrelated
  connections so chat data is not exposed to nonparticipants.
- Delete is scoped to the requesting student through
  `connection_chat_deletions`; it sets a history cutoff and does not delete
  messages or affect the partner's view.

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
not the database-owner credentials. Apply
[`migrations/004_create_chat_deletions.sql`](./migrations/004_create_chat_deletions.sql)
once as well to enable per-student chat deletion. The connection-service setup
and environment variables are documented in
[`README.md`](./README.md).

## Related implementation

- Shared chat connection and unread state:
  [`frontend/src/composables/useChat.js`](../../frontend/src/composables/useChat.js)
- Messages tab:
  [`frontend/src/pages/student/MessagesPage.vue`](../../frontend/src/pages/student/MessagesPage.vue)
- Active connection Chat action:
  [`frontend/src/pages/student/ConnectionsPage.vue`](../../frontend/src/pages/student/ConnectionsPage.vue)
