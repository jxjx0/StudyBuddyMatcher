package com.studybuddy.group.controller;

import com.studybuddy.group.model.GroupJoinRequest;
import com.studybuddy.group.model.StudyGroup;
import com.studybuddy.group.service.StudyGroupService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/groups")
public class StudyGroupController {

	private final StudyGroupService studyGroupService;

	public StudyGroupController(StudyGroupService studyGroupService) {
		this.studyGroupService = studyGroupService;
	}

	@GetMapping
	public List<StudyGroup> findAll() {
		return studyGroupService.findAll();
	}

	@GetMapping("/{groupId}")
	public StudyGroup findById(@PathVariable String groupId) {
		return studyGroupService.findById(groupId);
	}

	@PostMapping
	public ResponseEntity<StudyGroup> create(@RequestParam String groupName,
											 @RequestParam String courseCode,
											 @RequestParam String leaderId,
											 @RequestParam(defaultValue = "5") int maxGroupSize) {
		StudyGroup created = studyGroupService.create(groupName, courseCode, leaderId, maxGroupSize);
		return ResponseEntity.status(HttpStatus.CREATED).body(created);
	}

	@PutMapping("/{groupId}")
	public StudyGroup updateGroupInfo(@PathVariable String groupId,
									  @RequestParam String leaderId,
									  @RequestParam String groupName,
									  @RequestParam(required = false) String description,
									  @RequestParam int maxGroupSize) {
		return studyGroupService.updateGroupInfo(groupId, leaderId, groupName, description, maxGroupSize);
	}

	@PostMapping("/{groupId}/close")
	public ResponseEntity<Void> close(@PathVariable String groupId, @RequestParam String leaderId) {
		studyGroupService.close(groupId, leaderId);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/{groupId}/members/{memberId}")
	public ResponseEntity<Void> removeMember(@PathVariable String groupId,
											 @PathVariable String memberId,
											 @RequestParam String leaderId) {
		studyGroupService.removeMember(groupId, leaderId, memberId);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/{groupId}/join-requests")
	public List<GroupJoinRequest> findJoinRequests(@PathVariable String groupId) {
		return studyGroupService.findJoinRequests(groupId);
	}

	@PostMapping("/{groupId}/join-requests")
	public ResponseEntity<GroupJoinRequest> requestToJoin(@PathVariable String groupId,
														  @RequestParam String studentId) {
		GroupJoinRequest created = studyGroupService.requestToJoin(groupId, studentId);
		return ResponseEntity.status(HttpStatus.CREATED).body(created);
	}

	@PostMapping("/join-requests/{requestId}/accept")
	public GroupJoinRequest acceptJoinRequest(@PathVariable String requestId,
											  @RequestParam String leaderId) {
		return studyGroupService.acceptJoinRequest(requestId, leaderId);
	}

	@PostMapping("/join-requests/{requestId}/reject")
	public GroupJoinRequest rejectJoinRequest(@PathVariable String requestId,
											  @RequestParam String leaderId) {
		return studyGroupService.rejectJoinRequest(requestId, leaderId);
	}
}
