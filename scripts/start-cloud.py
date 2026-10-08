#!/usr/bin/env python3
"""Run the local app against cloud Supabase on macOS/Linux.

Reads backend/.env. Does not create tables, seed data or start local PostgreSQL.
Ctrl-C stops only the processes started by this script.
"""

import os
from pathlib import Path
import signal
import socket
import subprocess
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
SERVICES = {
    "user-service": 8081,
    "matching-service": 8083,
    "group-service": 8085,
    "connection-service": 8084,
    "api-gateway": 8080,
}


def load_environment():
    settings = {}
    config = ROOT / "backend/.env"
    if not config.is_file():
        raise RuntimeError("Create backend/.env using backend/.env.example first.")
    for line in config.read_text().splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            settings[key.strip()] = value.strip().strip("\"'")

    required = ["SUPABASE_JDBC_URL", "SUPABASE_DB_USER", "SUPABASE_DB_PASSWORD", "JWT_SECRET"]
    for key in required:
        if not settings.get(key):
            raise RuntimeError(f"Missing {key} in backend/.env")
    if len(settings["JWT_SECRET"]) < 32:
        raise RuntimeError("JWT_SECRET must contain at least 32 characters.")

    database = urllib.parse.urlparse(settings["SUPABASE_JDBC_URL"].removeprefix("jdbc:"))
    if not database.hostname or not database.hostname.endswith((".supabase.co", ".supabase.com")):
        raise RuntimeError("This command requires a cloud Supabase database URL.")
    options = urllib.parse.parse_qs(database.query)
    if database.port == 6543 and options.get("prepareThreshold") != ["0"]:
        raise RuntimeError("Add prepareThreshold=0 to the transaction-pool JDBC URL.")

    environment = {**os.environ, **settings}
    # Cloud schema changes are applied explicitly; existing records are never re-seeded.
    environment.update({
        "SPRING_CONFIG_IMPORT": "",
        "SPRING_SQL_INIT_MODE": "never",
        "DEMO_DATA_ENABLED": "false",
        "USER_SERVICE_URL": "http://127.0.0.1:8081",
        "MATCHING_SERVICE_URL": "http://127.0.0.1:8083",
        "GROUP_SERVICE_URL": "http://127.0.0.1:8085",
        "CONNECTION_SERVICE_URL": "http://127.0.0.1:8084",
        "VITE_API_TARGET": "http://127.0.0.1:8080",
    })
    return environment, database.hostname


def check_ports():
    for port in [*SERVICES.values(), 5173]:
        with socket.socket() as check:
            check.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            check.bind(("127.0.0.1", port))


def wait_ready(url, process, log_path):
    for _ in range(120):
        if process.poll() is not None:
            raise RuntimeError(f"Service stopped. See {log_path}")
        try:
            with urllib.request.urlopen(url, timeout=2) as response:
                if response.status == 200:
                    return
        except (urllib.error.URLError, TimeoutError):
            pass
        time.sleep(0.5)
    raise RuntimeError(f"Service did not become ready. See {log_path}")


def stop_processes(processes):
    for process in reversed(processes):
        if process.poll() is None:
            os.killpg(process.pid, signal.SIGTERM)
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGKILL)
                process.wait()


def interrupt(signum, frame):
    raise KeyboardInterrupt()


def main():
    if os.name == "nt":
        raise RuntimeError("On Windows, use backend/start-backend.ps1 -All.")
    environment, database_host = load_environment()
    check_ports()
    logs = Path(tempfile.mkdtemp(prefix="studybuddy-cloud-"))
    print(f"Cloud database: {database_host}", flush=True)
    print(f"Logs: {logs}", flush=True)
    subprocess.run(
        [str(ROOT / "backend/mvnw"), "-B", "package", "-DskipTests"],
        cwd=ROOT / "backend", env=environment, check=True,
    )

    processes = []
    signal.signal(signal.SIGTERM, interrupt)
    try:
        for name, port in SERVICES.items():
            jar = ROOT / f"backend/{name}/target/{name}-0.0.1-SNAPSHOT.jar"
            log_path = logs / f"{name}.log"
            with log_path.open("w") as output:
                process = subprocess.Popen(
                    ["java", "-jar", str(jar), "--server.address=127.0.0.1", f"--server.port={port}"],
                    cwd=ROOT / "backend", env=environment, stdout=output,
                    stderr=subprocess.STDOUT, start_new_session=True,
                )
            processes.append(process)
            wait_ready(f"http://127.0.0.1:{port}/actuator/health", process, log_path)
            print(f"Ready: {name}", flush=True)

        log_path = logs / "frontend.log"
        with log_path.open("w") as output:
            process = subprocess.Popen(
                ["npm", "run", "dev", "--", "--host", "127.0.0.1", "--port", "5173", "--strictPort"],
                cwd=ROOT / "frontend", env=environment, stdout=output,
                stderr=subprocess.STDOUT, start_new_session=True,
            )
        processes.append(process)
        wait_ready("http://127.0.0.1:5173", process, log_path)
        print("Cloud-backed app ready: http://127.0.0.1:5173", flush=True)
        print("Use your existing cloud account. Local Alice/Bob/Carol accounts are not created.", flush=True)
        while True:
            if any(process.poll() is not None for process in processes):
                raise RuntimeError(f"A service stopped. See {logs}")
            time.sleep(2)
    except KeyboardInterrupt:
        print("Stopping local app processes. Cloud data is retained.", flush=True)
    finally:
        stop_processes(processes)


if __name__ == "__main__":
    main()
