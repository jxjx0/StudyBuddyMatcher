# Feature Changes and Fixes

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
- Added a Messages tab listing every active connection with its latest message
  and unread count, plus a total unread badge in the navigation that updates
  live on any page. The Chat action on each active connection opens that
  conversation. Messages are limited to 2,000 characters and a conversation
  loads its latest 100 saved messages.
- A message stays in the input until the server confirms it, so a rejected
  send shows an error instead of silently losing the text.
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

## Configuration and secrets

Backend database settings belong in the local `backend/.env` file. Do not commit
that file or put database credentials in frontend code. This document does not
include credentials.
