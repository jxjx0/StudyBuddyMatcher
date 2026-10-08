package com.studybuddy.connection.repository;

import com.studybuddy.connection.model.MatchRequest;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Storage for match requests. Stubbed - nothing is stored yet.
 */
@Repository
public class MatchRequestRepository {

	private final Map<String, MatchRequest> storage = new ConcurrentHashMap<>();

	public List<MatchRequest> findAll() {
		return new ArrayList<>(storage.values());
	}

	public Optional<MatchRequest> findById(String requestId) {
		if (requestId == null) return Optional.empty();
		return Optional.ofNullable(storage.get(requestId));
	}

	public List<MatchRequest> findByRecipientId(String recipientId) {
		if (recipientId == null) return List.of();

		List<MatchRequest> allRequests = new ArrayList<>(storage.values());
		List<MatchRequest> matchingRequests = new ArrayList<>();

		for (int i = 0; i < allRequests.size(); i++) {
			MatchRequest req = allRequests.get(i);
			
			if (recipientId.equals(req.getRecipientId())) {
				matchingRequests.add(req);
			}
		}
		return matchingRequests;
	}

	public MatchRequest save(MatchRequest request) {
		if (request == null) return null;
		if (request.getRequestId() == null || request.getRequestId().trim().isEmpty()) {
			request.setRequestId(UUID.randomUUID().toString());
		}
		storage.put(request.getRequestId(), request);
		return request;
	}
}
