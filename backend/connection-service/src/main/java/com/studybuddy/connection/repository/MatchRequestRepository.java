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

	private final java.util.Map<String, MatchRequest> storage = new java.util.concurrent.ConcurrentHashMap<>();

	public List<MatchRequest> findAll() {
		return new java.util.ArrayList<>(storage.values());
	}

	public Optional<MatchRequest> findById(String requestId) {
		if (requestId == null) return Optional.empty();
		return Optional.ofNullable(storage.get(requestId));
	}

	public List<MatchRequest> findByRecipientId(String recipientId) {
		if (recipientId == null) return List.of();
		return storage.values().stream()
				.filter(req -> recipientId.equals(req.getRecipientId()))
				.collect(java.util.stream.Collectors.toList());
	}

	public MatchRequest save(MatchRequest request) {
		if (request == null) return null;
		if (request.getRequestId() == null || request.getRequestId().trim().isEmpty()) {
			request.setRequestId(java.util.UUID.randomUUID().toString());
		}
		storage.put(request.getRequestId(), request);
		return request;
	}
}
