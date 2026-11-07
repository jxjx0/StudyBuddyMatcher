# Study Buddy Matcher System

IS442 Object Oriented Programming — team project.

A web application that helps university students find compatible peers for
academic study and collaborative learning. Students build a study profile,
search for buddies ranked by a configurable compatibility score, send and manage
match requests, chat with their active buddies, and form study groups around a
course. A system administrator manages user accounts and tunes the matching
engine.

## Contents

- [Features](#features)
- [Architecture](#architecture)
- [Design principles](#design-principles)
- [Open source libraries](#open-source-libraries)
- [How the team divided the work](#how-the-team-divided-the-work)
- [How AI was used](#how-ai-was-used)
- [Getting started](#getting-started)
- [Running the application](#running-the-application)
- [Demo data](#demo-data)
- [Repository layout](#repository-layout)
- [Further documentation](#further-documentation)

## Features

### Students

**Profile and study preference management** — [`ProfilePage.vue`](frontend/src/pages/student/ProfilePage.vue), [`AvailabilityPage.vue`](frontend/src/pages/student/AvailabilityPage.vue)

- Create and update a profile with name, school, programme, year of study,
  contact number and current courses.
- Set study preferences: the course a buddy is needed for, preferred meeting
  mode (in-person, online or either), preferred group size (one-to-one, small
  group or either), and study goals (concept review, problem solving, exam
  preparation, project discussion).
- Declare weekly availability as one or more time slots.

**Study buddy matching** — [`DiscoverPage.vue`](frontend/src/pages/student/DiscoverPage.vue), [`PublicProfilePage.vue`](frontend/src/pages/student/PublicProfilePage.vue)

- Search for buddies by course or study goal, with additional filters for
  meeting mode and minimum timetable overlap.
- Receive a ranked list of candidates with an overall weighted match
  percentage.
- View another student's public study profile. Their contact number stays
  hidden until a connection is accepted.

**Match requests and connections** — [`ConnectionsPage.vue`](frontend/src/pages/student/ConnectionsPage.vue), [`MessagesPage.vue`](frontend/src/pages/student/MessagesPage.vue)

- Send a request with an optional message of up to 500 characters, and cancel a
  request that is still pending.
- Accept or decline incoming requests. Accepting reveals both students' contact
  numbers.
- View active connections and end a connection when it is no longer needed.
- Real-time chat with active buddies over an authenticated WebSocket/STOMP
  channel, with persisted history, unread counts and per-student chat deletion.
  Chat is an extra feature beyond the compulsory requirements.

### Study group leaders

[`GroupsPage.vue`](frontend/src/pages/student/GroupsPage.vue), [`GroupDetails.vue`](frontend/src/components/groups/GroupDetails.vue), [`GroupEditor.vue`](frontend/src/components/groups/GroupEditor.vue)

- Create a study group for a specific course with a name, description, study
  goals, preferred meeting mode, weekly availability and maximum size.
- Update group information, or close the group when it is no longer active.
- View join requests, accept or reject them, and remove a member.

Any student who creates a group becomes its leader, so the role is derived from
group ownership rather than stored on the account.

### System administrators

[`UsersPage.vue`](frontend/src/pages/admin/UsersPage.vue), [`MatchingPage.vue`](frontend/src/pages/admin/MatchingPage.vue), [`DashboardPage.vue`](frontend/src/pages/admin/DashboardPage.vue)

- Create, update and delete student and administrator accounts.
- View account status and basic usage information.
- Enable or disable each matching criterion (course, availability, study mode,
  study goal, group size) and set its relative weight.
- Choose the active matching strategy.

### Matching strategies

| Strategy | Behaviour |
|---|---|
| Balanced | Splits the weighting equally across every enabled criterion, replacing the configured weights. |
| Availability-first | Uses the administrator's weights but requires availability to carry the highest weight. |
| Course-first | Uses the administrator's weights but requires course to carry the highest weight. |

The scoring method and a worked example are documented in
[backend/matching-service/README.md](backend/matching-service/README.md).

## Architecture

The system is a Vue 3 single-page application talking to five Spring Boot
services behind an API gateway, over a shared PostgreSQL database hosted on
Supabase.

```
                 ┌──────────────────────────┐
                 │  Vue 3 SPA (Vite, 5173)  │
                 └────────────┬─────────────┘
                     /api     │     /ws
                 ┌────────────▼─────────────┐
                 │   api-gateway  (8080)    │  verifies JWT, routes requests
                 └────────────┬─────────────┘
          ┌───────────────┬───┴────────┬────────────────┐
          ▼               ▼            ▼                ▼
   ┌─────────────┐ ┌─────────────┐ ┌──────────────┐ ┌─────────────┐
   │user-service │ │  matching-  │ │ connection-  │ │   group-    │
   │   (8081)    │ │   service   │ │   service    │ │   service   │
   │             │ │   (8083)    │ │   (8084)     │ │   (8085)    │
   │ accounts    │ │ scoring &   │ │ requests,    │ │ groups,     │
   │ profiles    │ │ ranking     │ │ connections, │ │ membership, │
   │ courses     │ │ config      │ │ chat         │ │ join reqs   │
   └──────┬──────┘ └──────┬──────┘ └──────┬───────┘ └──────┬──────┘
          │               │               │                │
          └───────────────┴───────┬───────┴────────────────┘
                                  ▼
                Supabase PostgreSQL (session pooler, 5432)
```

`user-service` is the single owner of account, profile and course data. The
other three services hold their own tables and call `user-service` over HTTP
through a `StudentClient` when they need profile information, rather than
reading its tables directly.

### Gateway routes

| Path | Service |
|---|---|
| `/api/auth/**`, `/api/users/**`, `/api/students/**`, `/api/courses/**`, `/api/admin/**` | user-service (8081) |
| `/api/matching/**` | matching-service (8083) |
| `/api/match-requests/**`, `/api/connections/**` | connection-service (8084) |
| `/api/groups/**` | group-service (8085) |
| `/ws` | connection-service (8084), WebSocket upgrade |

Routes are declared in
[`api-gateway/src/main/resources/application.yml`](backend/api-gateway/src/main/resources/application.yml),
not hard-coded in Java, so a service can be repointed without a rebuild.

## Design principles

**Layering.** Every service follows the same package structure, so a class's
responsibility is visible from its location:

| Package | Responsibility |
|---|---|
| `controller` | HTTP and STOMP endpoints; request/response mapping and authorization checks only |
| `service` | Business rules and orchestration |
| `repository` | JDBC persistence |
| `model` | Domain entities and enums |
| `dto` | Data carried across a service boundary |
| `client` | Outbound calls to another service |
| `config` | Security, WebSocket and seeding configuration |

**Single Responsibility Principle.** Each service owns one area of the domain,
and within a service each class owns one job. For example, in matching-service
[`MatchingEngine`](backend/matching-service/src/main/java/com/studybuddy/matching/engine/MatchingEngine.java)
only scores and ranks candidates,
[`MatchingConfigurationService`](backend/matching-service/src/main/java/com/studybuddy/matching/service/MatchingConfigurationService.java)
only loads and saves the active configuration, and
[`StudentClient`](backend/matching-service/src/main/java/com/studybuddy/matching/client/StudentClient.java)
only fetches profiles.

**Strategy pattern.** The three matching strategies implement a shared
[`MatchingStrategy`](backend/matching-service/src/main/java/com/studybuddy/matching/engine/MatchingStrategy.java)
interface that decides how criteria are weighted before scoring runs. The engine
depends on the interface, so a new strategy can be added without changing it.

**Inheritance and polymorphism.** `Student` and `SystemAdministrator` extend an
abstract
[`User`](backend/user-service/src/main/java/com/studybuddy/user/model/User.java)
base class, which carries the shared identity and credential fields while each
subclass adds its own behaviour.

**Externalized configuration.** No credential, port, URL or secret is compiled
into the code. Every one is read from an environment variable with a sensible
local default, through Spring's `application.yml`
(`${SUPABASE_JDBC_URL:...}`, `${JWT_SECRET:}`, `${CONNECTION_SERVICE_URL:...}`
and so on). Real values live in a git-ignored `backend/.env`.

**Least privilege for sensitive data.** Contact numbers are served from a
separate internal endpoint in user-service that is protected by a shared secret
(`CONTACT_SERVICE_SECRET`), a header the browser never holds.
connection-service checks the caller's JWT and their membership of an active
connection before requesting it.

## Open source libraries

### Backend

| Library | Used for | Why |
|---|---|---|
| Spring Boot 4.1.1 | Application framework for all five services | Recommended in the project brief; dependency injection and auto-configuration keep the layered structure clean |
| Spring Cloud Gateway (MVC) 2025.1.3 | API gateway routing | Declarative route configuration, so routes stay in YAML rather than Java |
| Spring Security + OAuth2 Resource Server | JWT verification in every service | Standard JWT validation rather than hand-rolled token parsing |
| Spring JDBC (`spring-boot-starter-jdbc`) | Database access | Chosen over JPA/Hibernate so the SQL is explicit and reviewable, which suits a small, well-known schema |
| PostgreSQL JDBC driver | Supabase connectivity | The database is PostgreSQL |
| Spring WebSocket + STOMP | Real-time chat | Built into the Spring stack, so chat reuses the same JWT authentication as the REST API |
| Spring RestClient (`spring-boot-starter-restclient`) | Service-to-service HTTP calls | The synchronous client in the Spring stack; each `StudentClient` uses it to read profiles from user-service |
| Spring Boot Validation | Request payload validation | Declarative `@Valid` constraints keep validation out of controller bodies |
| Spring Boot Actuator | `health` and `info` endpoints | Lets the start-up scripts confirm a service is up |
| Spotless (Maven plugin) | Java formatting for group-service | Pinned formatter version gives consistent results across machines |

### Frontend

| Library | Used for | Why |
|---|---|---|
| Vue 3 | UI framework | Composition API suits the shared state in `src/composables/` |
| Vue Router 4 | Routing and role-based route guards | Official router; `meta.role` guards keep student and admin areas separate |
| Vite 8 | Dev server and build | Fast hot reload, and its dev proxy removes the need for CORS configuration in development |
| `@vitejs/plugin-vue` | Vite plugin for single-file components | Official Vue plugin; compiles `.vue` files in the Vite build |
| `@stomp/stompjs` | STOMP client for chat | Matches the Spring STOMP broker on the backend |
| `lucide-vue-next` | Icons | Consistent icon set with a Vue 3 build |
| Prettier | Frontend formatting | Scoped to the Study Groups files via `prettier.groups.json` |

## How the team divided the work

Work was split by domain class, so each member owned the model, service and
repository code for their area end to end.

| Member | Area |
|---|---|
| Shannon | `User`, `SystemAdministrator`, login and authentication (Spring Security / JWT), Supabase |
| Aaralyn | Frontend setup, `Student`, `Course` |
| Jessica | `MatchRequest`, `StudyBuddyConnection`, `TimeSlot` |
| Lwin | `StudyGroup`, `GroupJoinRequest` |
| Jing Xiang & Henry | The matching engine, built collaboratively: `MatchingEngine`, `MatchingConfig`, `MatchingCriterion`, `SearchCriteria`, `MatchResult`, and `MatchingStrategy` with its three implementations. Jing Xiang also did the backend setup. |

## How AI was used

AI assistance was used in the following ways, and every generated change was
reviewed by a team member before it was kept:

- Building the baseline for the backend.
- Building the design and baseline for the frontend.
- Manually reviewing AI-generated code to confirm the implementation matches
  the requirements.
- Optimising existing code.

## Getting started

### Prerequisites

The team works across Windows and macOS, and everything below runs on
Windows, macOS and Linux unless a section says otherwise.

- **JDK 21** (Maven itself is not needed; the repository ships the Maven
  wrapper, `mvnw` / `mvnw.cmd`)
- **Node.js** 20.19+ or 22.12+ (required by Vite 8 and `@vitejs/plugin-vue`)
- **Python 3.9+**, only if you use the `scripts/` runners
- A **Supabase** PostgreSQL project, or a local PostgreSQL database

Which command form to use:

| | Windows (PowerShell) | macOS / Linux |
|---|---|---|
| Maven wrapper | `.\mvnw.cmd` | `./mvnw` |
| Start everything | `backend\start-backend.ps1 -All` | `python3 scripts/start-cloud.py` |
| Stop everything | `backend\stop-backend.ps1` | Ctrl-C in the runner |
| Python | `python` | `python3` |

On macOS and Linux, if the wrapper will not run after cloning, restore its
executable bit once with `chmod +x backend/mvnw`.

### 1. Configure the backend

Copy [`backend/.env.example`](backend/.env.example) to `backend/.env` and fill
in your values. The file is git-ignored; never commit real credentials.
[backend/README.md](backend/README.md) explains every variable, where to find
the Supabase values, and which services create their own tables.

You will also need `JWT_SECRET` and `CONTACT_SERVICE_SECRET`. If you are
joining an existing setup, get the team's current values from a teammate rather
than generating your own — they must match across machines. See
[The two secrets](backend/README.md#the-two-secrets).

### 2. Install frontend dependencies

```bash
cd frontend
npm install
```

The project also has a `pnpm-lock.yaml`, so `pnpm install` works if you have
pnpm set up. `npm install` is the simplest option if you don't.

## Running the application

### Windows (PowerShell)

From the `backend` folder:

```powershell
cd backend
.\start-backend.ps1 -All
```

`-All` starts all five services. **Use it** unless you only need matching:
without it the script starts just user-service, api-gateway and
matching-service, which leaves connection requests, Messages/chat and study
groups non-functional.

The script builds the project once, then opens each service in its own tab of a
Windows Terminal window named "backend" (or in separate PowerShell windows if
Windows Terminal isn't installed). Each tab shows that service's logs. The first
start downloads Maven and the dependencies, so it takes a few minutes.

| Service | Port |
|---|---|
| api-gateway | 8080 |
| user-service | 8081 |
| matching-service | 8083 |
| connection-service | 8084 |
| group-service | 8085 |

Then start the frontend in a separate terminal:

```bash
cd frontend
npm run dev
```

Vite serves the app at [http://localhost:5173](http://localhost:5173) and
proxies `/api` to the gateway on port 8080 and `/ws` to connection-service on
port 8084. Start the backend first.

If PowerShell says running scripts is disabled:

```powershell
powershell -ExecutionPolicy Bypass -File .\start-backend.ps1 -All
```

> Open the `.ps1` file from a PowerShell prompt — double-clicking it in File
> Explorer just opens it in Notepad.

**Stopping:** run `.\stop-backend.ps1` from the `backend` folder. It stops every
service listening on the backend ports and prints which ones it stopped. You can
then close the "backend" Terminal window. To stop a single service instead,
press **Ctrl+C** in its tab. Run `stop-backend.ps1` if a start fails with
`Port 8081 was already in use` — that means a service from an earlier run is
still going.

### macOS / Linux

From the repository root:

```bash
python3 scripts/start-cloud.py
```

This starts the frontend at `http://127.0.0.1:5173` and all five backend
services against the database configured in `backend/.env`. It forces demo
seeding and automatic schema initialization off, so it neither seeds data nor
creates tables — sign in with an account that already exists. Ctrl-C stops the
local processes and leaves cloud data untouched.

Two things to know before you run it:

- It requires a **cloud Supabase** URL. The script rejects any
  `SUPABASE_JDBC_URL` whose host is not `*.supabase.co` or `*.supabase.com`,
  so it cannot be pointed at a local PostgreSQL database.
- It does **not** run on Windows; it exits and tells you to use
  `start-backend.ps1 -All` instead.

If you would rather start the services yourself on macOS or Linux, open five
terminals in `backend/` and run one service in each:

```sh
./mvnw -pl user-service spring-boot:run
./mvnw -pl api-gateway spring-boot:run
./mvnw -pl matching-service spring-boot:run
./mvnw -pl connection-service spring-boot:run
./mvnw -pl group-service spring-boot:run
```

Start `user-service` first — the others call it. Then run `npm run dev` in
`frontend/` as on Windows, and stop each service with Ctrl-C in its terminal.

### Running a single service

```powershell
cd backend
.\mvnw.cmd -pl user-service spring-boot:run
```

Replace `user-service` with the service you want. On macOS/Linux use `./mvnw`.

### Other frontend commands

```bash
npm run build    # production build, outputs to frontend/dist
npm run start    # preview the production build locally
```

## Demo data

The project brief requires at least 10 courses and 50 student profiles. The
seeder in
[`DemoDataSeeder.java`](backend/user-service/src/main/java/com/studybuddy/user/config/DemoDataSeeder.java)
inserts exactly that — 10 courses, 50 students with generated preferences and
availability, and one administrator — but it is **opt-in** and off by default.

Turn it on in `backend/.env`:

```text
DEMO_DATA_ENABLED=true
SEED_ADMIN_EMAIL=admin@studybuds.sg
SEED_ADMIN_PASSWORD=<at least 8 characters>
SEED_STUDENT_PASSWORD=<at least 8 characters>
```

Students are `student01@smu.edu.sg` through `student50@smu.edu.sg`, all using
`SEED_STUDENT_PASSWORD`, spread across Computing, Business, Accountancy,
Economics, Law and Social Sciences. Full details are in
[backend/README.md](backend/README.md#demo-population).

`python3 scripts/group-local.py demo` is a separate, disposable environment for
study-group testing. Its Alice/Bob/Carol accounts live only in a temporary local
PostgreSQL database and are removed when the runner stops.

**macOS / Linux only.** This runner uses POSIX process groups and needs a local
PostgreSQL 17 installation; set `PG_BIN` to the directory holding `initdb`,
`pg_ctl` and `psql` if it is not found automatically. It does not run on
Windows — on Windows use `start-backend.ps1 -All` with the shared database
instead. It also seeds only two courses and three accounts, so it is a test
harness, not the demo described above.

## Repository layout

```
StudyBuddyMatcher/
├── backend/
│   ├── api-gateway/          routing and JWT verification
│   ├── user-service/         accounts, profiles, courses, auth, demo seeder
│   ├── matching-service/     scoring engine, strategies, admin configuration
│   ├── connection-service/   match requests, connections, real-time chat
│   ├── group-service/        study groups, membership, join requests
│   ├── .env.example          template for the git-ignored backend/.env
│   ├── start-backend.ps1     starts the services (Windows)
│   └── stop-backend.ps1      stops them again
├── frontend/
│   └── src/
│       ├── pages/student/    Discover, Connections, Messages, Groups, Profile…
│       ├── pages/admin/      Dashboard, Users, Matching configuration
│       ├── composables/      shared reactive state (auth, matching, chat…)
│       ├── components/       dialogs, navigation, group editor
│       └── services/api.js   single place where HTTP calls are made
└── scripts/                  Python runners for cloud and local demos
```

## Further documentation

| Document | Covers |
|---|---|
| [backend/README.md](backend/README.md) | Supabase setup, every environment variable, schema initialization, full API summary |
| [backend/api-gateway/README.md](backend/api-gateway/README.md) | Routing table, JWT verification, authorization rules |
| [backend/user-service/README.md](backend/user-service/README.md) | Account model and inheritance, JWT claims, profile and admin APIs, seeding |
| [backend/matching-service/README.md](backend/matching-service/README.md) | How the match percentage is calculated, criteria, strategies, the engine's classes |
| [backend/connection-service/README.md](backend/connection-service/README.md) | Match requests, connections, chat database and WebSocket configuration |
| [backend/group-service/README.md](backend/group-service/README.md) | The leader role, group business rules, the schema and its row-level security |
| [backend/connection-service/CHAT_README.md](backend/connection-service/CHAT_README.md) | Class-by-class walkthrough of the chat feature with flow diagrams |
| [CHANGES_README.md](CHANGES_README.md) | Log of connection-request and study-group changes |
