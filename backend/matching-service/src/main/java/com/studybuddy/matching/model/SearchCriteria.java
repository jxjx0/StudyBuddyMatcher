package com.studybuddy.matching.model;

/**
 * What a student is searching for.
 *
 * The UML holds a Course object here. Across a service boundary that is not
 * possible, and this arrives as JSON from the browser, so it carries the
 * course code instead.
 */
public class SearchCriteria {

	private String courseCode;
	private StudyGoal studyGoal;
	private MeetingMode meetingMode;
	private int minOverlapMinutes;

	public SearchCriteria() {
	}

	public String getCourseCode() {
		return courseCode;
	}

	public void setCourseCode(String courseCode) {
		this.courseCode = courseCode;
	}

	public StudyGoal getStudyGoal() {
		return studyGoal;
	}

	public void setStudyGoal(StudyGoal studyGoal) {
		this.studyGoal = studyGoal;
	}

	public MeetingMode getMeetingMode() {
		return meetingMode;
	}

	public void setMeetingMode(MeetingMode meetingMode) {
		this.meetingMode = meetingMode;
	}

	public int getMinOverlapMinutes() {
		return minOverlapMinutes;
	}

	public void setMinOverlapMinutes(int minOverlapMinutes) {
		this.minOverlapMinutes = minOverlapMinutes;
	}
}
