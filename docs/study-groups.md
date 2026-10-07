# Study groups: Lwin's feature

Students can now create groups, browse/filter them, request membership and track their requests. Leaders can edit their group, accept/reject requests, remove other members and close the group. Groups, membership and request history are stored in PostgreSQL. The Groups screen uses real API data and no longer shows sample match scores.

The branch is `feat/study-groups`, based on main `6883542`. The group work fits the current service architecture. The supplied UML was treated as provisional. Student, matching and connection feature contracts were preserved; Jessica's TimeSlot overlap TODOs remain outside this change.

## Run the isolated local demo

Requirements: Java 21, Python 3, PostgreSQL 17 binaries, Node.js, and the frontend dependencies installed using the repository's pinned pnpm version. The launcher uses only Python's standard library. If PostgreSQL is elsewhere, set `PG_BIN` to the directory containing `initdb`, `pg_ctl`, `psql` and `createdb`.

From the repository root:

```sh
python3 scripts/group-local.py demo
```

Open <http://127.0.0.1:5173>. The existing login service permits that frontend origin. The launcher checks ports before starting and will not stop another server to free them.

| Service | Local port |
|---|---|
| Frontend | 5173 |
| Gateway | 18080 |
| User | 18081 |
| Matching | 18083 |
| Group | 18085 |
| PostgreSQL | A free loopback port selected for this run |

Use `alice@local.test`, `bob@local.test`, `carol@local.test` or `admin@local.test`, with password `StudyLocal123!`. These are synthetic local accounts. The launcher creates its own temporary database and signing secret, overrides the shared database configuration and disables the existing demo seeder. No supplied `.env` values are needed. Connection-service is not required for the group flow.

The missing students bootstrap is reproduced in `scripts/group-local-students.sql` strictly as a local fixture. Do not run that fixture against the team's database. The launcher's temporary PostgreSQL cluster trusts loopback connections for this short-lived local demo, so use it only on your development machine. Ctrl-C stops its services and deletes that temporary database; logs remain in the printed temporary directory. Group-service restarts within a running demo retain data. For that check, send SIGUSR1 to the `runnerPid` in the printed directory's `runtime.json`; the launcher restarts only group-service and retains its database/accounts. Do not kill unrelated processes.

Suggested demo: Alice creates a group of capacity three; Bob requests to join; Alice accepts; Bob sees the group in My groups; Alice edits capacity or removes Bob; Carol requests; Alice closes the group and Carol sees the rejected request in My requests.

## Verification

```sh
# All backend modules, with domain, HTTP client and real PostgreSQL group tests:
python3 scripts/group-local.py test

# Real HTTP routes through the running local demo's gateway:
python3 scripts/group-smoke.py

# Frontend build, avoiding changes to the tracked dist folder:
cd frontend
npm run build -- --outDir /tmp/studybuddy-group-build
```

The test runner creates and removes a separate `studybuddy_test` database. Integration tests run only when `GROUP_TEST_JDBC_URL` points to that named database on `127.0.0.1`; they truncate only group tables there. A plain Maven test run without that variable runs domain/client tests and explicitly skips PostgreSQL integration tests. Use the script for the complete check.

Verified on 7 October 2026:

- 18 Java tests passed: four domain tests, three StudentClient HTTP tests, eleven database/API integration tests; no skipped tests in the isolated run. All five backend applications passed the Maven test phase.
- Real signed JWT tests covered valid, expired, wrong-key, wrong-issuer, missing/invalid-uid and wrong-role tokens. Unrelated students and spoofed leader query parameters were denied.
- Actual PostgreSQL tests covered two approvals for the last place, approval racing closure/capacity reduction, duplicate pending requests and rollback when the request write fails after membership insertion.
- Gateway smoke checks exercised create/edit/request/accept/reject/remove/close, validation failures, full groups, history, permissions, and existing student/course/admin reads.
- Browser checks covered login, existing discovery, empty groups, creation with goals/time slots, Bob's request, Alice's approval, request history, editing capacity, account switching and refresh.
- A group-service process restart retained identical group details, Bob's membership and capacity in the same local database.
- Frontend production compilation passed. The team's shared database and main branch were not changed.

