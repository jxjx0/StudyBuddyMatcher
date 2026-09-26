package com.studybuddy.group.model;

import java.util.ArrayList;

/**
 * A study group for one course, created and managed by a student leader.
 *
 * The leader and members are ids, not Student objects - students live in
 * user-service. Same for the course, which is a course code.
 *
 * Invariant: the leader is also in memberIds. So a group with maxGroupSize 4
 * holds the leader plus three others, and isFull compares memberIds.size()
 * against maxGroupSize with no adjustment. The UML leaves this ambiguous.
 */
public class StudyGroup {

	private String groupId;
	private String groupName;
	private String description;
	private String courseCode;
	private ArrayList<StudyGoal> studyGoals = new ArrayList<>();
	private MeetingMode meetingMode;
	private ArrayList<TimeSlot> availability = new ArrayList<>();
	private int maxGroupSize;
	private GroupStatus status = GroupStatus.ACTIVE;
	private String leaderId;
	private ArrayList<String> memberIds = new ArrayList<>();

	public StudyGroup() {
	}

	public String getGroupId() {
		return groupId;
	}

	public void setGroupId(String groupId) {
		this.groupId = groupId;
	}

	public String getGroupName() {
		return groupName;
	}

	public void setGroupName(String groupName) {
		this.groupName = groupName;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getCourseCode() {
		return courseCode;
	}

	public void setCourseCode(String courseCode) {
		this.courseCode = courseCode;
	}

	public MeetingMode getMeetingMode() {
		return meetingMode;
	}

	public void setMeetingMode(MeetingMode meetingMode) {
		this.meetingMode = meetingMode;
	}

	public int getMaxGroupSize() {
		return maxGroupSize;
	}

	public void setMaxGroupSize(int maxGroupSize) {
		this.maxGroupSize = maxGroupSize;
	}

	public GroupStatus getStatus() {
		return status;
	}

	public String getLeaderId() {
		return leaderId;
	}

	public void setLeaderId(String leaderId) {
		this.leaderId = leaderId;
	}

	public ArrayList<String> getMemberIds() {
		return memberIds;
	}

	public ArrayList<StudyGoal> getStudyGoals() {
		return studyGoals;
	}

	public ArrayList<TimeSlot> getAvailability() {
		return availability;
	}

	/**
	 * The UML draws this with no parameters, which cannot do anything.
	 */
	public void updateGroupInfo(String groupName, String description, int maxGroupSize) {
		// TODO: implement
	}

	public void addMember(String studentId) {
		// TODO: implement - refuse when the group is full or closed
	}

	public void removeMember(String studentId) {
		// TODO: implement - the leader cannot be removed
	}

	public void addStudyGoal(StudyGoal goal) {
		// TODO: implement
	}

	public void removeStudyGoal(StudyGoal goal) {
		// TODO: implement
	}

	public void addAvailability(TimeSlot slot) {
		// TODO: implement
	}

	public void removeAvailability(TimeSlot slot) {
		// TODO: implement
	}

	public boolean isFull() {
		// TODO: implement - memberIds includes the leader
		return false;
	}

	/**
	 * Only the leader may update, close, or remove members. Not in the UML,
	 * which has no way to express who is permitted to act.
	 */
	public boolean isLedBy(String studentId) {
		// TODO: implement
		return false;
	}

	public boolean hasMember(String studentId) {
		// TODO: implement
		return false;
	}

	public void close() {
		// TODO: implement - set status to CLOSED
	}

	public boolean isActive() {
		// TODO: implement
		return false;
	}
}
