package com.studybuddy.matching.model;

import java.util.HashMap;
import java.util.Map;

import com.studybuddy.matching.dto.CourseDto;

/**
 * What a student is searching for.
 *
 * The UML holds a Course object here. Across a service boundary that is not
 * possible, and this arrives as JSON from the browser, so it carries the
 * course code instead.
 */
public class SearchCriteria {

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
		this.filters = filters;
	}
}