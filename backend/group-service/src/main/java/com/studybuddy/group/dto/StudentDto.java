package com.studybuddy.group.dto;

import com.studybuddy.group.model.AccountStatus;
import com.studybuddy.group.model.MeetingMode;
import com.studybuddy.group.model.StudyGoal;
import com.studybuddy.group.model.TimeSlot;

import java.util.ArrayList;
import java.util.List;

/**
 * A student profile as received from user-service.
 *
 * Every service that handles student data keeps its own copy of this class.
 * The JSON field names are the contract between them, and user-service's copy
 * is the source of truth. A field renamed in one copy but not the others still
 * compiles - the value just arrives empty - so change all copies together.
 *
 * There is deliberately no contactNumber field. The brief requires the contact
 * number to stay hidden until a match request has been accepted, and leaving it
 * out of this type means no service can leak it by accident - the data simply
 * is not there. Contact details travel separately, and only to
 * connection-service.
 */
public class StudentDto {

	private String userId;
	private String name;
	private String school;
	private String programme;
	private int yearOfStudy;
	private AccountStatus accountStatus;
	private MeetingMode meetingMode;
	private int groupSizePreference;
	private List<StudyGoal> studyGoals = new ArrayList<>();
	private List<CourseDto> currentCourses = new ArrayList<>();
	private List<CourseDto> coursesForStudying = new ArrayList<>();
	private List<TimeSlot> weeklyAvailability = new ArrayList<>();

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

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

	public AccountStatus getAccountStatus() {
		return accountStatus;
	}

	public void setAccountStatus(AccountStatus accountStatus) {
		this.accountStatus = accountStatus;
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

	public List<CourseDto> getCurrentCourses() {
		return currentCourses;
	}

	public void setCurrentCourses(List<CourseDto> currentCourses) {
		this.currentCourses = currentCourses;
	}

	public List<CourseDto> getCoursesForStudying() {
		return coursesForStudying;
	}

	public void setCoursesForStudying(List<CourseDto> coursesForStudying) {
		this.coursesForStudying = coursesForStudying;
	}

	public List<TimeSlot> getWeeklyAvailability() {
		return weeklyAvailability;
	}

	public void setWeeklyAvailability(List<TimeSlot> weeklyAvailability) {
		this.weeklyAvailability = weeklyAvailability;
	}
}
