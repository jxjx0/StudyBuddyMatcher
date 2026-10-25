package com.studybuddy.connection.model;

import java.time.LocalDateTime;
import java.time.ZoneId;

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

	public static MatchRequest restore(String requestId, String senderId, String recipientId,
									   String message, MatchRequestStatus status,
									   LocalDateTime createdAt, LocalDateTime respondedAt) {
		MatchRequest request = new MatchRequest();
		request.requestId = requestId;
		request.senderId = senderId;
		request.recipientId = recipientId;
		request.message = message;
		request.status = status;
		request.createdAt = createdAt;
		request.respondedAt = respondedAt;
		return request;
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
		this.status = MatchRequestStatus.ACCEPTED;
		this.respondedAt = LocalDateTime.now();
	}

	public void decline() {
		this.status = MatchRequestStatus.DECLINED;
		this.respondedAt = LocalDateTime.now();
	}

	public void cancel() {
		this.status = MatchRequestStatus.CANCELLED;
		this.respondedAt = LocalDateTime.now(ZoneId.systemDefault());
	}

	public boolean isPending() {
		return MatchRequestStatus.PENDING.equals(this.status);
	}

	/**
	 * Only the recipient may accept or decline. Not in the UML, which has no
	 * way to express who is permitted to respond.
	 */
	public boolean canBeRespondedBy(String studentId) {
		return isPending() && studentId != null && studentId.equals(this.recipientId);
	}
}
