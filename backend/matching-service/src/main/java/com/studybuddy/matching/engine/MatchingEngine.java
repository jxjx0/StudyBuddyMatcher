package com.studybuddy.matching.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
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

/**
 * Scores candidate students against a searching student and returns them
 * ranked.
 *
 * Candidates come from user-service over HTTP, not from local storage - this
 * service owns no student data. Weights come from the active MatchingConfig,
 * so the engine does not change when an administrator reconfigures matching.
 */
@Service
public class MatchingEngine {

	private static final Logger log = LoggerFactory.getLogger(MatchingEngine.class);

	private static final int FULL_OVERLAP_MINUTES = 120;

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
		// The weights are the same for every candidate, so they are worked out once per search.
		List<MatchingCriterion> weights = resolveWeights(currentConfig);
		SearchCriteria filters = criteria == null ? new SearchCriteria() : criteria;

		List<MatchResult> results = new ArrayList<>();
		for (StudentDto candidate : studentClient.findAllStudents()) {
			if (candidate == null) {
				continue;
			}
			if (candidate.getUserId() != null && candidate.getUserId().equals(student.getUserId())) {
				continue;
			}
			if (candidate.getAccountStatus() != null && candidate.getAccountStatus() != AccountStatus.ACTIVE) {
				continue;
			}
			if (!passesFilters(student, candidate, filters)) {
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
		return weightedScore(scoreComponents(a, b), resolveWeights(config));
	}

	public double scoreCourseMatch(StudentDto a, StudentDto b) {
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
		int minutes = totalOverlapMinutes(a, b);
		return minutes <= 0 ? 0 : Math.min(1.0, (double) minutes / FULL_OVERLAP_MINUTES);
	}

	public double scoreStudyModeCompat(StudentDto a, StudentDto b) {
		return modesCompatible(a.getMeetingMode(), b.getMeetingMode()) ? 1.0 : 0.0;
	}

	public double scoreStudyGoalOverlap(StudentDto a, StudentDto b) {
		Set<StudyGoal> goalsA = new HashSet<>(a.getStudyGoals());
		Set<StudyGoal> goalsB = new HashSet<>(b.getStudyGoals());
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

	private boolean passesFilters(StudentDto searcher, StudentDto candidate, SearchCriteria criteria) {
		if (criteria.getCourse() != null && criteria.getCourse().getCourseCode() != null
				&& !takesCourse(candidate, criteria.getCourse().getCourseCode())) {
			return false;
		}
		if (criteria.getStudyGoal() != null && !candidate.getStudyGoals().contains(criteria.getStudyGoal())) {
			return false;
		}
		MeetingMode mode = readMeetingMode(criteria);
		if (mode != null && !modesCompatible(mode, candidate.getMeetingMode())) {
			return false;
		}
		int minOverlap = readMinOverlapMinutes(criteria);
		if (minOverlap > 0 && totalOverlapMinutes(searcher, candidate) < minOverlap) {
			return false;
		}
		return true;
	}

	private MeetingMode readMeetingMode(SearchCriteria criteria) {
		Object value = criteria.getFilters() == null ? null : criteria.getFilters().get("meetingMode");
		if (value instanceof MeetingMode meetingMode) {
			return meetingMode;
		}
		if (value instanceof String text && !text.isBlank()) {
			try {
				return MeetingMode.valueOf(text.trim().toUpperCase());
			} catch (IllegalArgumentException e) {
				throw new IllegalArgumentException("meetingMode must be IN_PERSON, ONLINE or EITHER");
			}
		}
		return null;
	}

	private int readMinOverlapMinutes(SearchCriteria criteria) {
		Object value = criteria.getFilters() == null ? null : criteria.getFilters().get("minOverlapMinutes");
		if (value instanceof Number number) {
			return number.intValue();
		}
		if (value instanceof String text && !text.isBlank()) {
			try {
				return Integer.parseInt(text.trim());
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException("minOverlapMinutes must be a whole number of minutes");
			}
		}
		return 0;
	}

	private boolean takesCourse(StudentDto student, String courseCode) {
		Set<String> codes = courseCodes(student.getCoursesForStudying());
		codes.addAll(courseCodes(student.getCurrentCourses()));
		return codes.contains(courseCode.trim().toUpperCase());
	}

	private int totalOverlapMinutes(StudentDto a, StudentDto b) {
		int total = 0;
		for (TimeSlot slotA : a.getWeeklyAvailability()) {
			if (slotA == null) {
				continue;
			}
			for (TimeSlot slotB : b.getWeeklyAvailability()) {
				if (slotB != null) {
					total += slotA.overlapMinutes(slotB);
				}
			}
		}
		return total;
	}

	private Set<String> courseCodes(List<CourseDto> courses) {
		Set<String> codes = new HashSet<>();
		if (courses == null) {
			return codes;
		}
		for (CourseDto course : courses) {
			if (course != null && course.getCourseCode() != null) {
				codes.add(course.getCourseCode().trim().toUpperCase());
			}
		}
		return codes;
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
