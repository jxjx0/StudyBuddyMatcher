# Connection service database setup

The service uses the same Supabase PostgreSQL connection settings as the other
backend services:

- `SUPABASE_JDBC_URL`
- `SUPABASE_DB_USER`
- `SUPABASE_DB_PASSWORD`

Set these on the backend service only. For local development, the defaults point
to `jdbc:postgresql://localhost:5432/studybuddy`.

On startup the service runs
[`src/main/resources/schema.sql`](./src/main/resources/schema.sql), which
creates the request and connection tables if they do not exist. Set
`SPRING_SQL_INIT_MODE=never` to skip this.

If `match_requests` was created before cancellation support was added, apply
[`migrations/002_allow_cancelled_match_requests.sql`](./migrations/002_allow_cancelled_match_requests.sql)
once in the Supabase SQL Editor to update its status constraint.
