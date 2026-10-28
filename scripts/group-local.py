#!/usr/bin/env python3
"""Run the full local demo on a disposable PostgreSQL database.
No shared .env is loaded. Ctrl-C stops the demo and removes its temporary database.
"""
import argparse, json, os, pathlib, secrets, shutil, signal, socket, subprocess, tempfile, time, urllib.request
ROOT = pathlib.Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument("mode", choices=["demo"])
args = parser.parse_args()
pg_bin = os.environ.get("PG_BIN")
if not pg_bin:
    candidates = ["/opt/homebrew/opt/postgresql@17/bin", "/usr/lib/postgresql/17/bin"]
    pg_bin = next((x for x in candidates if pathlib.Path(x, "initdb").exists()), None)
if not pg_bin:
    raise SystemExit("Set PG_BIN to the directory containing PostgreSQL 17 initdb/pg_ctl/psql.")
work = pathlib.Path(tempfile.mkdtemp(prefix="studybuddy-group-local-"))
children = []; logs = []; started = False; restart_requested = False

def run(command, **kwargs):
    return subprocess.run(command, check=True, **kwargs)

def free_port():
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0)); return s.getsockname()[1]

def pg(tool, *arguments): return [str(pathlib.Path(pg_bin, tool)), *map(str, arguments)]

def interrupt(sig, frame): raise KeyboardInterrupt()
signal.signal(signal.SIGTERM, interrupt)
def restart_group(sig, frame):
    global restart_requested
    restart_requested = True
