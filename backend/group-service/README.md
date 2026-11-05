# Group service

Owns study groups, their membership and join requests. Runs on port `8085`; the
gateway routes `/api/groups/**` here.

For the feature list see the [project README](../../README.md); for shared
configuration see the [backend README](../README.md).

## The study group leader role

The project brief describes a study group leader as a third kind of user, but
it is not a third kind of account. Any student who creates a group becomes its
leader, so leadership is a property of the group (`leader_id`) rather than a
role on the account. A student can lead one group and be an ordinary member of
another. This keeps `user_accounts.user_role` to just `STUDENT` and `ADMIN`,
and means no account has to be promoted or demoted.

## Domain model

[`StudyGroup`](src/main/java/com/studybuddy/group/model/StudyGroup.java) keeps
`groupId`, `courseCode` and `leaderId` final — a group cannot change course or
change hands after creation. The group enforces its own invariants (maximum
size at least 2, a non-blank name, valid time slots, and whether it is still
active) rather than leaving them to the service layer.

| Type | Values |
|---|---|
| `GroupStatus` | `ACTIVE`, `CLOSED` |
| `GroupJoinRequestStatus` | `PENDING`, `ACCEPTED`, `REJECTED`, `CANCELLED` |
| `MeetingMode` | `ONLINE`, `IN_PERSON`, `EITHER` |
| `StudyGoal` | `CONCEPT_REVIEW`, `PROBLEM_SOLVING`, `EXAM_PREPARATION`, `PROJECT_DISCUSSION` |

## Business rules

- Creating a group requires an existing course code and an active student
  profile; the creator is recorded as the leader.
- Only the leader can update the group, close it, remove a member, or accept
  and reject join requests. Non-leaders get `403 Only the group leader can do
  this`.
- Closing a group automatically rejects every request still pending, so no
  request is left hanging against a dead group.
- A join request is refused if the group is closed or full, or if the requester
  is already a member. A partial unique index (`one_pending_group_request`)
  allows only one pending request per student per group, so a duplicate is
  rejected by the database rather than by a race-prone check.
- A student can cancel their own pending request.
- Both the requester and the group leader must be active students; a suspended
  or deleted account cannot join a group or keep accepting members.
- A row constraint keeps `responded_at` null exactly when the status is
  `PENDING`, so a decided request can never be missing its timestamp.

Concurrency is handled by taking locks in a consistent order — group first,
then request — in approval, cancellation and closing alike, which avoids
deadlocks between two leaders acting at once.

## API

| Method | Path | Who |
|---|---|---|
| `GET` | `/api/groups` | Any student (`scope` query narrows the list) |
| `GET` | `/api/groups/overview` | Any student — the dashboard summary |
| `GET` | `/api/groups/{id}` | Any student |
| `GET` | `/api/groups/{id}/details` | Any student; members and requests included for the leader |
| `POST` | `/api/groups` | Any student; becomes the leader |
| `PUT` | `/api/groups/{id}` | Leader |
| `POST` | `/api/groups/{id}/close` | Leader |
| `DELETE` | `/api/groups/{id}/members/{memberId}` | Leader |
| `POST` | `/api/groups/{id}/join-requests` | Any student |
| `DELETE` | `/api/groups/{id}/join-requests` | The requester, cancelling |
| `GET` | `/api/groups/{id}/join-requests` | Leader |
| `GET` | `/api/groups/join-requests/mine` | Any student |
| `POST` | `/api/groups/join-requests/{id}/accept` | Leader |
| `POST` | `/api/groups/join-requests/{id}/reject` | Leader |

All require a bearer JWT signed with the shared `JWT_SECRET`, including when
called directly on port 8085.
[`GroupExceptionHandler`](src/main/java/com/studybuddy/group/controller/GroupExceptionHandler.java)
maps `GroupException` onto the right status code in one place.

Rejecting a join request does not carry a leader's message — that optional
field was dropped before implementation, as recorded in
[CHANGES_README.md](../../CHANGES_README.md).

## Database

Unlike the other services, this one **never** runs its schema automatically —
`mode` is fixed at `never`. Apply
[`src/main/resources/schema.sql`](src/main/resources/schema.sql) once through
the Supabase SQL Editor or a migration, using a database-owner connection.

It creates three tables and nothing else:

| Table | Holds |
|---|---|
| `study_groups` | Group details, leader, status, goals and availability |
| `study_group_members` | Membership, cascading on group deletion |
| `group_join_requests` | Requests and their status |

The script enables row-level security on all three, revokes access from
`PUBLIC`, and revokes the Supabase `anon` and `authenticated` browser roles if
those roles exist — so the browser can never reach these tables directly, only
through this service's authenticated API. It uses `CREATE TABLE IF NOT EXISTS`
throughout and never alters existing grants, so it is safe to re-run and does
not disturb the user, course or matching tables.

There are deliberately **no cross-service foreign keys**: `leader_id` and
`student_id` reference students owned by user-service, which this service
reaches over HTTP through
[`StudentClient`](src/main/java/com/studybuddy/group/client/StudentClient.java).

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `GROUP_SERVICE_PORT` | `8085` | Listen port |
| `JWT_SECRET` | — | **Required**, ≥32 characters |
| `USER_SERVICE_URL` | `http://localhost:8081` | Where to fetch student profiles |
| `SUPABASE_JDBC_URL`, `SUPABASE_DB_USER`, `SUPABASE_DB_PASSWORD` | local PostgreSQL | Database connection |

## Formatting

This service is the one Java module under Spotless. From `backend`:

```sh
./mvnw -pl group-service spotless:check
./mvnw -pl group-service spotless:apply
```

On Windows use `mvnw.cmd`. See
[Study Groups formatting and checks](../README.md#study-groups-formatting-and-checks)
for the matching frontend commands and the CI workflow.

## Run

From `backend`:

```powershell
.\mvnw.cmd -pl group-service spring-boot:run
```

On macOS/Linux use `./mvnw`. On Windows, `start-backend.ps1` only starts this
service when you pass `-All`.
