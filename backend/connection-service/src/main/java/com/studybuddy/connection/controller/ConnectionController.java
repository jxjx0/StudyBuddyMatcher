package com.studybuddy.connection.controller;

import com.studybuddy.connection.dto.StudentContactDto;
import com.studybuddy.connection.dto.ChatMessage;
import com.studybuddy.connection.model.StudyBuddyConnection;
import com.studybuddy.connection.service.ChatService;
import com.studybuddy.connection.service.MatchRequestService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Viewing and ending active study buddy connections.
 */
@RestController
@RequestMapping("/api/connections")
public class ConnectionController {

	private final MatchRequestService matchRequestService;
	private final ChatService chatService;

	public ConnectionController(MatchRequestService matchRequestService, ChatService chatService) {
		this.matchRequestService = matchRequestService;
		this.chatService = chatService;
	}

	@GetMapping
	public List<StudyBuddyConnection> findActive(@RequestParam(required = false) String studentId,
												@AuthenticationPrincipal Jwt jwt) {
		String authenticatedId = jwt.getClaimAsString("uid");
		String targetId = (studentId != null && !studentId.isBlank()) ? studentId : authenticatedId;
		if (authenticatedId == null || !authenticatedId.equals(targetId)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only view your own active connections");
		}
		return matchRequestService.findActiveConnections(authenticatedId);
	}

	@GetMapping("/{connectionId}/contact")
	public StudentContactDto findPartnerContact(@PathVariable String connectionId,
												@AuthenticationPrincipal Jwt jwt) {
		String authenticatedId = jwt.getClaimAsString("uid");
		if (authenticatedId == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User is not authenticated");
		}
		return matchRequestService.findActiveConnectionContact(connectionId, authenticatedId);
	}

	@GetMapping("/{connectionId}/messages")
	public List<ChatMessage> findMessages(@PathVariable String connectionId,
										  @AuthenticationPrincipal Jwt jwt) {
		return chatService.history(connectionId, jwt.getClaimAsString("uid"));
	}

	@DeleteMapping("/{connectionId}")
	public ResponseEntity<Void> end(@PathVariable String connectionId,
									@RequestParam(required = false) String requesterId,
									@AuthenticationPrincipal Jwt jwt) {
		String authenticatedId = jwt.getClaimAsString("uid");
		String targetId = (requesterId != null && !requesterId.isBlank()) ? requesterId : authenticatedId;
		if (authenticatedId == null || !authenticatedId.equals(targetId)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only end your own connection");
		}
		matchRequestService.endConnection(connectionId, authenticatedId);
		return ResponseEntity.noContent().build();
	}
}
