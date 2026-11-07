# Student chat

This document describes the real-time chat for study-buddy connections: the
Java classes involved, the student experience, and how messages move between
the frontend, connection-service, and PostgreSQL.

## How students use chat

1. When a student signs in, the app opens one WebSocket connection to `/ws`
   and authenticates its STOMP session with the student's bearer JWT. It
   subscribes to active connections so unread badges update on any page.
2. The **Messages** tab lists active chats and ended connections with visible
   history, newest activity first. It shows the latest message and unread
   count. Ended connections are read-only.
3. Opening a conversation loads its latest 100 visible messages. Active
   conversations are marked read, and new messages appear immediately for
   both connected students.
4. A message stays in the input until the server broadcasts it back, so a
   rejected send does not lose its text. Messages are limited to 2,000
   characters.
5. Students can delete a chat from their own history. This does not delete
   the other student's copy or end an active connection; later messages make
   the chat visible again.
6. If the same students reconnect, the previous connection record is reopened.
   The existing chat history and conversation ID are preserved, and messaging
   is enabled again.

Only active connections can subscribe or send. Participants can read history
after a connection ends. Missing connections and requests from nonparticipants
are rejected by the service.

## Java classes

Java paths below are relative to
`src/main/java/com/studybuddy/connection/`.

| Class | Responsibility |
|---|---|
| `config/ChatWebSocketConfiguration.java` | Registers `/ws`, configures STOMP `/app` destinations and `/topic` broadcasts, allowed origins, interceptor, and heartbeats. |
| `config/ChatAuthenticationInterceptor.java` | Authenticates STOMP `CONNECT` using the bearer JWT, requires the student role, and checks active membership for subscriptions. It restricts client sends to supported destinations. |
| `controller/ChatController.java` | Receives live sends at `/app/connections/{connectionId}/send`, delegates to `ChatService`, then broadcasts the saved message. |
| `controller/ConnectionController.java` | Serves history, chat summaries, mark-read, and per-student chat deletion HTTP endpoints using the authenticated JWT identity. |
| `service/ChatService.java` | Validates message content and UUIDs; checks participant membership for history/deletion and active status for sending/read tracking. |
| `repository/ChatMessageRepository.java` | Uses `JdbcTemplate` to persist and retrieve messages, summaries, unread counts, read times, and per-student deletion cutoffs. |
| `dto/ChatMessage.java` | Message ID, connection ID, sender ID, content, and timestamp. |
| `dto/ChatSummary.java` | Connection and partner IDs, active/hidden state, latest message, and unread count. |
| `dto/SendChatMessageRequest.java` | Validates message content as nonblank and no longer than 2,000 characters. |
| `repository/StudyBuddyConnectionRepository.java` and `model/StudyBuddyConnection.java` | Provide connection status and participant membership checks. |
| `config/SecurityConfiguration.java` | Secures the HTTP API and JWT decoder. STOMP authentication is handled by `ChatAuthenticationInterceptor`. |

## How the classes communicate

### Load history

```text
MessagesPage.vue (useChat.js)
  -> GET /api/connections/{connectionId}/messages
  -> API gateway
  -> ConnectionController.findMessages()
  -> ChatService.history(connectionId, JWT uid)
  -> StudyBuddyConnectionRepository.findById()
  -> verify participant membership
  -> ChatMessageRepository.findRecent()
  -> JdbcTemplate SELECT from connection_chat_messages (latest 100 visible)
  -> ChatMessage list returned to the Messages page
```

The identity comes from the JWT, not a student ID supplied by the browser.

### Send a live message

```text
MessagesPage.vue (useChat.js)
  -> STOMP SEND /app/connections/{connectionId}/send
  -> ChatAuthenticationInterceptor authenticates principal and destination
  -> ChatController.send()
  -> ChatService.send(connectionId, principal name, content)
  -> verify connection is active and includes sender
  -> ChatMessageRepository.save()
  -> JdbcTemplate INSERT ... RETURNING from connection_chat_messages
  -> broadcast saved message to /topic/connections/{connectionId}
  -> both subscribed clients update the conversation
```

