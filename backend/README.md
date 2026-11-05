# Study Buddy Matcher backend

Five Spring Boot services sharing one Supabase PostgreSQL database. The Vue app
calls the API gateway on port `8080`; the gateway verifies JWTs and routes each
request to the owning service. `user-service` owns accounts, student profiles
and course data; the other services call it over HTTP rather than reading its
tables.

For the system overview, feature list and design principles, see the
[project README](../README.md).

| Service | Port | Owns | Docs |
|---|---|---|---|
| api-gateway | 8080 | Routing, JWT verification | [README](api-gateway/README.md) |
| user-service | 8081 | `user_accounts`, `students`, `courses` | [README](user-service/README.md) |
| matching-service | 8083 | `matching_configuration`, `matching_criteria` | [README](matching-service/README.md) |
| connection-service | 8084 | `match_requests`, `study_buddy_connections`, `connection_chat_messages`, `connection_chat_deletions` | [README](connection-service/README.md), [CHAT_README](connection-service/CHAT_README.md) |
| group-service | 8085 | `study_groups`, `study_group_members`, `group_join_requests` | [README](group-service/README.md) |

## Supabase setup

1. In the Supabase dashboard, open **Connect** and copy the host, port,
   database, username and database password. Pooler usernames include the
   project reference.
2. Copy [`.env.example`](.env.example) to `backend/.env` and fill in your
   values. `.env` is ignored by Git. Keep `sslmode=require` in the JDBC URL.
