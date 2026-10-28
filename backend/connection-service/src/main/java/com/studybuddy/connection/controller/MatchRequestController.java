package com.studybuddy.connection.controller;

import com.studybuddy.connection.model.MatchRequest;
import com.studybuddy.connection.model.StudyBuddyConnection;
import com.studybuddy.connection.service.MatchRequestService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/match-requests")
public class MatchRequestController {

	private final MatchRequestService matchRequestService;

	public MatchRequestController(MatchRequestService matchRequestService) {
		this.matchRequestService = matchRequestService;
	}

	@GetMapping("/{requestId}")
	public MatchRequest findById(@PathVariable String requestId,
								 @AuthenticationPrincipal Jwt jwt) {
		MatchRequest request = matchRequestService.findById(requestId);
		String currentUserId = jwt.getClaimAsString("uid");
		if (currentUserId == null || (!currentUserId.equals(request.getSenderId()) && !currentUserId.equals(request.getRecipientId()))) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not authorized to view this match request");
		}
		return request;
	}

	@GetMapping("/pending")
	public List<MatchRequest> findPending(@RequestParam(required = false) String studentId,
										  @AuthenticationPrincipal Jwt jwt) {
		String authenticatedId = jwt.getClaimAsString("uid");
		String targetId = (studentId != null && !studentId.isBlank()) ? studentId : authenticatedId;
		if (authenticatedId == null || !authenticatedId.equals(targetId)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only view your own incoming requests");
		}
		return matchRequestService.findPendingFor(authenticatedId);
	}

	@GetMapping("/pending/count")
	public Map<String, Integer> countPending(@RequestParam(required = false) String studentId,
											 @AuthenticationPrincipal Jwt jwt) {
		String authenticatedId = jwt.getClaimAsString("uid");
		String targetId = (studentId != null && !studentId.isBlank()) ? studentId : authenticatedId;
		if (authenticatedId == null || !authenticatedId.equals(targetId)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only view your own incoming request count");
		}
		return Map.of("count", matchRequestService.countPendingFor(authenticatedId));
	}

	@GetMapping("/outgoing-pending")
	public List<MatchRequest> findOutgoingPending(@RequestParam(required = false) String studentId,
												  @AuthenticationPrincipal Jwt jwt) {
		String authenticatedId = jwt.getClaimAsString("uid");
		String targetId = (studentId != null && !studentId.isBlank()) ? studentId : authenticatedId;
		if (authenticatedId == null || !authenticatedId.equals(targetId)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only view your own outgoing requests");
		}
		return matchRequestService.findOutgoingPendingFor(authenticatedId);
	}

	@PostMapping
	public ResponseEntity<MatchRequest> send(@RequestParam(required = false) String senderId,
											 @RequestParam String recipientId,
											 @RequestParam(required = false) String message,
											 @AuthenticationPrincipal Jwt jwt) {
		String authenticatedId = jwt.getClaimAsString("uid");
		if (authenticatedId == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User is not authenticated");
		}
		MatchRequest created = matchRequestService.send(authenticatedId, recipientId, message);
		return ResponseEntity.status(HttpStatus.CREATED).body(created);
	}

	@PostMapping("/{requestId}/accept")
	public StudyBuddyConnection accept(@PathVariable String requestId,
									   @RequestParam(required = false) String respondentId,
									   @AuthenticationPrincipal Jwt jwt) {
		String authenticatedId = jwt.getClaimAsString("uid");
		if (authenticatedId == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User is not authenticated");
		}
		return matchRequestService.accept(requestId, authenticatedId);
	}

	@PostMapping("/{requestId}/decline")
	public MatchRequest decline(@PathVariable String requestId,
								@RequestParam(required = false) String respondentId,
								@AuthenticationPrincipal Jwt jwt) {
		String authenticatedId = jwt.getClaimAsString("uid");
		if (authenticatedId == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User is not authenticated");
		}
		return matchRequestService.decline(requestId, authenticatedId);
	}

	@DeleteMapping("/{requestId}")
	public MatchRequest cancel(@PathVariable String requestId,
							   @AuthenticationPrincipal Jwt jwt) {
		return matchRequestService.cancel(requestId, jwt.getClaimAsString("uid"));
	}
}
