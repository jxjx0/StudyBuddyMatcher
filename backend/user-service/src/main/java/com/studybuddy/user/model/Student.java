package com.studybuddy.user.model;

import com.studybuddy.common.enums.MeetingMode;
import com.studybuddy.common.enums.StudyGoal;
import com.studybuddy.common.model.TimeSlot;

import java.util.ArrayList;

/**
 * A student profile together with the study preferences used for matching.
 */
public class Student extends User {

	private String name;
	private String school;
	private String programme;
	private int yearOfStudy;
	private String contactNumber;
	private ArrayList<Course> currentCourses = new ArrayList<>();
	private ArrayList<Course> coursesForStudying = new ArrayList<>();
	private MeetingMode meetingMode;
	private ArrayList<TimeSlot> weeklyAvailability = new ArrayList<>();
	private int groupSizePreference;
	private ArrayList<StudyGoal> studyGoals = new ArrayList<>();

	public Student() {
	}

	/**
	 * The UML draws this with no parameters, which cannot do anything. It takes
	 * the fields a student can edit.
	 */
	public void updateProfile(String name, String school, String programme,
							  int yearOfStudy, String contactNumber) {
		// TODO: implement
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

	/**
	 * Hidden from other students until a match request has been accepted, which
	 * is why it is absent from StudentDto.
	 */
	public String getContactNumber() {
		return contactNumber;
	}

	public void setContactNumber(String contactNumber) {
		this.contactNumber = contactNumber;
	}

	public ArrayList<Course> getCurrentCourses() {
		return currentCourses;
	}

	public void addCourse(Course course) {
		// TODO: implement
	}

	public void removeCourse(Course course) {
		// TODO: implement
	}

	public ArrayList<Course> getCoursesForStudying() {
		return coursesForStudying;
	}

	public void addCourseForStudying(Course course) {
		// TODO: implement
	}

	public void removeCourseForStudying(Course course) {
		// TODO: implement
	}

	public MeetingMode getMeetingMode() {
		return meetingMode;
	}

	public void setMeetingMode(MeetingMode mode) {
		this.meetingMode = mode;
	}

	public ArrayList<TimeSlot> getWeeklyAvailability() {
		return weeklyAvailability;
	}

	public void addTimeSlot(TimeSlot slot) {
		// TODO: implement
	}

	public void removeTimeSlot(TimeSlot slot) {
		// TODO: implement
	}

	public int getGroupSizePreference() {
		return groupSizePreference;
	}

	public void setGroupSizePreference(int size) {
		this.groupSizePreference = size;
	}

	public ArrayList<StudyGoal> getStudyGoals() {
		return studyGoals;
	}

	public void addStudyGoal(StudyGoal goal) {
		// TODO: implement
	}

	public void removeStudyGoal(StudyGoal goal) {
		// TODO: implement
	}
}
