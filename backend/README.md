# Study Buddy Matcher backend

The Vue app calls the API gateway on port `8080`. The gateway verifies JWTs and routes requests to the five Spring Boot services. The `user-service` owns accounts and course data in the project's Supabase PostgreSQL database.

## Supabase setup

1. In the Supabase dashboard, open **Connect** and choose **Session pooler** (recommended for local IPv4 networks) or a connection option that works on your network.
2. Copy the host, port, database, username, and database password from the supplied connection string. The project schema starts automatically when `user-service` connects; no tables need to be created by hand.
3. Copy [`.env.example`](.env.example) to `backend/.env`, then fill in your values. The `.env` file is ignored by Git. Both `user-service` and `api-gateway` search for it when started from the repository root, `backend`, or their own service directory.

The file should contain these database and signing values:

```text
SUPABASE_JDBC_URL=jdbc:postgresql://<host>:<port>/<database>?sslmode=require
SUPABASE_DB_USER=<username from Supabase Connect>
SUPABASE_DB_PASSWORD=<database password>
JWT_SECRET=<at least 32 random characters; use the same value for gateway and user-service>
```

Example format (replace each placeholder with a real value):

```text
SUPABASE_JDBC_URL=jdbc:postgresql://<host>:<port>/<database>?sslmode=require
SUPABASE_DB_USER=<username from Supabase Connect>
SUPABASE_DB_PASSWORD=<database password>
JWT_SECRET=<same random secret, at least 32 characters>
```

Use the exact host, port, username, and database shown in Supabase **Connect**. Session pooler usernames often include the project reference. Do not commit real credentials or put them in Vue/Vite variables.

This backend uses the PostgreSQL connection directly, so it does **not** need the Supabase project URL, anon/publishable key, or service-role key. The database password is the credential used by the backend. Never put the database password or service-role key in the frontend.

`SUPABASE_JDBC_URL`, `SUPABASE_DB_USER`, and `SUPABASE_DB_PASSWORD` have local-only fallback values for a local PostgreSQL database. For Supabase, replace them with the values above. `JWT_SECRET` has no insecure fallback; both apps fail to start if it is missing or shorter than 32 characters.

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

The admin matching configuration and matching API also require `matching-service` to be running. It uses the same Supabase database settings in `backend/.env` to persist the matching strategy and criteria.

## Authentication API

- `POST /api/auth/login` accepts `{ "email": "...", "password": "..." }` and returns a bearer token and safe user summary.
- Pass the token as `Authorization: Bearer <accessToken>` to protected API calls.
- Accounts are created through the admin account tools or by enabling the demo seeder; there is no public student registration route. Admin account management (`/api/admin/**`) and matching configuration (`/api/matching/config/**`) require an admin JWT. All other API routes require a valid JWT, except login, health, and course reads.

Passwords are BCrypt hashes in the database. JWTs are stateless HS256 tokens with a default lifetime of 60 minutes (`JWT_TTL_MINUTES` changes it). The frontend login uses the same API and retains the token in browser local storage for the demo.

## Database notes

`user-service/src/main/resources/schema.sql` creates `user_accounts` and `courses`. Student profile fields are kept in PostgreSQL `JSONB`, while email, password hash, role, account status, and ID are separate account columns. DDL uses `CREATE TABLE IF NOT EXISTS`, so starting the service again is safe. The matching, group, and connection services still have their own persistence stubs; this Supabase setup currently covers user-service accounts, student profiles, and courses.
