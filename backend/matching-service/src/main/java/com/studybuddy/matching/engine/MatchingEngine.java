package com.studybuddy.matching.engine;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.studybuddy.matching.client.StudentClient;
import com.studybuddy.matching.dto.CourseDto;
import com.studybuddy.matching.dto.StudentDto;
import com.studybuddy.matching.model.AccountStatus;
import com.studybuddy.matching.model.CriterionType;
import com.studybuddy.matching.model.GroupSizePreference;
import com.studybuddy.matching.model.MatchResult;
import com.studybuddy.matching.model.MatchingConfig;
import com.studybuddy.matching.model.MatchingCriterion;
import com.studybuddy.matching.model.MeetingMode;
import com.studybuddy.matching.model.SearchCriteria;
import com.studybuddy.matching.model.StudyGoal;
import com.studybuddy.matching.model.TimeSlot;
import com.studybuddy.matching.service.MatchingConfigurationService;

@Service
public class MatchingEngine {

	private static final Logger log = LoggerFactory.getLogger(MatchingEngine.class);

	// The UML shows currentConfig as a field. It is read into a local variable on
	// each search instead: Spring shares one MatchingEngine across all requests,
	// so a field would let two simultaneous searches overwrite each other's config.
	private final MatchingConfigurationService configService;
	private final StudentClient studentClient;

	public MatchingEngine(MatchingConfigurationService configService, StudentClient studentClient) {
		this.configService = configService;
		this.studentClient = studentClient;
	}

	public List<MatchResult> findMatches(StudentDto student, SearchCriteria criteria) {
		if (student == null) {
			return List.of();
		}
		MatchingConfig currentConfig = configService.getCurrentConfig();
		// The weights and filters are resolved once per search, rather than once per candidate.
		List<MatchingCriterion> weights = resolveWeights(currentConfig);
		SearchCriteria filters = criteria == null ? new SearchCriteria() : criteria;
		String requestedCourseCode = filters.getCourse() == null ? null : filters.getCourse().getCourseCode();
		String courseCode = requestedCourseCode == null || requestedCourseCode.isBlank()
				? null : normalizeCourseCode(requestedCourseCode);
		StudyGoal studyGoal = filters.getStudyGoal();
		MeetingMode meetingMode = readMeetingMode(filters);
		int minOverlapMinutes = readMinOverlapMinutes(filters);

		List<StudentDto> candidates = studentClient.findAllStudents();
		if (candidates == null || candidates.isEmpty()) {
			return List.of();
		}

		List<MatchResult> results = new ArrayList<>();
		for (StudentDto candidate : candidates) {
			if (candidate == null) {
				continue;
			}
			if (candidate.getUserId() != null && candidate.getUserId().equals(student.getUserId())) {
				continue;
			}
			if (candidate.getAccountStatus() != null && candidate.getAccountStatus() != AccountStatus.ACTIVE) {
				continue;
			}
			if (!passesFilters(student, candidate, courseCode, studyGoal, meetingMode, minOverlapMinutes)) {
				continue;
			}
			Map<CriterionType, Double> components = scoreComponents(student, candidate);
			double score = weightedScore(components, weights);
			results.add(new MatchResult(candidate, round(score), matchedCriteria(components, weights)));
		}
		results.sort(Comparator.comparingDouble(MatchResult::getScore).reversed());
		return results;
	}

	public double computeScore(StudentDto a, StudentDto b, MatchingConfig config) {
		if (a == null || b == null) {
			return 0;
		}
		return weightedScore(scoreComponents(a, b), resolveWeights(config));
	}

	public double scoreCourseMatch(StudentDto a, StudentDto b) {
		if (a == null || b == null) {
			return 0;
		}
		Set<String> wanted = courseCodes(a.getCoursesForStudying());
		if (wanted.isEmpty()) {
			wanted = courseCodes(a.getCurrentCourses());
		}
		if (wanted.isEmpty()) {
			return 0;
		}
		Set<String> candidateCourses = courseCodes(b.getCoursesForStudying());
		candidateCourses.addAll(courseCodes(b.getCurrentCourses()));
		long shared = wanted.stream().filter(candidateCourses::contains).count();
		return (double) shared / wanted.size();
	}

