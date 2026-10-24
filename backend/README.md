# Study Buddy Matcher backend

The Vue app calls the API gateway on port `8080`. The gateway verifies JWTs and routes requests to the five Spring Boot services. The `user-service` owns accounts and course data in the project's Supabase PostgreSQL database.

## Supabase setup

1. In the Supabase dashboard, open **Connect** and choose **Transaction pooler**. Copy its host, port (`6543`), database, username and database password.
2. Copy [`.env.example`](.env.example) to `backend/.env`, then fill in your values. Keep `sslmode=require&prepareThreshold=0` in the JDBC URL: transaction pooling requires server-side prepared statements to be disabled. The `.env` file is ignored by Git.
3. Keep `DEMO_DATA_ENABLED=false` and `SPRING_SQL_INIT_MODE=never` when using the shared cloud database. Use its existing accounts and tables.
4. For study groups, apply [group-service/src/main/resources/schema.sql](group-service/src/main/resources/schema.sql) once through the Supabase SQL Editor or a database migration. It adds `study_groups`, `study_group_members` and `group_join_requests` with row-level security and no browser-role access. It does not change the existing user, course or matching tables. Pushing the SQL file to GitHub does not apply it to Supabase.

The file should contain these database and signing values:

```text
SUPABASE_JDBC_URL=jdbc:postgresql://<pooler-host>:6543/postgres?sslmode=require&prepareThreshold=0
SUPABASE_DB_USER=<username from Supabase Connect>
SUPABASE_DB_PASSWORD=<database password>
JWT_SECRET=<at least 32 random characters; use the same value for gateway and user-service>
CONTACT_SERVICE_SECRET=<a different random secret of at least 32 characters; use the same value for user-service and connection-service>
DB_POOL_SIZE=2
DB_MIN_IDLE=0
DEMO_DATA_ENABLED=false
SPRING_SQL_INIT_MODE=never
```

Use the exact host, username and database shown in Supabase **Connect**. Pooler usernames include the project reference. Each database service allows two local connections by default and releases idle connections after roughly a minute. Transaction pooling shares database connections between transactions, avoiding one reserved session for every idle application connection. Do not stop teammates' sessions to free the session pool.

