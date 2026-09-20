package com.studybuddy.group.service;

import com.studybuddy.group.model.GroupJoinRequest;
import com.studybuddy.group.model.StudyGroup;
import com.studybuddy.group.repository.GroupJoinRequestRepository;
import com.studybuddy.group.repository.StudyGroupRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Study groups and the requests to join them.
 *
 * Leader-only operations take the acting student's id so the service can check
 * StudyGroup.isLedBy before acting.
 */
@Service
public class StudyGroupService {

	private final StudyGroupRepository groupRepository;
	private final GroupJoinRequestRepository joinRequestRepository;

	public StudyGroupService(StudyGroupRepository groupRepository,
							 GroupJoinRequestRepository joinRequestRepository) {
		this.groupRepository = groupRepository;
		this.joinRequestRepository = joinRequestRepository;
	}

	public List<StudyGroup> findAll() {
		// TODO: implement
		return List.of();
	}

	public StudyGroup findById(String groupId) {
		// TODO: implement - throw when absent
		return null;
	}

	public StudyGroup create(String groupName, String courseCode, String leaderId, int maxGroupSize) {
		// TODO: implement - the leader joins as the first member
		return null;
	}

	public StudyGroup updateGroupInfo(String groupId, String leaderId, String groupName,
									  String description, int maxGroupSize) {
		// TODO: implement - check isLedBy first
		return null;
	}

	public void close(String groupId, String leaderId) {
		// TODO: implement - check isLedBy first
	}

	public void removeMember(String groupId, String leaderId, String memberId) {
		// TODO: implement - check isLedBy first
	}

	public GroupJoinRequest requestToJoin(String groupId, String studentId) {
		// TODO: implement - refuse when the group is full or closed
		return null;
	}

	public List<GroupJoinRequest> findJoinRequests(String groupId) {
		// TODO: implement
		return List.of();
	}

	public GroupJoinRequest acceptJoinRequest(String requestId, String leaderId) {
		// TODO: implement - check isLedBy, accept the request, add the member
		return null;
	}

	public GroupJoinRequest rejectJoinRequest(String requestId, String leaderId) {
		// TODO: implement - check isLedBy first
		return null;
	}
}
