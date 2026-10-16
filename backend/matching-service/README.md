# Matching service

The matching service scores and ranks study-buddy candidates for a student. It
loads student profiles through `StudentClient`, applies the configured search
filters, computes a compatibility value for each matching criterion, combines
those values using the active matching weights, and returns the ranked results
to the API gateway.

## How to read the match percentage

The percentage on the Discover page is the **overall weighted match score**. It
is not an individual criterion's score. For example, with the default balanced
configuration, each of the five enabled criteria has a 20% weight. If two
students have 50% availability compatibility, availability contributes
`0.50 x 20 = 10` percentage points to their overall match. Other criteria add
their weighted contributions.

The score calculation and class responsibilities are explained in
[MATCHING-CALCULATION.txt](MATCHING-CALCULATION.txt).

## Main Java classes

- `controller/MatchingController.java` exposes the student match-search API.
- `client/StudentClient.java` retrieves student profiles from user-service.
- `dto/StudentDto.java` and `dto/CourseDto.java` represent the profile data
  consumed by matching-service.
- `engine/MatchingEngine.java` filters candidates, calculates criterion scores,
  combines configured weights, sorts results and rounds the API score.
- `service/MatchingConfigurationService.java` loads and updates the active
  strategy and criterion weights.
- `engine/MatchingStrategy.java` defines the strategy contract.
  `BalancedMatchingStrategy`, `AvailabilityFirstMatchingStrategy` and
  `CourseFirstMatchingStrategy` implement the available weighting strategies.
- `model/MatchResult.java` is the result returned for each candidate.
  `model/MatchingConfig.java`, `model/MatchingCriterion.java` and
  `model/CriterionType.java` describe the active scoring configuration.
- `model/TimeSlot.java`, `model/StudyGoal.java`, `model/MeetingMode.java` and
  `model/GroupSizePreference.java` define the profile values used by scoring.

The service's admin configuration endpoints are under
`/api/matching/config`; student match searches are under
`/api/matching/students/{studentId}/search`.

## Run and test

From `backend`, start the matching service with:

```powershell
.\mvnw.cmd -pl matching-service spring-boot:run
```

Run its tests with:

```powershell
.\mvnw.cmd -pl matching-service test
```
