package com.studybuddy.matching.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.studybuddy.matching.dto.StudentDto;
import com.studybuddy.matching.model.TimeSlot;

class MatchingEngineTest {

	private final MatchingEngine matchingEngine = new MatchingEngine(null, null);

	@Test
	void oneHourOfSharedAvailabilityScoresAsFullCompatibility() {
		StudentDto student = studentWithSlot("MONDAY", "18:00", "19:00");
		StudentDto candidate = studentWithSlot("MONDAY", "18:00", "19:00");

		assertEquals(1.0, matchingEngine.scoreAvailabilityOverlap(student, candidate));
	}

	@Test
	void compatibilityUsesSharedDurationAsAProportionOfTheLargerSchedule() {
		StudentDto student = studentWithSlot("MONDAY", "18:00", "19:00");
		StudentDto halfHourOverlap = studentWithSlot("MONDAY", "18:30", "19:30");
		StudentDto sameTwoHourSchedule = studentWithSlot("MONDAY", "17:00", "19:00");

		assertEquals(0.5, matchingEngine.scoreAvailabilityOverlap(student, halfHourOverlap));
		assertEquals(1.0, matchingEngine.scoreAvailabilityOverlap(sameTwoHourSchedule,
				studentWithSlot("MONDAY", "17:00", "19:00")));
	}

	@Test
	void oneHourOverlapOfOneAndTwoHourSchedulesScoresFiftyPercentInEitherOrder() {
		StudentDto twoHourSchedule = studentWithSlot("MONDAY", "18:00", "20:00");
		StudentDto oneHourSchedule = studentWithSlot("MONDAY", "18:00", "19:00");

		assertEquals(0.5, matchingEngine.scoreAvailabilityOverlap(twoHourSchedule, oneHourSchedule));
		assertEquals(0.5, matchingEngine.scoreAvailabilityOverlap(oneHourSchedule, twoHourSchedule));
	}

	@Test
	void availabilityOnDifferentDaysDoesNotOverlap() {
		StudentDto student = studentWithSlot("MONDAY", "18:00", "19:00");
		StudentDto candidate = studentWithSlot("TUESDAY", "18:00", "19:00");

		assertEquals(0.0, matchingEngine.scoreAvailabilityOverlap(student, candidate));
	}

	private StudentDto studentWithSlot(String day, String start, String end) {
		StudentDto student = new StudentDto();
		student.setWeeklyAvailability(List.of(new TimeSlot(
				day, LocalTime.parse(start), LocalTime.parse(end))));
		return student;
	}
}
