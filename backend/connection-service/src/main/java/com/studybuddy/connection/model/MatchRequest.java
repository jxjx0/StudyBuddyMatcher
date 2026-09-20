package com.studybuddy.connection.model;

import java.time.LocalDateTime;

/**
 * A study buddy request from one student to another. Accepting it creates a
 * StudyBuddyConnection and makes contact numbers available to both sides.
 *
 * The UML holds Student objects for sender and recipient. Students live in
 * user-service, a different program, so this holds their ids instead and
 * fetches details over HTTP when they are needed.
 */
public class MatchRequest {

	private String requestId;
	private String senderId;
	private String recipientId;
	private String message;
	private MatchRequestStatus status = MatchRequestStatus.PENDING;
	private LocalDateTime createdAt = LocalDateTime.now();
	private LocalDateTime respondedAt;

	public MatchRequest() {
	}

	public String getRequestId() {
		return requestId;
	}

	public void setRequestId(String requestId) {
		this.requestId = requestId;
	}

	public String getSenderId() {
		return senderId;
	}

	public void setSenderId(String senderId) {
		this.senderId = senderId;
	}

	public String getRecipientId() {
		return recipientId;
	}

	public void setRecipientId(String recipientId) {
		this.recipientId = recipientId;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public MatchRequestStatus getStatus() {
		return status;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getRespondedAt() {
		return respondedAt;
	}

	/**
	 * Marks this request accepted. Creating the connection is the service's
	 * job, not this object's - a request cannot build and store another object.
	 */
	public void accept() {
		// TODO: implement - set status to ACCEPTED and stamp respondedAt
	}

	public void decline() {
		// TODO: implement - set status to DECLINED and stamp respondedAt
	}

	public boolean isPending() {
		// TODO: implement
		return false;
	}

	/**
	 * Only the recipient may accept or decline. Not in the UML, which has no
	 * way to express who is permitted to respond.
	 */
	public boolean canBeRespondedBy(String studentId) {
		// TODO: implement
		return false;
	}
}