See [Supabase connection modes](https://supabase.com/docs/guides/database/connecting-to-postgres) and [JDBC prepared-statement configuration](https://supabase.com/docs/guides/troubleshooting/disabling-prepared-statements-qL8lEL). Do not commit real credentials or put them in Vue/Vite variables.

This backend uses the PostgreSQL connection directly, so it does **not** need the Supabase project URL, anon/publishable key, or service-role key. The database password is the credential used by the backend. Never put the database password or service-role key in the frontend.

`SUPABASE_JDBC_URL`, `SUPABASE_DB_USER`, and `SUPABASE_DB_PASSWORD` have local-only fallback values for a local PostgreSQL database. For Supabase, replace them with the values above. `JWT_SECRET` has no insecure fallback; the gateway, user-service, matching-service, and connection-service fail to start if it is missing or shorter than 32 characters. `CONTACT_SERVICE_SECRET` must be a separate random secret shared only by user-service and connection-service; both services fail to start if it is missing or shorter than 32 characters. It protects the internal contact lookup and must never be sent to the browser.

## Demo population

The assignment's sample data seeder is opt-in. Add these values to `backend/.env`:

```text
DEMO_DATA_ENABLED=true
SEED_ADMIN_EMAIL=admin@studybuds.sg
SEED_ADMIN_PASSWORD=<choose a private password of at least 8 characters>
SEED_STUDENT_PASSWORD=<choose a private password of at least 8 characters>
```

On startup it inserts 10 courses, one system administrator, and 50 student profiles with matching preferences and availability. The sample students use `student01@smu.edu.sg` through `student50@smu.edu.sg`, have fictional names (for example, Jack Tan and Amy Chew), and are spread across Computing, Business, Accountancy, Economics, Law, and Social Sciences; all use `SEED_STUDENT_PASSWORD`. The admin uses `SEED_ADMIN_EMAIL` and `SEED_ADMIN_PASSWORD`. When enabled, the seeder refreshes the demo students' sample profile fields and courses on startup; it leaves existing passwords unchanged. Seeding is disabled by default. These are demo credentials: choose temporary values and do not use them in a deployed environment.

## Run

On macOS/Linux, run `python3 scripts/start-cloud.py` from the repository root to start the frontend and all five services against the configured cloud database. It forces seeding and automatic schema initialization off. Ctrl-C stops its local processes without removing cloud records.

Use JDK 21. Set the environment variables above, then start the user service and gateway (and the other services when needed) from the Spring Boot Dashboard, or run:

```powershell
cd backend
.\mvnw.cmd -pl user-service spring-boot:run
```

Start the gateway in another terminal with the same `JWT_SECRET`:

```powershell
cd backend
.\mvnw.cmd -pl api-gateway spring-boot:run
```

The frontend's Vite server proxies `/api` to `http://localhost:8080`. Override it with `VITE_API_TARGET` if the gateway runs elsewhere.

The admin matching configuration and matching API also require `matching-service` to be running. It uses the same Supabase database settings in `backend/.env` to persist the matching strategy and criteria. Like the gateway, user-service and connection-service, matching-service validates JWTs with the shared `JWT_SECRET`; direct requests to its port must include a bearer token. Match searches are limited to the signed-in student's own profile unless the caller is an administrator.

## Authentication API

- `POST /api/auth/login` accepts `{ "email": "...", "password": "..." }` and returns a bearer token and safe user summary.
- Pass the token as `Authorization: Bearer <accessToken>` to protected API calls.
- Students can register through `POST /api/auth/register`. Admin account management (`/api/admin/**`) and matching configuration (`/api/matching/config/**`) require an admin JWT. Public gateway routes include registration, login, health and course reads; protected routes require a valid JWT.

Passwords are BCrypt hashes in the database. JWTs are stateless HS256 tokens with a default lifetime of 60 minutes (`JWT_TTL_MINUTES` changes it). The frontend login uses the same API and retains the token in browser local storage for the demo.

## Database notes

The shared cloud database stores accounts in `user_accounts`, student profiles in `students` (mirrored to `user_accounts.profile_data` for account administration), and course references in `courses`. Matching settings use `matching_configuration` and `matching_criteria`. Study groups, membership and join requests use the three group-service tables. The Java backend accesses these tables through JDBC and checks group permissions using its own authenticated user tokens.

Contact information is returned to a student only through an active study buddy connection. The connection-service checks the caller's JWT and connection membership before using the internal contact endpoint in user-service. Configure `CONTACT_SERVICE_SECRET` as described above; do not expose it to the frontend.

The group schema is applied explicitly. For an already configured cloud database, leave automatic schema initialization and demo seeding disabled. The separate `scripts/group-local.py` runner creates its own disposable database for tests and local demos; it does not populate or update cloud Supabase.

## Study Groups formatting and checks

Formatting is limited to the Study Groups files listed in `group-service/pom.xml` and `frontend/package.json`. Shared student/course models and teammates' pages are outside that list. The frontend configuration is passed explicitly so editors do not apply it across the whole project.

From the repository root, check or apply Java formatting:

```sh
cd backend
./mvnw -pl group-service spotless:check
./mvnw -pl group-service spotless:apply
```

On Windows, use `mvnw.cmd` instead of `./mvnw`. For frontend formatting, run these in `frontend`:

```sh
npm ci
npm run format:groups:check
npm run format:groups:write
```

The `write`/`apply` commands format the selected files. The `check` commands report differences without editing them. Run `npm run build` in `frontend` to check the frontend.

[Study Groups checks](../.github/workflows/study-groups.yml) runs on relevant pull requests and pushes to `main`, and can also be started manually in GitHub Actions. It checks the scoped formatting, compiles the backend services, and builds the frontend. No cloud database or repository secrets are needed. Formatter versions are pinned for consistent results.
