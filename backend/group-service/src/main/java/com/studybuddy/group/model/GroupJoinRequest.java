package com.studybuddy.group.model;

import java.time.LocalDateTime;

/**
 * A student's request to join a study group, decided by the group leader.
 */
public class GroupJoinRequest {

	private String groupJoinRequestId;
	private String studentId;
	private String groupId;
	private GroupJoinRequestStatus status = GroupJoinRequestStatus.PENDING;
	private LocalDateTime createdAt = LocalDateTime.now();
	private LocalDateTime respondedAt;

	public GroupJoinRequest() {
	}

	public String getGroupJoinRequestId() {
		return groupJoinRequestId;
	}

	public void setGroupJoinRequestId(String groupJoinRequestId) {
		this.groupJoinRequestId = groupJoinRequestId;
	}

	public String getStudentId() {
		return studentId;
	}

	public void setStudentId(String studentId) {
		this.studentId = studentId;
	}

	public String getGroupId() {
		return groupId;
	}

	public void setGroupId(String groupId) {
		this.groupId = groupId;
	}

	public GroupJoinRequestStatus getStatus() {
		return status;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getRespondedAt() {
		return respondedAt;
	}

	/**
	 * Marks this request accepted. Adding the student to the group is the
	 * service's job - a request should not be mutating a different object.
	 */
	public void accept() {
		// TODO: implement - set status to ACCEPTED and stamp respondedAt
	}

	public void reject() {
		// TODO: implement - set status to REJECTED and stamp respondedAt
	}

	public boolean isPending() {
		// TODO: implement
		return false;
	}
}