These checks establish the group feature, not completion of the other services' existing TODOs. The browser verification used synthetic local data, not shared accounts.

## Group rules and OOP responsibilities

`StudyGroup` owns capacity, membership, leader membership, editable information and ACTIVE/CLOSED state. The leader occupies one place. Capacity defaults to five and must be at least two; it cannot be reduced below current membership. A leader cannot be removed. Course and leader are fixed after creation. Defensive copies prevent callers from altering member lists or mutable TimeSlot objects behind these rules.

`GroupJoinRequest` owns a single PENDING → ACCEPTED/REJECTED transition and UTC creation/response timestamps. It does not add members itself. `StudyGroupService` validates identities and coordinates the group and request; repositories store/reload their state. Closing a group rejects pending requests in the same transaction. Repeated close succeeds; repeated decisions conflict. Rejected applicants and removed members may apply again, retaining prior history. Full groups cannot receive new requests or accept existing ones. Empty goals/availability mean flexible.

Missing/inactive leaders hide their groups from discovery and prevent new requests. Members and previous applicants can read closed/unavailable history. Missing members are labelled unavailable and retain their place until the leader removes them. This is deliberate: the user service physically deletes accounts, and this feature must not make that flow fail through new cross-service foreign keys.

Leave/cancel, leadership transfer, reopening, notifications, chat and compatibility scoring are not included. Availability is validated/stored/displayed; matching overlap calculations are not implemented here.

## Authentication and consistency

The controller reads student identity from the verified JWT `uid` claim, not its email subject or an actor ID supplied by the browser. Group-service validates the same HS256 secret, issuer, expiry and student role as the existing login contract, then checks active profiles and stored ownership. Gateway forwarding was verified using actual login tokens. Response DTOs contain public student summaries, never contact numbers or password data.

Mutations use a `TransactionTemplate` so remote student/course validation finishes before row locks are acquired. Writers lock the group first, then the request; capacity and pending state are rechecked under that lock. Membership and request updates commit or roll back together. Memberships are read in a separate statement after the group lock, so a waiting approval sees the previous writer's committed membership. Read-only repository operations use a consistent snapshot for group and member rows.

