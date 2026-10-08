package com.studybuddy.connection.controller;

import com.studybuddy.connection.model.MatchRequest;
import com.studybuddy.connection.model.StudyBuddyConnection;
import com.studybuddy.connection.service.MatchRequestService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/match-requests")
public class MatchRequestController {

	private final MatchRequestService matchRequestService;

	public MatchRequestController(MatchRequestService matchRequestService) {
		this.matchRequestService = matchRequestService;
	}

	@GetMapping
	public List<MatchRequest> findAll() {
		return matchRequestService.findAll();
	}

	@GetMapping("/{requestId}")
	public MatchRequest findById(@PathVariable String requestId) {
		return matchRequestService.findById(requestId);
	}

	@GetMapping("/pending")
	public List<MatchRequest> findPending(@RequestParam String studentId) {
		return matchRequestService.findPendingFor(studentId);
	}

	@GetMapping("/outgoing-pending")
	public List<MatchRequest> findOutgoingPending(@RequestParam String studentId) {
		return matchRequestService.findOutgoingPendingFor(studentId);
	}

	@PostMapping
	public ResponseEntity<MatchRequest> send(@RequestParam String senderId,
											 @RequestParam String recipientId,
											 @RequestParam(required = false) String message) {
		MatchRequest created = matchRequestService.send(senderId, recipientId, message);
		return ResponseEntity.status(HttpStatus.CREATED).body(created);
	}

	@PostMapping("/{requestId}/accept")
	public StudyBuddyConnection accept(@PathVariable String requestId,
									   @RequestParam String respondentId) {
		return matchRequestService.accept(requestId, respondentId);
	}

	@PostMapping("/{requestId}/decline")
	public MatchRequest decline(@PathVariable String requestId,
								@RequestParam String respondentId) {
		return matchRequestService.decline(requestId, respondentId);
	}
}
