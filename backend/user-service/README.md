# User service

Owns accounts, student profiles and courses, and issues the JWTs that every
other service verifies. Runs on port `8081`. It is the only service that writes
to `user_accounts`, `students` and `courses`; the others ask it over HTTP.

For the system overview see the [project README](../../README.md); for shared
configuration see the [backend README](../README.md).

## Domain model

`Student` and `SystemAdministrator` both extend the abstract
[`User`](src/main/java/com/studybuddy/user/model/User.java), which holds the
identity and credential fields common to every account — user ID, email,
password hash and account status — while each subclass adds what only it needs.
`User` also normalizes email to lowercase on assignment, so the uniqueness
constraint cannot be sidestepped by changing case.

| Class | Adds |
|---|---|
| [`Student`](src/main/java/com/studybuddy/user/model/Student.java) | Name, school, programme, year of study, contact number, current courses, courses needing a buddy, weekly availability, meeting mode, group size preference, study goals |
| [`SystemAdministrator`](src/main/java/com/studybuddy/user/model/SystemAdministrator.java) | Administrator name |

Supporting enums: `AccountStatus` (`ACTIVE`, `SUSPENDED`, `DELETED`),
`MeetingMode`, `GroupSizePreference`, `StudyGoal`, and `TimeSlot` for
availability.

## Layers

| Package | Classes |
|---|---|
| `controller` | `AuthController`, `StudentController`, `CourseController`, `AdminController`, `ApiExceptionHandler` |
| `service` | `AuthService`, `JwtService`, `StudentService`, `CourseService`, `UserAccountService` |
| `repository` | `UserRepository`, `StudentRepository`, `CourseRepository` |
| `config` | `SecurityConfiguration`, `DemoDataSeeder` |

`ApiExceptionHandler` translates domain exceptions into HTTP status codes in
one place, so controllers stay free of error-mapping code.

## Authentication

[`JwtService`](src/main/java/com/studybuddy/user/service/JwtService.java) issues
stateless HS256 tokens signed with the shared `JWT_SECRET`:

| Claim | Value |
|---|---|
| `iss` | `studybuddy-user-service` |
| `sub` | The account's email |
| `uid` | The account's user ID — what the other services authorize against |
| `roles` | `["ROLE_STUDENT"]` or `["ROLE_ADMIN"]`, decided by the runtime type of the `User` |
| `exp` | Issue time plus `JWT_TTL_MINUTES` (default 60) |

Passwords are stored as BCrypt hashes and never leave the service — the
account DTOs omit the hash field. The frontend keeps the token in browser local
storage for the demo.

## API

### Authentication

| Method | Path | Access |
|---|---|---|
| `POST` | `/api/auth/register` | Public; creates a student account only |
| `POST` | `/api/auth/login` | Public; returns the bearer token and a safe user summary |
| `GET` | `/api/auth/me` | Authenticated |

### Student profiles

| Method | Path | Access |
|---|---|---|
| `GET` | `/api/students` | Authenticated |
| `GET` | `/api/students/{userId}` | Authenticated; public profile, contact number omitted |
| `POST` | `/api/students` | The student themselves |
| `PUT` | `/api/students/{userId}` | The student themselves |
| `GET` | `/api/students/{userId}/contact` | The student themselves, or an admin |
| `GET` | `/api/students/internal/{userId}/contact` | Service-to-service only |

The internal endpoint is how a student eventually sees their buddy's number. It
requires the `X-Contact-Service-Secret` header to match
`CONTACT_SERVICE_SECRET`, compared with `MessageDigest.isEqual` to avoid a
timing side channel. The gateway does forward this path, since it falls under
`/api/students/**`, so the header is what actually protects it — not
unreachability. Only connection-service calls it, and only after confirming the
caller belongs to an active connection.

### Courses

| Method | Path | Access |
|---|---|---|
| `GET` | `/api/courses`, `/api/courses/{courseCode}` | Public |
| `POST` | `/api/courses` | Admin |

### Administration

| Method | Path |
|---|---|
| `GET` | `/api/admin/accounts`, `/api/admin/accounts/{userId}` |
| `POST` | `/api/admin/accounts` — role must be `STUDENT` or `ADMIN` |
| `PUT` | `/api/admin/accounts/{userId}` |
| `GET` | `/api/admin/accounts/{userId}/status` |
| `PUT` | `/api/admin/accounts/{userId}/status` |
| `DELETE` | `/api/admin/accounts/{userId}` |
| `GET` | `/api/admin/usage` |

All require an admin JWT, enforced both at the gateway and here.

## Database

| Table | Created by `schema.sql`? |
|---|---|
| `user_accounts` | Yes |
| `courses` | Yes |
| `students` | **No — see below** |

This service runs [`schema.sql`](src/main/resources/schema.sql) on **every**
startup; the mode is fixed at `always` and `SPRING_SQL_INIT_MODE` does not
change it. Both statements use `CREATE TABLE IF NOT EXISTS`, so this is safe
against a populated database.

The student profile DDL is missing from that bootstrap, so a brand-new database
needs the `students` table created by hand before profiles will work. See
[Two tables are not created for you](../README.md#two-tables-are-not-created-for-you).

Student profile fields are also mirrored into `user_accounts.profile_data` as
JSONB, so the admin account screens can show profile information without
joining.

## Demo data

[`DemoDataSeeder`](src/main/java/com/studybuddy/user/config/DemoDataSeeder.java)
is a `CommandLineRunner` that inserts 10 courses, 50 students and one
administrator — the minimum the project brief requires. It is disabled unless
`DEMO_DATA_ENABLED=true`. Full details, including the generated emails and
passwords, are in [Demo population](../README.md#demo-population).

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `USER_SERVICE_PORT` | `8081` | Listen port |
| `JWT_SECRET` | — | **Required**, ≥32 characters |
| `CONTACT_SERVICE_SECRET` | — | **Required**, ≥32 characters, different from `JWT_SECRET` |
| `JWT_TTL_MINUTES` | `60` | Token lifetime |
| `SUPABASE_JDBC_URL`, `SUPABASE_DB_USER`, `SUPABASE_DB_PASSWORD` | local PostgreSQL | Database connection |
| `DEMO_DATA_ENABLED`, `SEED_ADMIN_EMAIL`, `SEED_ADMIN_PASSWORD`, `SEED_STUDENT_PASSWORD` | off | Demo seeding |

## Run

From `backend`:

```powershell
.\mvnw.cmd -pl user-service spring-boot:run
```

On macOS/Linux use `./mvnw`. Start this service before the others — they all
depend on it for profile data.