The server saves the message before broadcasting it, so the broadcast includes
the database-generated timestamp and message ID. The frontend merges messages
by ID to avoid duplicates when history and live updates arrive together.

### Summaries and unread counts

```text
useChat.js
  -> GET /api/connections/chats
  -> ConnectionController.findChats()
  -> ChatService.summaries(JWT uid)
  -> ChatMessageRepository.findSummaries()
  -> latest visible message and unread count for each conversation
```

Unread counts use `connection_chat_reads`. Opening an active conversation, or
receiving a message while it is open, calls
`POST /api/connections/{connectionId}/read`. Ended conversations remain
readable but do not accrue unread counts.

### Delete a chat

```text
MessagesPage.vue (useChat.js)
  -> DELETE /api/connections/{connectionId}/chat
  -> ConnectionController.deleteChat()
  -> ChatService.deleteForStudent(connectionId, JWT uid)
  -> verify participant membership
  -> ChatMessageRepository.deleteForStudent()
  -> upsert the student's cutoff in connection_chat_deletions
```

The cutoff hides messages sent up to that time for that student only; messages
are not deleted from the shared message table. Later messages appear in the
conversation again.

## Data and access

- `connection_chat_messages` stores message ID, connection ID, sender ID,
  content, and database-generated timestamp.
- `connection_chat_reads` stores each student's last-read timestamp. **No
  `schema.sql` creates this table** — it exists on the shared Supabase
  database because it was added by hand. See
  [`connection_chat_reads` is not created for you](./README.md#connection_chat_reads-is-not-created-for-you)
  for the DDL to run on a fresh database.
- `connection_chat_deletions` stores the per-student history cutoff.
- Reopening a connection reuses its connection ID, so messages and deletion
  cutoffs remain associated with the same conversation.
- The Vue app does not connect to Supabase directly. Connection-service uses
  server-side `SUPABASE_JDBC_URL`, `SUPABASE_DB_USER`, and
  `SUPABASE_DB_PASSWORD` credentials.
- STOMP `CONNECT` validates the JWT and requires `ROLE_STUDENT`. Subscriptions
  and sends require active membership; HTTP history and deletion require
  participant membership. The sender ID always comes from the authenticated
  principal.
- No `schema.sql` enables row-level security on these tables (group-service is
  the only service whose schema does). The browser never reaches them
  directly: every read and write goes through connection-service, which
  authorizes the caller's JWT first.

## Routing and deployment

- HTTP requests go through Vite's `/api` proxy to the gateway at
  `http://localhost:8080`, which routes `/api/connections/**` to
  connection-service.
- In local development, Vite proxies `/ws` to connection-service at
  `http://localhost:8084`. Set `VITE_CHAT_WS_TARGET` to override it.
- A deployment proxy must preserve the WebSocket upgrade. Set
  `CHAT_ALLOWED_ORIGINS` to the exact frontend origins; do not use wildcard
  origins in production.
- The built-in broker is in-memory and intended for one connection-service
  instance. Multiple instances require a shared broker.

## Database tables

Chat uses three tables. Two are created by the service's own
[`schema.sql`](./src/main/resources/schema.sql) on startup unless
`SPRING_SQL_INIT_MODE=never` is set:

- `connection_chat_messages` — persisted history, keyed by connection
- `connection_chat_deletions` — which student has hidden which conversation

The third, `connection_chat_reads`, is **not** created by any `schema.sql`.
Unread counts and mark-read fail on a database where it is missing; the DDL is
in the
[connection-service README](./README.md#connection_chat_reads-is-not-created-for-you).

There are no separate migration files to apply. Use the backend JDBC
credentials for the running service, not database-owner credentials. See the
[connection-service README](./README.md) for configuration.

## Related implementation

- Chat connection and unread state:
  [`frontend/src/composables/useChat.js`](../../frontend/src/composables/useChat.js)
- Messages page:
  [`frontend/src/pages/student/MessagesPage.vue`](../../frontend/src/pages/student/MessagesPage.vue)
- Active connection chat action:
  [`frontend/src/pages/student/ConnectionsPage.vue`](../../frontend/src/pages/student/ConnectionsPage.vue)