	public double scoreAvailabilityOverlap(StudentDto a, StudentDto b) {
		if (a == null || b == null) {
			return 0;
		}
		int minutes = totalOverlapMinutes(a, b);
		if (minutes <= 0) {
			return 0;
		}
		// The share of the searcher's (a's) free time that the candidate can also make, like
		// scoreCourseMatch counts the searcher's courses. Scores can differ by who searches.
		int searcherFreeMinutes = totalFreeMinutes(a);
		return Math.min(1.0, (double) minutes / searcherFreeMinutes);
	}

	public double scoreStudyModeCompat(StudentDto a, StudentDto b) {
		if (a == null || b == null) {
			return 0;
		}
		return modesCompatible(a.getMeetingMode(), b.getMeetingMode()) ? 1.0 : 0.0;
	}

	public double scoreStudyGoalOverlap(StudentDto a, StudentDto b) {
		if (a == null || b == null) {
			return 0;
		}
		Set<StudyGoal> goalsA = nonNullGoals(a.getStudyGoals());
		Set<StudyGoal> goalsB = nonNullGoals(b.getStudyGoals());
		if (goalsA.isEmpty() || goalsB.isEmpty()) {
			return 0;
		}
		Set<StudyGoal> shared = new HashSet<>(goalsA);
		shared.retainAll(goalsB);
		if (shared.isEmpty()) {
			return 0;
		}
		Set<StudyGoal> union = new HashSet<>(goalsA);
		union.addAll(goalsB);
		return (double) shared.size() / union.size();
	}

	public double scoreGroupSizeCompat(StudentDto a, StudentDto b) {
		if (a == null || b == null) {
			return 0;
		}
		return groupSizesCompatible(a.getGroupSizePreference(), b.getGroupSizePreference()) ? 1.0 : 0.0;
	}

	// ----- internal helpers (implementation detail, not in the class diagram) -----

	private Map<CriterionType, Double> scoreComponents(StudentDto a, StudentDto b) {
		Map<CriterionType, Double> components = new EnumMap<>(CriterionType.class);
		components.put(CriterionType.COURSE, scoreCourseMatch(a, b));
		components.put(CriterionType.AVAILABILITY, scoreAvailabilityOverlap(a, b));
		components.put(CriterionType.STUDY_MODE, scoreStudyModeCompat(a, b));
		components.put(CriterionType.STUDY_GOAL, scoreStudyGoalOverlap(a, b));
		components.put(CriterionType.GROUP_SIZE, scoreGroupSizeCompat(a, b));
		return components;
	}

	/**
	 * The weights to score with: the strategy's version of the administrator's
	 * weights, or the administrator's weights as they are when no strategy is set.
	 */
	private List<MatchingCriterion> resolveWeights(MatchingConfig config) {
		if (config == null || config.getCriteria() == null || config.getCriteria().isEmpty()) {
			return List.of();
		}
		if (config.getStrategy() == null) {
			return config.getCriteria();
		}
		try {
			return config.getStrategy().applyStrategy(config.getCriteria());
		} catch (IllegalArgumentException e) {
			// Weights saved before the strategy rules were enforced can break them. Searching should
			// still work, so the administrator's weights are used until they are saved again.
			log.warn("Saved matching weights break the {} rule ({}); using them as saved.",
					config.getStrategy().getName(), e.getMessage());
			return config.getCriteria();
		}
	}

	private double weightedScore(Map<CriterionType, Double> components, List<MatchingCriterion> weights) {
		if (components == null || weights == null || weights.isEmpty()) {
			return 0;
		}
		double totalWeight = 0;
		double weightedSum = 0;
		for (MatchingCriterion criterion : weights) {
			if (criterion == null || !criterion.isEnabled() || criterion.getType() == null || criterion.getWeight() <= 0) {
				continue;
			}
			totalWeight += criterion.getWeight();
			weightedSum += criterion.getWeight() * components.getOrDefault(criterion.getType(), 0.0);
		}
		return totalWeight <= 0 ? 0 : weightedSum / totalWeight * 100.0;
	}

