package com.studybuddy.connection.service;

import com.studybuddy.connection.model.MatchRequest;
import com.studybuddy.connection.model.StudyBuddyConnection;
import com.studybuddy.connection.repository.MatchRequestRepository;
import com.studybuddy.connection.repository.StudyBuddyConnectionRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Match requests and the connections they produce.
 *
 * The request object flips its own status; building and storing the resulting
 * connection happens here, because an object should not be reaching out to
 * create and persist a different object.
 */
@Service
public class MatchRequestService {

	private final MatchRequestRepository matchRequestRepository;
	private final StudyBuddyConnectionRepository connectionRepository;

	public MatchRequestService(MatchRequestRepository matchRequestRepository,
							   StudyBuddyConnectionRepository connectionRepository) {
		this.matchRequestRepository = matchRequestRepository;
		this.connectionRepository = connectionRepository;
	}

	public List<MatchRequest> findAll() {
		return matchRequestRepository.findAll();
	}

	public MatchRequest findById(String requestId) {
		return matchRequestRepository.findById(requestId)
				.orElseThrow(() -> new IllegalArgumentException("MatchRequest not found with id: " + requestId));
	}

	public List<MatchRequest> findPendingFor(String studentId) {
		if (studentId == null) return List.of();

		List<MatchRequest> recipientRequests = matchRequestRepository.findByRecipientId(studentId);
		List<MatchRequest> pendingRequests = new ArrayList<>();

		for (int i = 0; i < recipientRequests.size(); i++) {
			MatchRequest req = recipientRequests.get(i);
			
			if (req.isPending()) {
				pendingRequests.add(req);
			}
		}
		return pendingRequests;
	}

	public List<MatchRequest> findOutgoingPendingFor(String studentId) {
		if (studentId == null) return List.of();

		List<MatchRequest> senderRequests = matchRequestRepository.findBySenderId(studentId);
		List<MatchRequest> pendingRequests = new ArrayList<>();

		for (int i = 0; i < senderRequests.size(); i++) {
			MatchRequest req = senderRequests.get(i);

			if (req.isPending()) {
				pendingRequests.add(req);
			}
		}
		return pendingRequests;
	}

	public MatchRequest send(String senderId, String recipientId, String message) {
		if (senderId == null || recipientId == null) {
			throw new IllegalArgumentException("Sender and recipient IDs must not be null");
		}
		if (senderId.equals(recipientId)) {
			throw new IllegalArgumentException("Cannot send match request to yourself");
		}

		List<StudyBuddyConnection> activeConnections = connectionRepository.findActiveByStudentId(senderId);
		for (int i = 0; i < activeConnections.size(); i++) {
			if (activeConnections.get(i).involves(recipientId)) {
				throw new IllegalStateException("You are already connected with this study buddy");
			}
		}

		List<MatchRequest> outgoing = matchRequestRepository.findBySenderId(senderId);
		for (int i = 0; i < outgoing.size(); i++) {
			MatchRequest req = outgoing.get(i);
			if (recipientId.equals(req.getRecipientId()) && req.isPending()) {
				throw new IllegalStateException("A pending match request has already been sent to this student");
			}
		}

		List<MatchRequest> incoming = matchRequestRepository.findByRecipientId(senderId);
		for (int i = 0; i < incoming.size(); i++) {
			MatchRequest req = incoming.get(i);
			if (senderId.equals(req.getRecipientId()) && recipientId.equals(req.getSenderId()) && req.isPending()) {
				throw new IllegalStateException("This student has already sent you a match request. Check your incoming requests!");
			}
		}

		MatchRequest request = new MatchRequest();
		request.setSenderId(senderId);
		request.setRecipientId(recipientId);
		request.setMessage(message);
		return matchRequestRepository.save(request);
	}

	/**
	 * Accept a request and open the connection it produces.
	 */
	public StudyBuddyConnection accept(String requestId, String respondentId) {
		MatchRequest request = findById(requestId);
		if (!request.canBeRespondedBy(respondentId)) {
			throw new IllegalStateException("Student " + respondentId + " is not authorized to respond to this request");
		}
		request.accept();
		matchRequestRepository.save(request);

		StudyBuddyConnection connection = new StudyBuddyConnection();
		connection.setStudentAId(request.getSenderId());
		connection.setStudentBId(request.getRecipientId());
		return connectionRepository.save(connection);
	}

	public MatchRequest decline(String requestId, String respondentId) {
		MatchRequest request = findById(requestId);
		if (!request.canBeRespondedBy(respondentId)) {
			throw new IllegalStateException("Student " + respondentId + " is not authorized to respond to this request");
		}
		request.decline();
		return matchRequestRepository.save(request);
	}

	public List<StudyBuddyConnection> findActiveConnections(String studentId) {
		return connectionRepository.findActiveByStudentId(studentId);
	}

	public void endConnection(String connectionId, String requesterId) {
		StudyBuddyConnection connection = connectionRepository.findById(connectionId)
				.orElseThrow(() -> new IllegalArgumentException("Connection not found with id: " + connectionId));
		if (!connection.involves(requesterId)) {
			throw new IllegalStateException("Student " + requesterId + " is not part of this connection");
		}
		connection.end();
		connectionRepository.save(connection);
	}
}
