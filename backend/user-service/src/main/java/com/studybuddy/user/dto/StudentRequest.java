package com.studybuddy.user.dto;

import com.studybuddy.user.model.MeetingMode;
import com.studybuddy.user.model.StudyGoal;
// import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

import java.util.List;
import com.studybuddy.user.model.TimeSlot;

/**
 * Payload for creating or updating a student profile.
 */
public class StudentRequest {

	@NotBlank(message = "name is required")
	@Size(max = 100, message = "name must be at most 100 characters")
	private String name;
	@Email
	private String email;
	@Size(min = 8, message = "password must contain at least 8 characters")
	private String password;

	@NotBlank(message = "school is required")
	@Size(max = 200, message = "school must be at most 200 characters")
	private String school;

	@NotBlank(message = "programme is required")
	@Size(max = 200, message = "programme must be at most 200 characters")
	private String programme;

	@Min(value = 1, message = "yearOfStudy must be at least 1")
	@Max(value = 5, message = "yearOfStudy must be at most 5")
	private int yearOfStudy;

	@Size(max = 32, message = "contactNumber must be at most 32 characters")
	private String contactNumber;
	private MeetingMode meetingMode;

	@Min(value = 2, message = "groupSizePreference must be at least 2")
	@Max(value = 10, message = "groupSizePreference must be at most 10")
	private Integer groupSizePreference; // optional: null when left out

	private List<StudyGoal> studyGoals;
	private List<String> currentCourseCodes;
	private List<String> coursesForStudyingCodes;
	// private List<@Valid TimeSlot> weeklyAvailability; // can include it when validations are made for timeslot
	private List<TimeSlot> weeklyAvailability;

	public String getName() {
		return name;
	}

	public String getEmail() { return email; }
	public void setEmail(String email) { this.email = email; }
	public String getPassword() { return password; }
	public void setPassword(String password) { this.password = password; }

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

	public Integer getGroupSizePreference() {
		return groupSizePreference;
	}

	public void setGroupSizePreference(Integer groupSizePreference) {
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

	public List<TimeSlot> getWeeklyAvailability() { return weeklyAvailability; }
	public void setWeeklyAvailability(List<TimeSlot> weeklyAvailability) { this.weeklyAvailability = weeklyAvailability; }
}
