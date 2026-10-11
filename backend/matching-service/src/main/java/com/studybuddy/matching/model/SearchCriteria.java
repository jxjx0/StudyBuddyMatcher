package com.studybuddy.matching.model;

import java.util.HashMap;
import java.util.Map;

import com.studybuddy.matching.dto.CourseDto;

public class SearchCriteria {

	public static final String FILTER_MEETING_MODE = "meetingMode";
	public static final String FILTER_MIN_OVERLAP_MINUTES = "minOverlapMinutes";

	private CourseDto course;
	private StudyGoal studyGoal;
	private Map<String, Object> filters = new HashMap<>();

	public SearchCriteria() {
	}

	public CourseDto getCourse() {
		return course;
	}

	public void setCourse(CourseDto course) {
		this.course = course;
	}

	public StudyGoal getStudyGoal() {
		return studyGoal;
	}

	public void setStudyGoal(StudyGoal studyGoal) {
		this.studyGoal = studyGoal;
	}

	public Map<String, Object> getFilters() {
		return filters;
	}

	public void setFilters(Map<String, Object> filters) {
		this.filters = filters == null ? new HashMap<>() : new HashMap<>(filters);
	}
}