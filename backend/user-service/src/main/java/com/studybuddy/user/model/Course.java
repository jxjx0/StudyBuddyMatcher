package com.studybuddy.user.model;

import java.util.UUID;

public class Course {

	private UUID courseId;
	private String courseCode;
	private String courseName;

	public Course(){}

	public Course(String courseCode, String courseName) {
		this.courseCode = courseCode;
		this.courseName = courseName;
	}

	public Course(UUID courseId, String courseCode, String courseName) {
		this.courseId = courseId;
		this.courseCode = courseCode;
		this.courseName = courseName;
	}

	public UUID getCourseId() {
		return courseId;
	}

	public void setCourseId(UUID courseId) {
		this.courseId = courseId;
	}

	public String getCourseCode() {
		return courseCode;
	}

	public void setCourseCode(String courseCode) {
		this.courseCode = courseCode;
	}

	public String getCourseName() {
		return courseName;
	}

	public void setCourseName(String courseName) {
		this.courseName = courseName;
	}
}
