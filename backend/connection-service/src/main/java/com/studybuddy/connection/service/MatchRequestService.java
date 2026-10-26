package com.studybuddy.connection.service;

import com.studybuddy.connection.client.StudentClient;
import com.studybuddy.connection.dto.StudentContactDto;
import com.studybuddy.connection.model.MatchRequest;
import com.studybuddy.connection.model.StudyBuddyConnection;
import com.studybuddy.connection.repository.MatchRequestRepository;
import com.studybuddy.connection.repository.StudyBuddyConnectionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
	private final StudentClient studentClient;

	public MatchRequestService(MatchRequestRepository matchRequestRepository,
							   StudyBuddyConnectionRepository connectionRepository,
							   StudentClient studentClient) {
		this.matchRequestRepository = matchRequestRepository;
		this.connectionRepository = connectionRepository;
		this.studentClient = studentClient;
	}

	public List<MatchRequest> findAll() {
		return matchRequestRepository.findAll();
	}

	public MatchRequest findById(String requestId) {
		return matchRequestRepository.findById(requestId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
						"MatchRequest not found with id: " + requestId));
	}

	public List<MatchRequest> findPendingFor(String studentId) {
		if (studentId == null) return List.of();

		List<MatchRequest> recipientRequests = matchRequestRepository.findByRecipientId(studentId);
		List<MatchRequest> pendingRequests = new ArrayList<>();

		for (int i = 0; i < recipientRequests.size(); i++) {
			MatchRequest req = recipientRequests.get(i);

			if (req.isPending()) {
				// Validate that sender still exists; if deleted, auto-decline orphan request
				try {
					studentClient.findStudent(req.getSenderId());
					pendingRequests.add(req);
				} catch (Exception e) {
					req.decline();
					matchRequestRepository.save(req);
				}
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
				// Validate that recipient still exists; if deleted, auto-decline orphan request
				try {
					studentClient.findStudent(req.getRecipientId());
					pendingRequests.add(req);
				} catch (Exception e) {
					req.decline();
					matchRequestRepository.save(req);
				}
			}
		}
		return pendingRequests;
	}

	public int countPendingFor(String studentId) {
		if (studentId == null) return 0;
		return findPendingFor(studentId).size();
	}

	@Transactional
	public MatchRequest send(String senderId, String recipientId, String message) {
		if (senderId == null || recipientId == null) {
			throw new IllegalArgumentException("Sender and recipient IDs must not be null");
		}
		if (senderId.equals(recipientId)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot send match request to yourself");
		}

		List<StudyBuddyConnection> activeConnections = connectionRepository.findActiveByStudentId(senderId);
		for (int i = 0; i < activeConnections.size(); i++) {
			if (activeConnections.get(i).involves(recipientId)) {
				throw new ResponseStatusException(HttpStatus.CONFLICT,
						"You are already connected with this study buddy");
			}
		}

		List<MatchRequest> incoming = matchRequestRepository.findByRecipientId(senderId);
		for (int i = 0; i < incoming.size(); i++) {
			MatchRequest req = incoming.get(i);
			if (recipientId.equals(req.getSenderId()) && req.isPending()) {
				req.accept();
				matchRequestRepository.save(req);
				createConnection(req.getSenderId(), req.getRecipientId());
				return req;
			}
		}

		List<MatchRequest> outgoing = matchRequestRepository.findBySenderId(senderId);
		for (int i = 0; i < outgoing.size(); i++) {
			MatchRequest req = outgoing.get(i);
			if (recipientId.equals(req.getRecipientId()) && req.isPending()) {
				throw new ResponseStatusException(HttpStatus.CONFLICT,
						"A pending match request has already been sent to this student");
			}
		}

		MatchRequest request = new MatchRequest();
		request.setSenderId(senderId);
		request.setRecipientId(recipientId);
		request.setMessage(message);
		return matchRequestRepository.save(request);
	}

	private StudyBuddyConnection createConnection(String studentAId, String studentBId) {
		StudyBuddyConnection connection = new StudyBuddyConnection();
		connection.setStudentAId(studentAId);
		connection.setStudentBId(studentBId);
		return connectionRepository.save(connection);
	}

	/**
	 * Accept a request and open the connection it produces.
	 */
	@Transactional
	public StudyBuddyConnection accept(String requestId, String respondentId) {
		MatchRequest request = findById(requestId);
		if (!request.canBeRespondedBy(respondentId)) {
			throw new IllegalStateException("Student " + respondentId + " is not authorized to respond to this request");
		}
		request.accept();
		matchRequestRepository.save(request);
		return createConnection(request.getSenderId(), request.getRecipientId());
	}

	@Transactional
	public MatchRequest decline(String requestId, String respondentId) {
		MatchRequest request = findById(requestId);
		if (!request.canBeRespondedBy(respondentId)) {
			throw new IllegalStateException("Student " + respondentId + " is not authorized to respond to this request");
		}
		request.decline();
		return matchRequestRepository.save(request);
	}

	@Transactional
	public MatchRequest cancel(String requestId, String senderId) {
		MatchRequest request = findById(requestId);
		if (!request.isPending() || senderId == null || !senderId.equals(request.getSenderId())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"Only the sender can cancel a pending match request");
		}
		request.cancel();
		return matchRequestRepository.save(request);
	}

	public List<StudyBuddyConnection> findActiveConnections(String studentId) {
		return connectionRepository.findActiveByStudentId(studentId);
	}

	public StudentContactDto findActiveConnectionContact(String connectionId, String requesterId) {
		StudyBuddyConnection connection = connectionRepository.findById(connectionId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		if (!connection.isActive() || !connection.involves(requesterId)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		}
		String otherStudentId = connection.getOtherStudentId(requesterId);
		if (otherStudentId == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		try {
			return studentClient.findContactDetails(otherStudentId);
		} catch (Exception e) {
			return new StudentContactDto(otherStudentId, "Former Student", "", "");
		}
	}

	@Transactional
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
