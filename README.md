# StudyBuddyMatcher

## Frontend

The frontend lives in [frontend/](frontend/) and is a Vue 3 + Vite app.

### Prerequisites

- [Node.js](https://nodejs.org/) 22.22.2+, 24.15.0+, or 26+ (including UI tests)

### Setup

```bash
cd frontend
npm install
```

> The project also has a `pnpm-lock.yaml`, so `pnpm install` works too if you have pnpm set up. `npm install` is the simplest option if you don't.

### Run the dev server

```bash
cd frontend
npm run dev
```

This starts Vite's dev server (default: [http://localhost:5173](http://localhost:5173)) with hot reload.

### Other commands

```bash
npm run build    # build for production (outputs to frontend/dist)
npm run start    # preview the production build locally
npm test         # utility and study-group UI regression tests
```

## Backend

The backend lives in [backend/](backend/) and is made up of Spring Boot services that share a Supabase PostgreSQL database. Supabase configuration, JWT secrets, and demo account/course seeding are documented in [backend/README.md](backend/README.md).

### Prerequisites

- JDK 21
- A `backend/.env` file with your Supabase and JWT values (copy [backend/.env.example](backend/.env.example) and fill it in — see [backend/README.md](backend/README.md))

Maven does not need to be installed; the scripts use the bundled Maven wrapper (`mvnw.cmd`). The first start downloads Maven and the dependencies, so it takes a few minutes.

### Run locally with cloud Supabase (macOS / Linux)

Use Python 3.9 or newer. Install the frontend dependencies, then configure `backend/.env` using [backend/.env.example](backend/.env.example). The database must already have the project tables; see [the backend setup](backend/README.md).

From the repository root:

```bash
python3 scripts/start-cloud.py
```

This starts the frontend at `http://127.0.0.1:5173` and all five backend services using your cloud database. Sign in with an existing cloud account. It does not seed data or create tables. Ctrl-C stops the local processes and keeps cloud data.

`python3 scripts/group-local.py demo` is a separate, disposable test environment. Its Alice/Bob/Carol accounts and groups exist only in temporary local PostgreSQL and are removed when that runner stops.

### Start the backend (Windows, PowerShell)

From the `backend` folder:

```powershell
cd backend
.\start-backend.ps1 -All   # all services, including study groups
```

Study groups requires `-All`. Running the script without it starts only user-service, api-gateway and matching-service.

The script builds the project once, then opens each service in its own tab of a Windows Terminal window named "backend" (or in separate PowerShell windows if Windows Terminal isn't installed). Each tab shows that service's logs.

| Service | Port |
|---|---|
| api-gateway | 8080 |
| user-service | 8081 |
| matching-service | 8083 |
| connection-service | 8084 |
| group-service | 8085 |

The frontend talks to the API gateway on port `8080`, so start the backend before using the frontend.

If PowerShell says running scripts is disabled, run:

```powershell
powershell -ExecutionPolicy Bypass -File .\start-backend.ps1 -All
```

> Open the `.ps1` file from a PowerShell prompt — double-clicking it in File Explorer just opens it in Notepad.

### Stop the backend

From the `backend` folder:

```powershell
.\stop-backend.ps1
```

This stops every service listening on the backend ports and prints which ones it stopped. You can then close the "backend" Terminal window. To stop a single service instead, press **Ctrl+C** in its tab (or close the tab).

Run `stop-backend.ps1` if a start fails with `Port 8081 was already in use` — it means a service from an earlier run is still running.

### Run one service manually

```powershell
cd backend
.\mvnw.cmd -pl user-service spring-boot:run
```

Replace `user-service` with the service you want to start.
