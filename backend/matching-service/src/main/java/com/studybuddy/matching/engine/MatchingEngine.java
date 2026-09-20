package com.studybuddy.matching.engine;

import com.studybuddy.common.dto.StudentDto;
import com.studybuddy.matching.client.StudentClient;
import com.studybuddy.matching.service.MatchingConfigurationService;
import com.studybuddy.matching.model.MatchResult;
import com.studybuddy.matching.model.MatchingConfig;
import com.studybuddy.matching.model.SearchCriteria;
import org.springframework.stereotype.Service;

import java.util.List;

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

	private final MatchingConfigurationService configService;
	private final StudentClient studentClient;

	public MatchingEngine(MatchingConfigurationService configService, StudentClient studentClient) {
		this.configService = configService;
		this.studentClient = studentClient;
	}

	/**
	 * Ranked matches for one student, highest score first.
	 */
	public List<MatchResult> findMatches(String studentId, SearchCriteria criteria) {
		StudentDto searcher = studentClient.findStudent(studentId);
		List<StudentDto> candidates = studentClient.findAllStudents();

		// TODO: drop the searcher from the candidates, score each remaining one
		// with computeScore, filter by the search criteria, sort descending
		return List.of();
	}

	private double computeScore(StudentDto a, StudentDto b, MatchingConfig config) {
		// TODO: combine the criterion scores below using the configured weights
		return 0;
	}

	private double scoreCourseMatch(StudentDto a, StudentDto b) {
		// TODO: implement
		return 0;
	}

	private double scoreAvailabilityOverlap(StudentDto a, StudentDto b) {
		// TODO: implement using TimeSlot.overlapMinutes
		return 0;
	}

	private double scoreStudyModeCompat(StudentDto a, StudentDto b) {
		// TODO: implement - EITHER is compatible with everything
		return 0;
	}

	private double scoreStudyGoalOverlap(StudentDto a, StudentDto b) {
		// TODO: implement
		return 0;
	}

	private double scoreGroupSizeCompat(StudentDto a, StudentDto b) {
		// TODO: implement
		return 0;
	}
}
