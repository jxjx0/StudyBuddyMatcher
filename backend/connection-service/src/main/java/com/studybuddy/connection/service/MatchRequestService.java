package com.studybuddy.connection.service;

import com.studybuddy.connection.model.MatchRequest;
import com.studybuddy.connection.model.StudyBuddyConnection;
import com.studybuddy.connection.repository.MatchRequestRepository;
import com.studybuddy.connection.repository.StudyBuddyConnectionRepository;
import org.springframework.stereotype.Service;

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
		// TODO: implement
		return List.of();
	}

	public MatchRequest findById(String requestId) {
		// TODO: implement - throw when absent
		return null;
	}

	public List<MatchRequest> findPendingFor(String studentId) {
		// TODO: implement
		return List.of();
	}

	public MatchRequest send(String senderId, String recipientId, String message) {
		// TODO: implement
		return null;
	}

	/**
	 * Accept a request and open the connection it produces.
	 */
	public StudyBuddyConnection accept(String requestId, String respondentId) {
		// TODO: implement - check canBeRespondedBy, accept the request, then
		// build and save the connection
		return null;
	}

	public MatchRequest decline(String requestId, String respondentId) {
		// TODO: implement - check canBeRespondedBy first
		return null;
	}

	public List<StudyBuddyConnection> findActiveConnections(String studentId) {
		// TODO: implement
		return List.of();
	}

	public void endConnection(String connectionId, String requesterId) {
		// TODO: implement - only a student in the connection may end it
	}
}