	private List<String> matchedCriteria(Map<CriterionType, Double> components, List<MatchingCriterion> weights) {
		List<String> matched = new ArrayList<>();
		if (components == null || weights == null) {
			return matched;
		}
		for (MatchingCriterion criterion : weights) {
			// Same skip rule as weightedScore, so a tag only appears for criteria that counted.
			if (criterion == null || !criterion.isEnabled() || criterion.getType() == null || criterion.getWeight() <= 0) {
				continue;
			}
			if (components.getOrDefault(criterion.getType(), 0.0) > 0) {
				matched.add(criterion.getType().name());
			}
		}
		return matched;
	}

	private boolean passesFilters(StudentDto searcher, StudentDto candidate, String courseCode,
			StudyGoal studyGoal, MeetingMode meetingMode, int minOverlapMinutes) {
		if (courseCode != null && !takesCourse(candidate, courseCode)) {
			return false;
		}
		if (studyGoal != null && (candidate.getStudyGoals() == null || !candidate.getStudyGoals().contains(studyGoal))) {
			return false;
		}
		if (meetingMode != null && !modesCompatible(meetingMode, candidate.getMeetingMode())) {
			return false;
		}
		return minOverlapMinutes <= 0 || totalOverlapMinutes(searcher, candidate) >= minOverlapMinutes;
	}

