package com.studybuddy.connection.model;

import java.time.LocalDateTime;

/**
 * An accepted study buddy relationship between two students.
 */
public class StudyBuddyConnection {

	private String connectionId;
	private String studentAId;
	private String studentBId;
	private LocalDateTime startedAt = LocalDateTime.now();
	private LocalDateTime endedAt;
	private boolean active = true;

	public StudyBuddyConnection() {
	}

	public String getConnectionId() {
		return connectionId;
	}

	public void setConnectionId(String connectionId) {
		this.connectionId = connectionId;
	}

	public String getStudentAId() {
		return studentAId;
	}

	public void setStudentAId(String studentAId) {
		this.studentAId = studentAId;
	}

	public String getStudentBId() {
		return studentBId;
	}

	public void setStudentBId(String studentBId) {
		this.studentBId = studentBId;
	}

	public LocalDateTime getStartedAt() {
		return startedAt;
	}

	public void setStartedAt(LocalDateTime startedAt) {
		this.startedAt = startedAt;
	}

	public LocalDateTime getEndedAt() {
		return endedAt;
	}

	public void setEndedAt(LocalDateTime endedAt) {
		this.endedAt = endedAt;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public void end() {
		this.active = false;
		this.endedAt = LocalDateTime.now();
	}

	public boolean isActive() {
		return active;
	}

	public boolean involves(String studentId) {
		if (studentId == null) return false;
		return studentId.equals(studentAId) || studentId.equals(studentBId);
	}

	/**
	 * Given one side of the connection, returns the other.
	 */
	public String getOtherStudentId(String studentId) {
		if (studentId == null) return null;
		if (studentId.equals(studentAId)) return studentBId;
		if (studentId.equals(studentBId)) return studentAId;
		return null;
	}
}
