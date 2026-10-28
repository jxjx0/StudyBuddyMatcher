package com.studybuddy.connection.repository;

import com.studybuddy.connection.model.MatchRequest;
import com.studybuddy.connection.model.MatchRequestStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Storage for match requests backed by PostgreSQL.
 */
@Repository
public class MatchRequestRepository {

	private final JdbcTemplate jdbcTemplate;

	public MatchRequestRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	private final RowMapper<MatchRequest> rowMapper = new RowMapper<>() {
		@Override
		public MatchRequest mapRow(ResultSet rs, int rowNum) throws SQLException {
			MatchRequest req = new MatchRequest();
			req.setRequestId(rs.getString("request_id"));
			req.setSenderId(rs.getString("sender_id"));
			req.setRecipientId(rs.getString("recipient_id"));
			req.setMessage(rs.getString("message"));
			req.setStatus(MatchRequestStatus.valueOf(rs.getString("status")));

			Timestamp createdAt = rs.getTimestamp("created_at");
			if (createdAt != null) {
				req.setCreatedAt(createdAt.toLocalDateTime());
			}

			Timestamp respondedAt = rs.getTimestamp("responded_at");
			if (respondedAt != null) {
				req.setRespondedAt(respondedAt.toLocalDateTime());
			}
			return req;
		}
	};

	public List<MatchRequest> findAll() {
		return jdbcTemplate.query(
				"SELECT request_id, sender_id, recipient_id, message, status, created_at, responded_at FROM match_requests ORDER BY created_at DESC",
				rowMapper
		);
	}

	public Optional<MatchRequest> findById(String requestId) {
		if (requestId == null) return Optional.empty();
		try {
			UUID id = UUID.fromString(requestId);
			List<MatchRequest> results = jdbcTemplate.query(
					"SELECT request_id, sender_id, recipient_id, message, status, created_at, responded_at FROM match_requests WHERE request_id = ?",
					rowMapper,
					id
			);
			if (results.isEmpty()) {
				return Optional.empty();
			}
			return Optional.of(results.get(0));
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}

	public List<MatchRequest> findByRecipientId(String recipientId) {
		if (recipientId == null) return List.of();
		try {
			UUID id = UUID.fromString(recipientId);
			return jdbcTemplate.query(
					"SELECT request_id, sender_id, recipient_id, message, status, created_at, responded_at FROM match_requests WHERE recipient_id = ? ORDER BY created_at DESC",
					rowMapper,
					id
			);
		} catch (IllegalArgumentException e) {
			return List.of();
		}
	}

	public List<MatchRequest> findBySenderId(String senderId) {
		if (senderId == null) return List.of();
		try {
			UUID id = UUID.fromString(senderId);
			return jdbcTemplate.query(
					"SELECT request_id, sender_id, recipient_id, message, status, created_at, responded_at FROM match_requests WHERE sender_id = ? ORDER BY created_at DESC",
					rowMapper,
					id
			);
		} catch (IllegalArgumentException e) {
			return List.of();
		}
	}

	public int countPendingForRecipient(String recipientId) {
		if (recipientId == null) return 0;
		try {
			UUID id = UUID.fromString(recipientId);
			Integer count = jdbcTemplate.queryForObject(
					"SELECT COUNT(*) FROM match_requests WHERE recipient_id = ? AND status = 'PENDING'",
					Integer.class,
					id
			);
			return count != null ? count : 0;
		} catch (IllegalArgumentException e) {
			return 0;
		}
	}

	public MatchRequest save(MatchRequest request) {
		if (request == null) return null;
		if (request.getRequestId() == null || request.getRequestId().trim().isEmpty()) {
			request.setRequestId(UUID.randomUUID().toString());
		}

		Timestamp createdAt = request.getCreatedAt() != null ? Timestamp.valueOf(request.getCreatedAt()) : Timestamp.valueOf(java.time.LocalDateTime.now());
		Timestamp respondedAt = request.getRespondedAt() != null ? Timestamp.valueOf(request.getRespondedAt()) : null;

		jdbcTemplate.update(
				"""
				INSERT INTO match_requests (request_id, sender_id, recipient_id, message, status, created_at, responded_at)
				VALUES (?, ?, ?, ?, ?, ?, ?)
				ON CONFLICT (request_id) DO UPDATE
				SET status = EXCLUDED.status,
				    responded_at = EXCLUDED.responded_at,
				    message = EXCLUDED.message
				""",
				UUID.fromString(request.getRequestId()),
				UUID.fromString(request.getSenderId()),
				UUID.fromString(request.getRecipientId()),
				request.getMessage(),
				request.getStatus().name(),
				createdAt,
				respondedAt
		);

		return request;
	}
}