if hasattr(signal, "SIGUSR1"): signal.signal(signal.SIGUSR1, restart_group)
try:
    port = free_port(); db = "studybuddy_demo"
    run(pg("initdb", "-D", work/"data", "-U", "studybuddy_local", "--auth-local=trust", "--auth-host=trust", "--no-locale", "--encoding=UTF8"), stdout=subprocess.DEVNULL)
    run(pg("pg_ctl", "-D", work/"data", "-l", work/"postgres.log", "-o", f"-h 127.0.0.1 -p {port} -k {work}", "-w", "start"), stdout=subprocess.DEVNULL)
    started = True
    run(pg("createdb", "-h", "127.0.0.1", "-p", port, "-U", "studybuddy_local", db))
    jdbc = f"jdbc:postgresql://127.0.0.1:{port}/{db}"
    env = {**os.environ, "SPRING_CONFIG_IMPORT": "", "SUPABASE_JDBC_URL": jdbc,
           "SUPABASE_DB_USER": "studybuddy_local", "SUPABASE_DB_PASSWORD": "", "DEMO_DATA_ENABLED": "false",
           "JWT_SECRET": secrets.token_urlsafe(48), "CONTACT_SERVICE_SECRET": secrets.token_urlsafe(48)}
    def sql(text):
        run(pg("psql", "-h", "127.0.0.1", "-p", port, "-U", "studybuddy_local", "-d", db, "-v", "ON_ERROR_STOP=1", "-q"), input=text, text=True, stdout=subprocess.DEVNULL)
    sql((ROOT/"backend/user-service/src/main/resources/schema.sql").read_text())
    sql((ROOT/"backend/group-service/src/main/resources/schema.sql").read_text())
    sql((ROOT/"scripts/group-local-students.sql").read_text())
    run([str(ROOT/"backend/mvnw"), "-B", "package", "-DskipTests"], cwd=ROOT/"backend", env=env, stdout=subprocess.DEVNULL)
    ports = {"user-service":18081, "api-gateway":18080, "matching-service":18083, "group-service":18085}
    # Check the entire set before starting, and never stop a pre-existing server.
    for p in [*ports.values(), 5173]:
        with socket.socket() as check:
            check.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            check.bind(("127.0.0.1", p))
    env.update(USER_SERVICE_URL="http://127.0.0.1:18081", GROUP_SERVICE_URL="http://127.0.0.1:18085", MATCHING_SERVICE_URL="http://127.0.0.1:18083", VITE_API_TARGET="http://127.0.0.1:18080")
    def start(command, directory, name):
        log = open(work/(name+".log"), "a"); logs.append(log)
        child = subprocess.Popen(command, cwd=directory, env=env, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        children.append(child); return child
    def wait_http(url, child):
        for _ in range(120):
            if child.poll() is not None: raise RuntimeError(f"Local service exited; see logs in {work}")
            try:
                with urllib.request.urlopen(url, timeout=2) as res:
                    if res.status == 200: return
            except Exception: time.sleep(0.5)
        raise RuntimeError(f"Timed out starting {url}; see logs in {work}")
    services = {}
    for name,p in ports.items():
        jar=ROOT/f"backend/{name}/target/{name}-0.0.1-SNAPSHOT.jar"
        child=start(["java", "-jar", str(jar), "--server.address=127.0.0.1", f"--server.port={p}"], ROOT/"backend", name)
        wait_http(f"http://127.0.0.1:{p}/actuator/health", child)
        services[name] = child
    def post(path, body):
        req=urllib.request.Request("http://127.0.0.1:18080"+path, data=json.dumps(body).encode(), headers={"Content-Type":"application/json"})
        with urllib.request.urlopen(req) as res: return json.load(res)
    for name in ["Alice", "Bob", "Carol"]:
        post("/api/auth/register", {"name":name,"email":name.lower()+"@local.test","password":"StudyLocal123!","school":"School of Computing and Information Systems","programme":"Information Systems","yearOfStudy":2,"contactNumber":"+65 8000 0000","currentCourseCodes":["IS442"],"coursesForStudyingCodes":["IS442"],"meetingMode":"ONLINE","groupSizePreference":"SMALL_GROUP","studyGoals":["EXAM_PREPARATION"],"weeklyAvailability":[]})
    sql("INSERT INTO user_accounts(email,password_hash,user_role,account_status,profile_data) SELECT 'admin@local.test',password_hash,'ADMIN','ACTIVE','{\"name\":\"Local Admin\"}'::jsonb FROM user_accounts WHERE email='alice@local.test'")
    child=start(["npm","run","dev","--","--host","127.0.0.1","--port","5173","--strictPort"], ROOT/"frontend", "frontend")
    wait_http("http://127.0.0.1:5173", child)
    (work/"runtime.json").write_text(json.dumps({"runnerPid":os.getpid(),"url":"http://127.0.0.1:5173","postgresPort":port,"database":db,"processes":[c.pid for c in children]}))
    print(f"Local demo ready: http://127.0.0.1:5173\nAccounts: alice@local.test, bob@local.test, carol@local.test, admin@local.test\nPassword: StudyLocal123! (local synthetic accounts only)\nLogs: {work}\nCtrl-C stops all demo services and removes the disposable database.", flush=True)
    while True:
        time.sleep(2)
        if restart_requested:
            restart_requested = False
            old = services["group-service"]; os.killpg(old.pid, signal.SIGTERM); old.wait(timeout=15); children.remove(old)
            jar = ROOT/"backend/group-service/target/group-service-0.0.1-SNAPSHOT.jar"
            child = start(["java", "-jar", str(jar), "--server.address=127.0.0.1", "--server.port=18085"], ROOT/"backend", "group-service")
            services["group-service"] = child
            wait_http("http://127.0.0.1:18085/actuator/health", child)
            print("Group service restarted; the same local database and accounts were retained.", flush=True)
        if any(c.poll() is not None for c in children): raise RuntimeError("A demo service stopped; inspect logs")
except KeyboardInterrupt:
    print("Stopping the isolated local run.", flush=True)
finally:
    for child in reversed(children):
        if child.poll() is None:
            os.killpg(child.pid, signal.SIGTERM)
            try: child.wait(timeout=8)
            except subprocess.TimeoutExpired: os.killpg(child.pid,signal.SIGKILL); child.wait()
    for log in logs: log.close()
    if started: subprocess.run(pg("pg_ctl", "-D", work/"data", "-m", "fast", "-w", "stop"), stdout=subprocess.DEVNULL)
    # Preserve small diagnostic logs, never a database cluster or signing secret.
    shutil.rmtree(work/"data", ignore_errors=True)
    print(f"Temporary database removed. Logs: {work}", flush=True)
