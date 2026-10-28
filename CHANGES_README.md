# Feature Changes, Fixes, and Tests

This document summarizes the connection-request and study-group changes made
for StudyBuddyMatcher. It supplements the project setup instructions in
[README.md](./README.md).

## Connection requests

- Added a confirmation dialog when sending a connection request. Students can
  include an optional message of up to 500 characters.
- Persisted connection requests, including their message, and established
  connections in Supabase PostgreSQL. Messages are stored in the
  `match_requests.message` column and are associated with their request.
- Added server-side JDBC persistence in connection-service. The browser does
  not connect directly to these tables.
- When a student receives a pending request from another student, sending a
  reciprocal request accepts the existing request and establishes the
  connection.
- Prevented duplicate outgoing requests from becoming server errors. The
  service returns a conflict response for an already-pending duplicate.
- Added pending-request state in the UI, including a disabled “Request Sent”
  state instead of allowing another request to be submitted.
- Added request cancellation from the sender's request controls, including the
  Pending Requests tab. A cancelled request no longer appears as pending.
- Kept contact details private until the connection is accepted.
- Added real-time chat for active connections using an authenticated
  WebSocket/STOMP channel and persisted conversation history.
- Added a Chat action on each active connection. Messages are limited to 2,000
  characters and the conversation loads its latest 100 saved messages.
- Restricted chat history, subscriptions, and sending to the two students in
  that active connection.
- Configured a dedicated STOMP heartbeat scheduler to help detect stale chat
  WebSocket connections without colliding with Spring's default scheduler bean.

### Database setup and cancellation fix

The connection service uses the backend Supabase JDBC settings documented in
[the connection-service README](./backend/connection-service/README.md).

The service creates its tables on startup from
[schema.sql](./backend/connection-service/src/main/resources/schema.sql),
which permits the `CANCELLED` status.

The reported HTTP 500 on request cancellation was caused by the database
rejecting `CANCELLED` under its existing status constraint. For a
`match_requests` table created before cancellation support, apply
[002_allow_cancelled_match_requests.sql](./backend/connection-service/migrations/002_allow_cancelled_match_requests.sql)
once in the Supabase SQL Editor. The shared Supabase database already has it.

## Study groups

- Group leaders can review and accept or reject join requests in the group
  details dialog.
- The temporary Accept/Reject controls on group cards were removed to avoid
  clutter when many requests are pending. The overview no longer loads a
  separate request list for each group; requests load when the leader opens
  that group's details.
- Students can cancel their pending group join request from the group details
  dialog, a group card, or the My Requests view.
- Added explanatory text to the member-removal confirmation so the leader can
  see what the confirmation button will do.

### Not included

The optional message field for a group leader's rejection response was
cancelled before implementation. Rejecting a group join request does not
currently send or save a leader message.

## Relevant tests

The following existing test files cover these features and related behavior:

- [groups.spec.js](./frontend/tests/ui/groups.spec.js)
  - `leaders see pending work separately from completed request history`
    verifies that pending requests and request history are shown in group
    details, not as card-level decision controls.
  - `member removal confirmation explains the final action` checks the
    confirmation text.
  - `students can cancel a pending request from a group card and reapply`,
    `students can cancel a pending request from the detail dialog`, and
    `pending requests can be cancelled from My requests` cover group-request
    cancellation entry points.
  - Other tests cover request-to-join feedback, loading behavior, group
    details, and error handling.
- [MatchRequestServiceTest.java](./backend/connection-service/src/test/java/com/studybuddy/connection/service/MatchRequestServiceTest.java)
  - Covers reciprocal-request acceptance, duplicate outgoing-request conflict,
    cancellation by the sender, and rejecting cancellation by a non-sender.
- [ChatServiceTest.java](./backend/connection-service/src/test/java/com/studybuddy/connection/service/ChatServiceTest.java)
  covers active-connection authorization, message trimming and validation,
  plus rejecting access after a connection ends.
- [ConnectionChatDialog.spec.js](./frontend/tests/ui/ConnectionChatDialog.spec.js)
  covers authenticated WebSocket setup, loading persisted history, sending and
  receiving messages, error display, and closing the socket.
- [GroupIntegrationTest.java](./backend/group-service/src/test/java/com/studybuddy/group/GroupIntegrationTest.java)
  - Covers group-request approval and history, request cancellation,
    authorization, and concurrency behavior.
- [GroupDomainTest.java](./backend/group-service/src/test/java/com/studybuddy/group/GroupDomainTest.java)
  - Covers terminal join-request decisions and restoration of request history.

Run frontend tests from the `frontend` directory:

```bash
npm test
```

Run the connection-service tests from the `backend` directory:

```bash
.\mvnw.cmd -pl connection-service test
```

Run the group-service tests from the `backend` directory:

```bash
.\mvnw.cmd -pl group-service test
```

The group integration tests require the isolated test database configured by
the `GROUP_TEST_*` environment variables; see
[GroupIntegrationTest.java](./backend/group-service/src/test/java/com/studybuddy/group/GroupIntegrationTest.java).
Automated tests/builds and the live Supabase migration have not been run in this
environment; the required pnpm and PowerShell/Maven runners were unavailable.

## Configuration and secrets

Backend database settings belong in the local `backend/.env` file. Do not commit
that file or put database credentials in frontend code. This document does not
include credentials.
