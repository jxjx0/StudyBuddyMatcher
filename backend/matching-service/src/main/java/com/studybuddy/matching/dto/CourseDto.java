package com.studybuddy.matching.dto;

/**
 * A course as received from user-service. A copy - see {@link StudentDto}.
 */
public class CourseDto {

	private String courseCode;
	private String courseName;

	public CourseDto() {
	}

	public CourseDto(String courseCode, String courseName) {
		this.courseCode = courseCode;
		this.courseName = courseName;
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