	private MeetingMode readMeetingMode(SearchCriteria criteria) {
		Object value = criteria.getFilters() == null ? null
				: criteria.getFilters().get(SearchCriteria.FILTER_MEETING_MODE);
		if (value == null || value instanceof String text && text.isBlank()) {
			return null;
		}
		if (value instanceof MeetingMode meetingMode) {
			return meetingMode;
		}
		if (value instanceof String text) {
			try {
				return MeetingMode.valueOf(text.trim().toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException e) {
				throw new IllegalArgumentException("meetingMode must be IN_PERSON, ONLINE or EITHER");
			}
		}
		throw new IllegalArgumentException("meetingMode must be a string: IN_PERSON, ONLINE or EITHER");
	}

	private int readMinOverlapMinutes(SearchCriteria criteria) {
		Object value = criteria.getFilters() == null ? null
				: criteria.getFilters().get(SearchCriteria.FILTER_MIN_OVERLAP_MINUTES);
		if (value == null || value instanceof String text && text.isBlank()) {
			return 0;
		}

		double parsed;
		if (value instanceof Number number) {
			parsed = number.doubleValue();
		} else if (value instanceof String text) {
			try {
				parsed = Long.parseLong(text.trim());
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException("minOverlapMinutes must be a whole number of minutes");
			}
		} else {
			throw new IllegalArgumentException("minOverlapMinutes must be a whole number of minutes");
		}

		// At most 10,080 minutes exist in a seven-day week. Reject negative,
		// fractional, infinite and out-of-range values instead of truncating them.
		if (!Double.isFinite(parsed) || parsed != Math.rint(parsed) || parsed < 0 || parsed > 10_080) {
			throw new IllegalArgumentException("minOverlapMinutes must be a whole number between 0 and 10080");
		}
		return (int) parsed;
	}

	private boolean takesCourse(StudentDto student, String courseCode) {
		if (student == null || courseCode == null || courseCode.isBlank()) {
			return false;
		}
		Set<String> codes = courseCodes(student.getCoursesForStudying());
		codes.addAll(courseCodes(student.getCurrentCourses()));
		return codes.contains(normalizeCourseCode(courseCode));
	}

	private int totalOverlapMinutes(StudentDto a, StudentDto b) {
		if (a == null || b == null) {
			return 0;
		}
		// Merged first, so a student's own overlapping slots cannot count the same time twice.
		List<TimeSlot> slotsB = mergeSlots(b.getWeeklyAvailability());
		int total = 0;
		for (TimeSlot slotA : mergeSlots(a.getWeeklyAvailability())) {
			for (TimeSlot slotB : slotsB) {
				total += slotA.overlapMinutes(slotB);
			}
		}
		return total;
	}

	private int totalFreeMinutes(StudentDto student) {
		// Merged first, so a student's own overlapping slots cannot count the same time twice.
		int total = 0;
		for (TimeSlot slot : mergeSlots(student.getWeeklyAvailability())) {
			total += (int) Duration.between(slot.getStartTime(), slot.getEndTime()).toMinutes();
		}
		return total;
	}

	/**
	 * Joins one student's overlapping or touching slots on the same day, for
	 * example Mon 16:00-18:00 and Mon 17:00-19:00 become Mon 16:00-19:00.
	 * Slots with a missing day or time, or that end before they start, are left out.
	 */
	private List<TimeSlot> mergeSlots(List<TimeSlot> slots) {
		List<TimeSlot> valid = new ArrayList<>();
		if (slots != null) {
			for (TimeSlot slot : slots) {
				if (slot != null && slot.getDay() != null && !slot.getDay().isBlank()
						&& slot.getStartTime() != null && slot.getEndTime() != null
						&& slot.getEndTime().isAfter(slot.getStartTime())) {
					valid.add(new TimeSlot(slot.getDay().trim(), slot.getStartTime(), slot.getEndTime()));
				}
			}
		}
		valid.sort(Comparator.comparing((TimeSlot slot) -> slot.getDay().toUpperCase(Locale.ROOT))
				.thenComparing(TimeSlot::getStartTime));

		List<TimeSlot> merged = new ArrayList<>();
		TimeSlot current = null;
		for (TimeSlot slot : valid) {
			boolean sameDay = current != null && current.getDay().equalsIgnoreCase(slot.getDay());
			if (sameDay && !slot.getStartTime().isAfter(current.getEndTime())) {
				// Starts before the current slot ends: stretch the current slot instead of adding another.
				LocalTime end = slot.getEndTime().isAfter(current.getEndTime()) ? slot.getEndTime() : current.getEndTime();
				current = new TimeSlot(current.getDay(), current.getStartTime(), end);
				merged.set(merged.size() - 1, current);
			} else {
				current = new TimeSlot(slot.getDay(), slot.getStartTime(), slot.getEndTime());
				merged.add(current);
			}
		}
		return merged;
	}

	private Set<String> courseCodes(List<CourseDto> courses) {
		Set<String> codes = new HashSet<>();
		if (courses == null) {
			return codes;
		}
		for (CourseDto course : courses) {
			if (course != null && course.getCourseCode() != null) {
				String code = normalizeCourseCode(course.getCourseCode());
				if (!code.isEmpty()) {
					codes.add(code);
				}
			}
		}
		return codes;
	}

	private Set<StudyGoal> nonNullGoals(List<StudyGoal> goals) {
		Set<StudyGoal> result = new HashSet<>();
		if (goals != null) {
			for (StudyGoal goal : goals) {
				if (goal != null) {
					result.add(goal);
				}
			}
		}
		return result;
	}

	private String normalizeCourseCode(String courseCode) {
		return courseCode == null ? "" : courseCode.trim().toUpperCase(Locale.ROOT);
	}

	private boolean modesCompatible(MeetingMode one, MeetingMode two) {
		if (one == null || two == null || one == MeetingMode.EITHER || two == MeetingMode.EITHER) {
			return true;
		}
		return one == two;
	}

	private boolean groupSizesCompatible(GroupSizePreference one, GroupSizePreference two) {
		if (one == null || two == null || one == GroupSizePreference.EITHER || two == GroupSizePreference.EITHER) {
			return true;
		}
		return one == two;
	}

	private double round(double score) {
		return Math.round(score * 10.0) / 10.0;
	}
}