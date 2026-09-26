# Study Buddy Matcher - Backend

Five Spring Boot applications plus a gateway. The frontend only ever talks to
the gateway, which forwards each request to the right service.


## Running it

You need **JDK 21 or higher** (`java -version`). Maven is not needed.

### Spring Boot Dashboard (easiest, Mac and Windows)

1. Install **Extension Pack for Java** and **Spring Boot Extension Pack** in
   VS Code
2. Open the project and wait for it to finish importing
3. In the Explorer sidebar, open **SPRING BOOT DASHBOARD → Apps**
4. Start the five apps

If a service shows a spinner instead of a green dot, it's usually running
anyway. Check with `curl http://localhost:8081/actuator/health`.

### Command line

Build everything first, then run each service in its own terminal.

**Mac**

```bash
cd backend
./mvnw clean install
./mvnw spring-boot:run -pl user-service
```

**Windows**

```cmd
cd backend
mvnw.cmd clean install
mvnw.cmd spring-boot:run -pl user-service
```

Swap `user-service` for whichever service you want. Running all five this way
needs five terminals, which is why the dashboard is easier.

### Check it worked

Open <http://localhost:8080/api/students> - you should see `[]`. Empty is
correct for now: the route works, there's just no data.

## If something seems broken

Don't start the same service twice - the second one fails quietly, and
requests keep going to the first.

To see what's using the ports:

```bash
lsof -ti:8080,8081,8083,8084,8085           # Mac
netstat -ano | findstr "8080 8081"          # Windows
```

## File structure

```
backend/
├── pom.xml              lists the modules and sets the Java/Spring versions
├── mvnw, mvnw.cmd       build tool (see below) - never edited
│
├── api-gateway/         routes requests to the services
├── user-service/
├── matching-service/
├── connection-service/
└── group-service/
```

`mvnw` is Maven, the tool that downloads our libraries and compiles the code -
`mvnw` for Mac, `mvnw.cmd` for Windows. It downloads the correct Maven version
by itself, which is why nobody has to install anything. You run it
(`./mvnw clean install`) but never open or edit it.

Every service has the same layout inside. Using `user-service` as the example:

```
user-service/
├── pom.xml                              this service's dependencies
└── src/main/
    ├── resources/application.yml        port and settings
    └── java/com/studybuddy/user/
        ├── UserServiceApplication.java  starts the service - don't touch
        ├── controller/                  StudentController, CourseController, AdminController
        ├── service/                     StudentService, UserAccountService, CourseService
        ├── repository/                  UserRepository, CourseRepository
        ├── model/                       User, Student, SystemAdministrator, Course, enums, TimeSlot
        └── dto/                         StudentRequest, StudentDto, StudentContactDto, CourseDto
```

## How it's organised

Each service is built from these folders:

```
controller/   receives the HTTP request
service/      does the work and applies the rules
repository/   gets the data (empty stubs for now)
model/        the objects being passed around - Student, StudyGroup, ...
client/       asks another service for something, over HTTP
dto/          the shape of data coming in or going out
```

The first four are the main ones. A service only has `client/` if it needs
data another service owns, and `repository/` if it stores anything itself -
so matching-service has a client but no repository, and the others are the
other way round.

A typical request travels through them in order:

```
controller  →  service  →  repository  →  JPA  →  Supabase
                                          ^^^^^^^^^^^^^^^^
                                          not implemented yet
```


JPA would be the part that turns Java into SQL, so we wouldn't write SQL
ourselves. Nothing past the repository exists yet, which is why endpoints
return empty.

For example `GET /api/students/S00001`: the **controller** picks the id out of
the URL, the **service** decides what to do, the **repository** fetches the
data, and a **Student** object comes back up.

Because only the repository touches storage, connecting Supabase later changes
that one folder and nothing else - so the controller and service logic can be
written now.

**When writing code:** keep controllers short, put the logic in the service,
and only let the repository touch data.

## Services