3. On a brand-new database, create the `students` table and apply the group
   schema — see [Schema initialization](#schema-initialization) below. Neither
   is created automatically.

### Which port you connect on

Supabase offers the database on two ports, and the choice changes one thing in
the JDBC URL.

| Port | Mode | Needs `prepareThreshold=0`? |
|---|---|---|
| `5432` | Direct connection or session pooler — each client holds its own database session | **No** |
| `6543` | Transaction pooler — sessions are shared between transactions | **Yes** |

On `6543` the pooler hands the same backend session to different clients
between transactions, so a server-side prepared statement created by one
connection can collide with another and you get errors like
`prepared statement "S_1" already exists`. Appending `prepareThreshold=0` tells
the PostgreSQL JDBC driver never to create them.

**This project currently connects on `5432`**, so `prepareThreshold=0` is not
required and omitting it is correct. Add it only if you switch the team to the
transaction pooler on `6543`.

Each service opens at most two connections by default and releases idle ones
after about a minute, so all five services together use at most ten. Do not
stop teammates' sessions to free the pool.

References: [Supabase connection modes](https://supabase.com/docs/guides/database/connecting-to-postgres)
and [JDBC prepared-statement configuration](https://supabase.com/docs/guides/troubleshooting/disabling-prepared-statements-qL8lEL).

## Environment variables

All of these are read from `backend/.env`. Every service looks for that file at
several relative paths, so it works whether you start from the repository root
or from `backend/`.

### Required

| Variable | Notes |
|---|---|
| `SUPABASE_JDBC_URL` | `jdbc:postgresql://<host>:5432/postgres?sslmode=require`. Add `&prepareThreshold=0` only on port `6543` — see [Which port you connect on](#which-port-you-connect-on). Falls back to a local database if unset. |
| `SUPABASE_DB_USER` | Username from Supabase **Connect**. Falls back to `postgres`. |
| `SUPABASE_DB_PASSWORD` | Database password. Falls back to `postgres`. |
| `JWT_SECRET` | At least 32 random characters, identical across all services. **No insecure fallback** — api-gateway, user-service, matching-service, connection-service and group-service all fail to start without it. |
| `CONTACT_SERVICE_SECRET` | A *different* random secret of at least 32 characters, shared only by user-service and connection-service. Both fail to start without it. Protects the internal contact lookup; never send it to the browser. |

### The two secrets

**If you are joining the team's existing setup, do not generate new secrets.**
Ask a teammate for the project's current `JWT_SECRET` and
`CONTACT_SERVICE_SECRET` and paste those exact values into your `backend/.env`.

They have to match across machines. `JWT_SECRET` signs the token that
user-service issues and that all five services verify, so a different value on
your machine means every login is rejected with a 401. `CONTACT_SERVICE_SECRET`
is checked byte-for-byte when connection-service asks user-service for a
buddy's contact number, so a mismatch breaks the contact reveal. Share them
through a private channel — never commit them.

Generate a new pair only when starting a fresh deployment of this project.
Run the command for your platform twice, once per secret:

**macOS / Linux**

```sh
openssl rand -hex 32
```

**Windows (PowerShell)**

```powershell
$bytes = New-Object byte[] 32
[Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
-join ($bytes | ForEach-Object { $_.ToString('x2') })
```

Both produce 64 hexadecimal characters from a cryptographically secure source.
Hex rather than Base64, because `.env` is parsed as a Java properties file
where `\` is an escape character; hex avoids the problem entirely. Never type a
secret by hand or reuse a password.

The enforced minimum is 32 **bytes**, which for ordinary ASCII means 32
characters. A 32-character secret passes with no margin to spare, so prefer 64
when choosing a new one.

### Optional

| Variable | Default | Notes |
|---|---|---|
| `DB_POOL_SIZE` | `2` | Max Hikari pool size per service. |
| `DB_MIN_IDLE` | `0` | Minimum idle connections. |
| `JWT_TTL_MINUTES` | `60` | Access token lifetime. |
| `SPRING_SQL_INIT_MODE` | `always` | **Only affects connection-service** — see below. |
| `CHAT_ALLOWED_ORIGINS` | `http://localhost:5173,http://127.0.0.1:5173` | Comma-separated browser origins allowed to open the chat WebSocket. Never use a wildcard in production. |
| `GATEWAY_PORT`, `USER_SERVICE_PORT`, `MATCHING_SERVICE_PORT`, `CONNECTION_SERVICE_PORT`, `GROUP_SERVICE_PORT` | 8080/8081/8083/8084/8085 | Override a service's port. |
| `USER_SERVICE_URL`, `MATCHING_SERVICE_URL`, `CONNECTION_SERVICE_URL`, `GROUP_SERVICE_URL` | `http://localhost:<port>` | Where each service can be reached, used by the gateway and the inter-service clients. |
| `DEMO_DATA_ENABLED` | `false` | See [Demo population](#demo-population). |
| `SEED_ADMIN_EMAIL` | `admin@studybuds.sg` | Only used when seeding. |
| `SEED_ADMIN_PASSWORD`, `SEED_STUDENT_PASSWORD` | — | Required when seeding; at least 8 characters. |

A complete `backend/.env` needs only these five lines. Everything in the
optional table above already has a working default, so leaving a variable out
is the same as setting it to its default value:

```text
SUPABASE_JDBC_URL=jdbc:postgresql://<host>:5432/postgres?sslmode=require
SUPABASE_DB_USER=<username from Supabase Connect>
SUPABASE_DB_PASSWORD=<database password>
JWT_SECRET=<the team's shared secret>
CONTACT_SERVICE_SECRET=<the team's shared contact secret>
```

Add `DEMO_DATA_ENABLED=true` and the three `SEED_*` values only when you want
the sample data.

This backend connects to PostgreSQL directly, so it does **not** need the
Supabase project URL, anon/publishable key or service-role key. The database
password is the credential it uses. Never put the database password or a
service-role key in Vue/Vite variables or anywhere else the browser can read.

## Schema initialization

Each service decides for itself whether to run its `schema.sql` on startup, and
they do **not** all behave the same way. `SPRING_SQL_INIT_MODE` only changes
connection-service; the others are fixed in their `application.yml`.

| Service | Runs `schema.sql` on startup? | Controlled by |
|---|---|---|
| user-service | Yes, always | Fixed (`mode: always`) |
| matching-service | Yes, always | Fixed (`mode: always`) |
| connection-service | Yes, unless you set `SPRING_SQL_INIT_MODE=never` | `${SPRING_SQL_INIT_MODE:always}` |
| group-service | No, never | Fixed (`mode: never`) |

Every `schema.sql` uses `CREATE TABLE IF NOT EXISTS`, so re-running against an
already-populated database is safe and does not drop data.

### Two tables are not created for you

Setting up a brand-new database needs two manual steps. Both are already done
on the team's shared Supabase database.

**1. The `students` table.** user-service reads and writes `students`, but its
`schema.sql` only creates `user_accounts` and `courses` — the student profile
DDL is missing from the bootstrap. On an empty database, user-service starts but
every profile operation fails. The column definitions are in
[`scripts/group-local-students.sql`](../scripts/group-local-students.sql), which
exists as a local test fixture; use it as the reference for the columns, but do
not run that file against the shared database — it also inserts fixture courses.

**2. The group-service tables.** Apply
[`group-service/src/main/resources/schema.sql`](group-service/src/main/resources/schema.sql)
once through the Supabase SQL Editor or a migration. It creates `study_groups`,
`study_group_members` and `group_join_requests` with row-level security and no
browser-role access, and it touches nothing else. Pushing the file to GitHub
does not apply it to Supabase.

## Demo population

The sample data seeder
([`DemoDataSeeder.java`](user-service/src/main/java/com/studybuddy/user/config/DemoDataSeeder.java))
is opt-in and disabled by default. Add to `backend/.env`:

```text
DEMO_DATA_ENABLED=true
SEED_ADMIN_EMAIL=admin@studybuds.sg
SEED_ADMIN_PASSWORD=<choose a private password of at least 8 characters>
SEED_STUDENT_PASSWORD=<choose a private password of at least 8 characters>
```

On the next user-service startup it inserts 10 courses, one system
administrator and 50 student profiles with matching preferences and
availability — the minimum the project brief asks for. The students are
`student01@smu.edu.sg` through `student50@smu.edu.sg` with fictional names (Jack
Tan, Amy Chew and so on), spread across Computing, Business, Accountancy,
Economics, Law and Social Sciences, all using `SEED_STUDENT_PASSWORD`. The
courses are IS442, IS212, MKTG101, FIN201, ACC201, ACC305, ECON101, ECON202,
COR-LAW and LAW201.

Re-running refreshes the demo students' sample profile fields and courses but
leaves existing passwords unchanged. These are demo credentials: choose
temporary values and never reuse them in a deployed environment.

## Running

Use JDK 21. Maven does not need to be installed — the repository ships the
wrapper.

The easiest options are the scripts described in the
[project README](../README.md#running-the-application):
`.\start-backend.ps1 -All` on Windows, or `python3 scripts/start-cloud.py` from
the repository root on macOS/Linux.

To run a service by hand, start each one in its own terminal with the same
`backend/.env` in place:

```powershell
cd backend
.\mvnw.cmd -pl user-service spring-boot:run
.\mvnw.cmd -pl api-gateway spring-boot:run
.\mvnw.cmd -pl matching-service spring-boot:run
.\mvnw.cmd -pl connection-service spring-boot:run
.\mvnw.cmd -pl group-service spring-boot:run
```

On macOS/Linux use `./mvnw` instead of `.\mvnw.cmd`.

The frontend's Vite server proxies `/api` to `http://localhost:8080` and `/ws`
to `http://localhost:8084`. Override them with `VITE_API_TARGET` and
`VITE_CHAT_WS_TARGET` if the services run elsewhere.

Every service exposes Actuator `health` and `info` endpoints, so
`http://localhost:8081/actuator/health` confirms user-service is up.

## API summary

All routes below are reached through the gateway on port 8080. Everything
except registration, login, health and course reads requires
`Authorization: Bearer <accessToken>`. Calling a service directly on its own
port still requires the same token — the services verify JWTs themselves rather
than trusting the gateway.

### Authentication — user-service

| Method | Path | Access |
|---|---|---|
| `POST` | `/api/auth/register` | Public (students only) |
| `POST` | `/api/auth/login` | Public — returns a bearer token and a safe user summary |
| `GET` | `/api/auth/me` | Authenticated |

### Students and courses — user-service

| Method | Path | Access |
|---|---|---|
| `GET` | `/api/students`, `/api/students/{userId}` | Authenticated (public profile; contact hidden) |
| `POST` | `/api/students`, `PUT /api/students/{userId}` | The student themselves |
| `GET` | `/api/students/{userId}/contact` | The student themselves, or an admin. A buddy's number comes from `/api/connections/{connectionId}/contact` instead. |
| `GET` | `/api/students/internal/{userId}/contact` | Service-to-service; requires the `X-Contact-Service-Secret` header matching `CONTACT_SERVICE_SECRET` |
| `GET` | `/api/courses`, `/api/courses/{courseCode}` | Public |
| `POST` | `/api/courses` | Admin |

### Administration — user-service

| Method | Path |
|---|---|
| `GET` | `/api/admin/accounts`, `/api/admin/accounts/{userId}` |
| `POST` | `/api/admin/accounts` |
| `PUT` | `/api/admin/accounts/{userId}`, `/api/admin/accounts/{userId}/status` |
| `DELETE` | `/api/admin/accounts/{userId}` |
| `GET` | `/api/admin/usage` |

All require an admin JWT.

### Matching — matching-service

| Method | Path | Access |
|---|---|---|
| `POST` | `/api/matching/students/{studentId}/search` | The student themselves, or an admin |
| `GET` | `/api/matching/config`, `/api/matching/config/strategies` | Admin |
| `PUT` | `/api/matching/config/strategy`, `/api/matching/config/criteria` | Admin |

A student can only search using their own user ID.

### Requests, connections and chat — connection-service

| Method | Path |
|---|---|
| `POST` | `/api/match-requests` |
| `GET` | `/api/match-requests/pending`, `/pending/count`, `/outgoing-pending`, `/{requestId}` |
| `POST` | `/api/match-requests/{requestId}/accept`, `/decline` |
| `DELETE` | `/api/match-requests/{requestId}` (cancel) |
| `GET` | `/api/connections`, `/api/connections/chats` |
| `GET` | `/api/connections/{connectionId}/contact`, `/messages` |
| `POST` | `/api/connections/{connectionId}/read` |
| `DELETE` | `/api/connections/{connectionId}` (end), `/{connectionId}/chat` (hide own copy) |
| STOMP | `/ws` → `/connections/{connectionId}/send` |

### Study groups — group-service

| Method | Path |
|---|---|
| `GET` | `/api/groups`, `/overview`, `/{id}`, `/{id}/details` |
| `POST` | `/api/groups`, `PUT /api/groups/{id}`, `POST /api/groups/{id}/close` |
| `DELETE` | `/api/groups/{id}/members/{memberId}` |
| `POST` | `/api/groups/{id}/join-requests` (request to join) |
| `DELETE` | `/api/groups/{id}/join-requests` (cancel own request) |
| `GET` | `/api/groups/{id}/join-requests` (leader), `/api/groups/join-requests/mine` |
| `POST` | `/api/groups/join-requests/{id}/accept`, `/reject` (leader) |

## Security notes

Passwords are BCrypt hashes. JWTs are stateless HS256 tokens with a default
60-minute lifetime (`JWT_TTL_MINUTES`). The frontend keeps the token in browser
local storage for the demo.

Contact information reaches a student only through an active study buddy
connection. connection-service checks the caller's JWT and their connection
membership before calling the internal contact endpoint in user-service, which
is itself guarded by `CONTACT_SERVICE_SECRET`.

Match searches are limited to the signed-in student's own profile unless the
caller is an administrator.

## Study Groups formatting and checks

Formatting is scoped to the Study Groups files listed in `group-service/pom.xml`
and `frontend/package.json`. Shared student/course models and teammates' pages
are deliberately outside that list, and the frontend configuration is passed
explicitly so editors do not apply it across the whole project.

```sh
cd backend
./mvnw -pl group-service spotless:check    # report differences
./mvnw -pl group-service spotless:apply    # fix them
```

On Windows use `mvnw.cmd`. For the frontend, from `frontend`:

```sh
npm ci
npm run format:groups:check
npm run format:groups:write
```

The `check` commands report differences without editing; `write`/`apply` fix
them. `npm run build` checks that the frontend still builds.

[Study Groups checks](../.github/workflows/study-groups.yml) runs on relevant
pull requests and pushes to `main`, and can be started manually in GitHub
Actions. It checks the scoped formatting, compiles the backend services and
builds the frontend. It needs no cloud database or repository secrets, and
formatter versions are pinned for consistent results.
