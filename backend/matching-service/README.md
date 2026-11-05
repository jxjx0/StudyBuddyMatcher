# Matching service

Scores and ranks study-buddy candidates for a student. Runs on port `8083`; the
gateway routes `/api/matching/**` here.

It loads student profiles through `StudentClient`, applies the search filters,
computes a compatibility value for each enabled criterion, combines those values
using the active strategy's weights, and returns the ranked results.

For the feature list see the [project README](../../README.md); for shared
backend configuration see the [backend README](../README.md).

## Matching criteria

The five criteria in
[`CriterionType`](src/main/java/com/studybuddy/matching/model/CriterionType.java)
correspond to the dimensions in the project brief:

| Criterion | Compares |
|---|---|
| `COURSE` | The course the student needs a buddy for |
| `AVAILABILITY` | Overlap between the two weekly timetables |
| `STUDY_MODE` | In-person / online / either |
| `STUDY_GOAL` | Concept review, problem solving, exam preparation, project discussion |
| `GROUP_SIZE` | One-to-one / small group / either |

An administrator can enable or disable each criterion and set its weight, from
the Matching page in the admin area.

## Matching strategies

All three implement
[`MatchingStrategy`](src/main/java/com/studybuddy/matching/engine/MatchingStrategy.java),
which decides how the configured criteria are weighted before scoring runs. The
engine depends only on that interface, so adding a fourth strategy does not
require changing it.

| Strategy | Behaviour |
|---|---|
| [`BalancedMatchingStrategy`](src/main/java/com/studybuddy/matching/engine/BalancedMatchingStrategy.java) | **Replaces** the administrator's weights with an equal share across every enabled criterion. |
| [`AvailabilityFirstMatchingStrategy`](src/main/java/com/studybuddy/matching/engine/AvailabilityFirstMatchingStrategy.java) | **Keeps** the administrator's weights, but rejects a configuration where `AVAILABILITY` does not carry the highest weight. |
| [`CourseFirstMatchingStrategy`](src/main/java/com/studybuddy/matching/engine/CourseFirstMatchingStrategy.java) | **Keeps** the administrator's weights, but rejects a configuration where `COURSE` does not carry the highest weight. |

The difference matters when configuring: under Balanced, the weight sliders have
no effect on the result. Under the two "first" strategies the sliders are used
as set, and saving a configuration that breaks the strategy's rule is rejected.

## How to read the match percentage

The percentage on the Discover page is the **overall weighted match score**, not
any single criterion's score.

For example, with the balanced configuration each of the five enabled criteria
carries a 20% weight. If two students have 50% availability compatibility,
availability contributes `0.50 × 20 = 10` percentage points to the overall
match. The other criteria add their own weighted contributions, and the total is
rounded for the API.

The full calculation and class responsibilities are explained in
[MATCHING-CALCULATION.txt](MATCHING-CALCULATION.txt).

## Search filters

A search is described by
[`SearchCriteria`](src/main/java/com/studybuddy/matching/model/SearchCriteria.java):
a course, a study goal, and a map of extra filters. Two filter keys are
supported — `meetingMode` and `minOverlapMinutes`. Candidates that fail a filter
are removed before scoring rather than scored and ranked low.

## Main Java classes

- `controller/MatchingController.java` — the student match-search API.
- `controller/MatchingConfigController.java` — the admin configuration API.
- `client/StudentClient.java` — retrieves student profiles from user-service.
- `dto/StudentDto.java`, `dto/CourseDto.java` — the profile data this service
  consumes.
- `engine/MatchingEngine.java` — filters candidates, calculates criterion
  scores, combines the configured weights, sorts and rounds.
- `service/MatchingConfigurationService.java` — loads and updates the active
  strategy and criterion weights.
- `engine/MatchingStrategy.java` plus the three implementations above.
- `model/MatchResult.java` — the result returned per candidate.
- `model/MatchingConfig.java`, `model/MatchingCriterion.java`,
  `model/CriterionType.java` — the active scoring configuration.
- `model/SearchCriteria.java` — a search request.
- `model/TimeSlot.java`, `model/StudyGoal.java`, `model/MeetingMode.java`,
  `model/GroupSizePreference.java` — the profile values used by scoring.

## API

| Method | Path | Access |
|---|---|---|
| `POST` | `/api/matching/students/{studentId}/search` | The student themselves, or an admin |
| `GET` | `/api/matching/config` | Admin |
| `GET` | `/api/matching/config/strategies` | Admin |
| `PUT` | `/api/matching/config/strategy` | Admin |
| `PUT` | `/api/matching/config/criteria` | Admin |

Both groups require a bearer JWT signed with the shared `JWT_SECRET`, including
when calling this service directly instead of through the gateway. Students can
search only for their own user ID.

## Persistence

The active strategy and criterion weights live in `matching_configuration` and
`matching_criteria`. This service runs its
[`schema.sql`](src/main/resources/schema.sql) on every startup — see
[Schema initialization](../README.md#schema-initialization).

## Run

From `backend`:

```powershell
.\mvnw.cmd -pl matching-service spring-boot:run
```

On macOS/Linux use `./mvnw`.