| Service              | Port | Handles                                            |
|----------------------|------|----------------------------------------------------|
| `api-gateway`        | 8080 | Routes requests - the only port the frontend uses  |
| `user-service`       | 8081 | Students, admins, accounts, courses                |
| `matching-service`   | 8083 | Matching engine and strategies                     |
| `connection-service` | 8084 | Match requests and connections                     |
| `group-service`      | 8085 | Study groups and join requests                     |

## Clients - how services ask each other for data

This whole idea only exists because we chose microservices. In a single
application you would just write `student.getName()`, because every class
would be in the same program. Splitting into separate services means the data
lives elsewhere, so it has to be fetched over the network instead.

## Duplicated classes - keep the copies in step

There is no shared module. Each service keeps its own copy of the classes it
sends or receives, so every service builds and runs on its own.

| Class                                      | Copies in                              |
|--------------------------------------------|----------------------------------------|
| `StudentDto`, `CourseDto`                  | user, matching, connection, group      |
| `StudentContactDto`                        | user, connection                       |
| `AccountStatus`, `MeetingMode`, `StudyGoal` | user, matching, connection, group      |
| `TimeSlot`                                 | user, matching, connection, group      |

The price is that the compiler no longer checks the copies against each other.
Services exchange JSON, and the field names are the contract:

- **Rename a field in one copy only** and it still compiles - the value just
  arrives empty on the other side.
- **Add an enum value in one copy only** and the other services fail to read
  any student that uses it.
- **`TimeSlot.overlapMinutes` must behave the same everywhere**, or matching
  and groups will disagree about whether two students can meet.

`user-service` owns student data, so its copies are the source of truth. When
you change one of these classes, change every copy in the same commit.


## The API gateway

The frontend never calls the services directly. It sends everything to
`localhost:8080`, and the gateway passes each request on to whichever service
handles it.

```
Vue  →  gateway :8080  →  user-service :8081
                       →  matching-service :8083
                       →  connection-service :8084
                       →  group-service :8085
```

This means the frontend only needs one address instead of four, and ports can
change without touching any frontend code. Later it is also the natural place
to put login checks, so they don't have to be repeated in every service.

The gateway has no controllers, services or models - it is just a list of
routes in `api-gateway/src/main/resources/application.yml`:

| Path                                                        | Goes to            |
|-------------------------------------------------------------|--------------------|
| `/api/users`, `/api/students`, `/api/courses`, `/api/admin`  | user-service       |
| `/api/matching`                                              | matching-service   |
| `/api/match-requests`, `/api/connections`                    | connection-service |
| `/api/groups`                                                | group-service      |

Add a controller under one of these paths and it works through the gateway
automatically. Use a new path and you'll need to add a route.

## Where things differ from the UML diagram

- **Links between services are ids, not objects** - `MatchRequest` holds
  `senderId`, not a `Student`. Separate programs can't share objects.
- **`SystemAdministrator` has no methods** - they're in `UserAccountService`,
  because a model object can't reach the database on its own.
- **`updateProfile()` and `updateGroupInfo()` take parameters** - the diagram's
  versions take none, so they couldn't do anything.
- **`getOtherStudent()` is `getOtherStudentId()`** - it returns an id, because
  connection-service has no `Student` class to return.
- **`SearchCriteria` has no `filters` map** - the diagram has
  `Map<String, Object>`, which documents nothing about what can be filtered on.
  It's named fields instead: `courseCode`, `studyGoal`, `meetingMode`,
  `minOverlapMinutes`.
- **Four methods were added.** `canBeRespondedBy()` on `MatchRequest` and
  `isLedBy()` / `hasMember()` on `StudyGroup`, because the diagram can't say
  who is allowed to accept a request or close a group. Plus `getName()` on
  `MatchingStrategy`, so an admin can pick a strategy by name.

Two things for the team to decide: group size is an `int` in the diagram but
the brief asks for "one-to-one, small group, or either"; and
`getMatchedCriteria()` is drawn returning `List<String>` when the field is
`List<CriterionType>`.
