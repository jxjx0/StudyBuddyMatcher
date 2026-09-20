package com.studybuddy.connection.repository;

import com.studybuddy.connection.model.MatchRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Storage for match requests. Stubbed - nothing is stored yet.
 */
@Repository
public class MatchRequestRepository {

	public List<MatchRequest> findAll() {
		// TODO: implement
		return List.of();
	}

	public Optional<MatchRequest> findById(String requestId) {
		// TODO: implement
		return Optional.empty();
	}

	public List<MatchRequest> findByRecipientId(String recipientId) {
		// TODO: implement
		return List.of();
	}

	public MatchRequest save(MatchRequest request) {
		// TODO: implement - generate an id when the request does not have one
		return request;
	}
}
