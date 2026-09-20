package com.studybuddy.group.repository;

import com.studybuddy.group.model.GroupJoinRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Storage for join requests. Stubbed - nothing is stored yet.
 */
@Repository
public class GroupJoinRequestRepository {

	public List<GroupJoinRequest> findAll() {
		// TODO: implement
		return List.of();
	}

	public Optional<GroupJoinRequest> findById(String requestId) {
		// TODO: implement
		return Optional.empty();
	}

	public List<GroupJoinRequest> findByGroupId(String groupId) {
		// TODO: implement
		return List.of();
	}

	public GroupJoinRequest save(GroupJoinRequest request) {
		// TODO: implement
		return request;
	}
}
