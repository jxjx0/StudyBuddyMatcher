# Connection service database setup

The service uses the same Supabase PostgreSQL connection settings as the other
backend services:

- `SUPABASE_JDBC_URL`
- `SUPABASE_DB_USER`
- `SUPABASE_DB_PASSWORD`

Set these on the backend service only. For local development, the defaults point
to `jdbc:postgresql://localhost:5432/studybuddy`.

Before starting the updated service against Supabase, apply
[`migrations/001_create_connection_tables.sql`](./migrations/001_create_connection_tables.sql)
in the Supabase SQL Editor using a database-owner connection. If
`match_requests` existed before cancellation support was added, also apply
[`migrations/002_allow_cancelled_match_requests.sql`](./migrations/002_allow_cancelled_match_requests.sql)
to update its status constraint. The migrations create the request and
connection tables, enable row-level security, and keep the tables inaccessible
to browser roles. The application accesses them through its server-side JDBC
connection.
