package com.studybuddy.user.model;

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
	private GroupSizePreference groupSizePreference;
	private ArrayList<StudyGoal> studyGoals = new ArrayList<>();

	/**
	 * The UML draws this with no parameters, which cannot do anything. It takes
	 * the fields a student can edit.
	 */
	public void updateProfile(String name, String school, String programme,
							  int yearOfStudy, String contactNumber) {
		requireText(name, "name", 100);
		requireText(school, "school", 200);
		requireText(programme, "programme", 200);
		if (yearOfStudy < 1 || yearOfStudy > 5)
			throw new IllegalArgumentException("yearOfStudy must be between 1 and 5");
		if (contactNumber != null && contactNumber.length() > 32)
			throw new IllegalArgumentException("contactNumber must be at most 32 characters");
		this.name = name;
		this.school = school;
		this.programme = programme;
		this.yearOfStudy = yearOfStudy;
		this.contactNumber = contactNumber;
	}

	// Same limits as the students table and StudentRequest.
	private static void requireText(String value, String field, int maxLength) {
		if (value == null || value.isBlank())
			throw new IllegalArgumentException(field + " is required");
		if (value.length() > maxLength)
			throw new IllegalArgumentException(field + " must be at most " + maxLength + " characters");
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

	/** Rejects null and courses without a code; a course already in the list is not added again. */
	public void addCourse(Course course) {
		addIfAbsent(currentCourses, course);
	}

	/** Also drops the course from coursesForStudying, which may only hold current courses. */
	public void removeCourse(Course course) {
		removeMatching(currentCourses, course);
		removeMatching(coursesForStudying, course);
	}

	public ArrayList<Course> getCoursesForStudying() {
		return coursesForStudying;
	}

	/** Students can only look for study buddies in courses they are currently taking. */
	public void addCourseForStudying(Course course) {
		if (course != null && !takesCourse(course))
			throw new IllegalArgumentException(course.getCourseCode() + " is not one of your current courses");
		addIfAbsent(coursesForStudying, course);
	}

	/** Whether the course is one of the student's current courses. */
	public boolean takesCourse(Course course) {
		return currentCourses.stream().anyMatch(existing -> sameCourse(existing, course));
	}

	/** Whether a course with this code is one of the student's current courses (case is ignored). */
	public boolean takesCourse(String courseCode) {
		return courseCode != null
				&& currentCourses.stream().anyMatch(existing -> courseCode.trim().equalsIgnoreCase(existing.getCourseCode()));
	}

	public void removeCourseForStudying(Course course) {
		removeMatching(coursesForStudying, course);
	}

	// Course has no equals(), so courses are matched by their course code.
	private static boolean sameCourse(Course a, Course b) {
		return a.getCourseCode() != null && a.getCourseCode().equalsIgnoreCase(b.getCourseCode());
	}

	// addIfAbsent adds a course to a list only if the course is valid and not already in the list. 
	// Helper method.
	private static void addIfAbsent(ArrayList<Course> courses, Course course) {
		// Check if course is null, missing course code or course code is empty / only spaces
		if (course == null || course.getCourseCode() == null || course.getCourseCode().isBlank())
			throw new IllegalArgumentException("course with a course code is required");
		// Checking if existing course have the same code as the new one
		// If true, no existing course matches the new one
		if (courses.stream().noneMatch(existing -> sameCourse(existing, course)))
			courses.add(course);
	}

	// helper method with removal of course
	private static void removeMatching(ArrayList<Course> courses, Course course) {
		// Ignore null
		if (course == null) return;
		// goes through the list and deletes every course for which the lambda returns true
		courses.removeIf(existing -> sameCourse(existing, course));
	}

	public MeetingMode getMeetingMode() {
		return meetingMode;
	}

	public void setMeetingMode(MeetingMode mode) {
		this.meetingMode = mode;
	}

	// Jessica part
	public ArrayList<TimeSlot> getWeeklyAvailability() {
		return weeklyAvailability;
	}

	public void addTimeSlot(TimeSlot slot) {
		// TODO: implement
	}

	public void removeTimeSlot(TimeSlot slot) {
		// TODO: implement
	}
	// end of Jessica part
	
	public GroupSizePreference getGroupSizePreference() {
		return groupSizePreference;
	}

	public void setGroupSizePreference(GroupSizePreference preference) {
		this.groupSizePreference = preference;
	}

	public ArrayList<StudyGoal> getStudyGoals() {
		return studyGoals;
	}

	/** Rejects null; a goal already in the list is not added again. */
	public void addStudyGoal(StudyGoal goal) {
		if (goal == null)
			throw new IllegalArgumentException("study goal is required");
		if (!studyGoals.contains(goal))
			studyGoals.add(goal);
	}

	public void removeStudyGoal(StudyGoal goal) {
		studyGoals.remove(goal);
	}
}
