package com.studybuddy.connection.controller;

import com.studybuddy.connection.dto.StudentContactDto;
import com.studybuddy.connection.model.StudyBuddyConnection;
import com.studybuddy.connection.service.MatchRequestService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Viewing and ending active study buddy connections.
 */
@RestController
@RequestMapping("/api/connections")
public class ConnectionController {

	private final MatchRequestService matchRequestService;

	public ConnectionController(MatchRequestService matchRequestService) {
		this.matchRequestService = matchRequestService;
	}

	@GetMapping
	public List<StudyBuddyConnection> findActive(@RequestParam String studentId) {
		return matchRequestService.findActiveConnections(studentId);
	}

	@GetMapping("/{connectionId}/contact")
	public StudentContactDto findPartnerContact(@PathVariable String connectionId,
												@AuthenticationPrincipal Jwt jwt) {
		return matchRequestService.findActiveConnectionContact(
				connectionId, jwt.getClaimAsString("uid"));
	}

	@DeleteMapping("/{connectionId}")
	public ResponseEntity<Void> end(@PathVariable String connectionId,
									@RequestParam String requesterId) {
		matchRequestService.endConnection(connectionId, requesterId);
		return ResponseEntity.noContent().build();
	}
}