This follows [Spring Security JWT validation](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html), [PostgreSQL row locking](https://www.postgresql.org/docs/17/explicit-locking.html) and [partial unique indexes](https://www.postgresql.org/docs/17/indexes-partial.html). The existing framework versions are unchanged.

## Database setup for the team

The new [schema.sql](../backend/group-service/src/main/resources/schema.sql) is additive and transactional. It creates `study_groups`, `study_group_members` and `group_join_requests`, membership/lookup indexes and a unique pending-request index. It has foreign keys only between those group-owned tables. Course codes are not unique in the existing course table, so no foreign key targets that field.

Automatic SQL initialization is disabled in group-service. Review and explicitly apply this file once to the intended database using the backend database owner (the same owner should run the service), before starting the service with the team's database configuration. For example, use the database owner's SQL editor, or `psql -v ON_ERROR_STOP=1 -f backend/group-service/src/main/resources/schema.sql` with a separately configured connection. Do not paste database credentials into Git. Re-running the file does not erase existing group records.

The script enables RLS and revokes public/anon/authenticated access only on the new tables. Server-side JDBC and Java authorization serve this application; there are no direct browser database policies. The actual backend owner or a deliberately configured privileged backend role must have access. See [Supabase's API table security guidance](https://supabase.com/docs/guides/api/securing-your-api). Shared-database application was not performed in this branch's local verification.

Group-service reads `SUPABASE_JDBC_URL`, `SUPABASE_DB_USER`, `SUPABASE_DB_PASSWORD`, `JWT_SECRET`, `USER_SERVICE_URL`, `DB_POOL_SIZE` and `GROUP_SERVICE_PORT`, following existing project conventions. The secret must match user-service/gateway and contain at least 32 bytes. The normal group-service port remains 8085; the isolated launcher overrides it.

## API

All routes require a student bearer token. IDs are UUIDs. JSON errors have a `message` field: 400 invalid input, 401 invalid login, 403 permission/account failure, 404 absent resource, 409 state/capacity conflict, 503 unavailable service/storage.

| Route | Purpose |
|---|---|
| `GET /api/groups` | Active discovery; `?scope=mine` includes caller's groups/history |
| `GET /api/groups/{id}` | Detail, member summaries and caller-specific action state |
| `POST /api/groups` | Create; returns 201 |
| `PUT /api/groups/{id}` | Leader edits information |
| `POST /api/groups/{id}/close` | Leader closes; returns 204 |
| `DELETE /api/groups/{id}/members/{memberId}` | Leader removes another member; returns 204 |
| `POST /api/groups/{id}/join-requests` | Caller requests; returns 201 |
| `GET /api/groups/{id}/join-requests` | Leader reads incoming history |
| `GET /api/groups/join-requests/mine` | Caller's request history |
| `POST /api/groups/join-requests/{id}/accept` | Leader accepts |
| `POST /api/groups/join-requests/{id}/reject` | Leader rejects |

Create/edit JSON:

```json
{
  "groupName": "OOP Revision Circle",
  "description": "Practice Java together",
  "courseCode": "IS442",
  "maxGroupSize": 5,
  "meetingMode": "ONLINE",
  "studyGoals": ["EXAM_PREPARATION"],
  "availability": [{"day": "MONDAY", "startTime": "18:00", "endTime": "19:00"}]
}
```

Course is required on creation; when supplied on edit it must match the existing course. Updating replaces the editable fields, so send the complete form. The existing stub routes previously accepted actor IDs and other form data as query parameters; the implemented routes derive actors from JWTs and accept JSON instead.

## Files added

Paths below are relative to the repository root.

| File | Purpose |
|---|---|
| `backend/group-service/src/main/java/com/studybuddy/group/config/SecurityConfiguration.java` | JWT validation and student access |
| `backend/group-service/src/main/java/com/studybuddy/group/controller/GroupExceptionHandler.java` | Consistent HTTP errors |
| `backend/group-service/src/main/java/com/studybuddy/group/dto/GroupInput.java` | Validated form input |
| `backend/group-service/src/main/java/com/studybuddy/group/dto/GroupView.java` | Group/member response |
| `backend/group-service/src/main/java/com/studybuddy/group/dto/JoinRequestView.java` | Request-history response |
| `backend/group-service/src/main/java/com/studybuddy/group/service/GroupException.java` | Application error status/message |
| `backend/group-service/src/main/resources/schema.sql` | Group tables, constraints, indexes and access controls |
| `backend/group-service/src/test/java/com/studybuddy/group/GroupDomainTest.java` | Four domain tests |
| `backend/group-service/src/test/java/com/studybuddy/group/GroupIntegrationTest.java` | Eleven PostgreSQL/authentication/transaction tests |
| `backend/group-service/src/test/java/com/studybuddy/group/StudentClientTest.java` | Three HTTP boundary tests |
| `frontend/src/composables/useGroups.js` | Group state/loading/actions and account isolation |
| `frontend/src/services/api.js` | Bearer API requests and session failure handling |
| `scripts/group-local.py` | Isolated database, tests and local demo runner |
| `scripts/group-local-students.sql` | Local-only missing student-schema fixture |
| `scripts/group-smoke.py` | Real gateway route checks |
| `docs/study-groups.md` | This setup, verification and handover guide |

Existing files changed: group-service's POM/application configuration, StudentClient, StudyGroupController, StudyGroup, GroupJoinRequest, both repositories, StudyGroupService; frontend GroupsPage and the removal of group-only mock state in useAppState; and the root README link to this guide. Existing tracked frontend build output is excluded from the change.

The October UI pass separates the editor and details into `frontend/src/components/groups/GroupEditor.vue` and `GroupDetails.vue`. See [UI review](ui-review.md) for the full screen inventory, added files and browser verification.
