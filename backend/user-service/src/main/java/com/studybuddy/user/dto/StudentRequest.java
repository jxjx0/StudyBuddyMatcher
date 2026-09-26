package com.studybuddy.user.dto;

import com.studybuddy.user.model.MeetingMode;
import com.studybuddy.user.model.StudyGoal;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * Payload for creating or updating a student profile.
 */
public class StudentRequest {

	@NotBlank(message = "name is required")
	private String name;

	@NotBlank(message = "school is required")
	private String school;

	private String programme;

	@Min(value = 1, message = "yearOfStudy must be at least 1")
	private int yearOfStudy;

	private String contactNumber;
	private MeetingMode meetingMode;
	private int groupSizePreference;
	private List<StudyGoal> studyGoals;
	private List<String> currentCourseCodes;
	private List<String> coursesForStudyingCodes;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getSchool() {
		return school;
	}

	public void setSchool(String school) {
		this.school = school;
	}

	public String getProgramme() {
		return programme;
	}

	public void setProgramme(String programme) {
		this.programme = programme;
	}

	public int getYearOfStudy() {
		return yearOfStudy;
	}

	public void setYearOfStudy(int yearOfStudy) {
		this.yearOfStudy = yearOfStudy;
	}

	public String getContactNumber() {
		return contactNumber;
	}

	public void setContactNumber(String contactNumber) {
		this.contactNumber = contactNumber;
	}

	public MeetingMode getMeetingMode() {
		return meetingMode;
	}

	public void setMeetingMode(MeetingMode meetingMode) {
		this.meetingMode = meetingMode;
	}

	public int getGroupSizePreference() {
		return groupSizePreference;
	}

	public void setGroupSizePreference(int groupSizePreference) {
		this.groupSizePreference = groupSizePreference;
	}

	public List<StudyGoal> getStudyGoals() {
		return studyGoals;
	}

	public void setStudyGoals(List<StudyGoal> studyGoals) {
		this.studyGoals = studyGoals;
	}

	public List<String> getCurrentCourseCodes() {
		return currentCourseCodes;
	}

	public void setCurrentCourseCodes(List<String> currentCourseCodes) {
		this.currentCourseCodes = currentCourseCodes;
	}

	public List<String> getCoursesForStudyingCodes() {
		return coursesForStudyingCodes;
	}

	public void setCoursesForStudyingCodes(List<String> coursesForStudyingCodes) {
		this.coursesForStudyingCodes = coursesForStudyingCodes;
	}
}
