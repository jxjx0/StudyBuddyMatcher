package com.studybuddy.connection.repository;

import com.studybuddy.connection.model.MatchRequest;
import com.studybuddy.connection.model.MatchRequestStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MatchRequestRepository {
	private final JdbcTemplate jdbc;

	private final RowMapper<MatchRequest> rowMapper = this::mapRequest;

	public MatchRequestRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public List<MatchRequest> findAll() {
		return jdbc.query("SELECT * FROM match_requests ORDER BY created_at DESC, request_id", rowMapper);
	}

	public Optional<MatchRequest> findById(String requestId) {
		if (requestId == null) return Optional.empty();
		List<MatchRequest> requests = jdbc.query(
				"SELECT * FROM match_requests WHERE request_id = ?",
				rowMapper, UUID.fromString(requestId));
		return requests.stream().findFirst();
	}

	public List<MatchRequest> findByRecipientId(String recipientId) {
		if (recipientId == null) return List.of();
		return jdbc.query(
				"SELECT * FROM match_requests WHERE recipient_id = ? ORDER BY created_at DESC, request_id",
				rowMapper, UUID.fromString(recipientId));
	}

	public List<MatchRequest> findBySenderId(String senderId) {
		if (senderId == null) return List.of();
		return jdbc.query(
				"SELECT * FROM match_requests WHERE sender_id = ? ORDER BY created_at DESC, request_id",
				rowMapper, UUID.fromString(senderId));
	}

	public MatchRequest save(MatchRequest request) {
		if (request == null) return null;
		if (request.getRequestId() == null || request.getRequestId().isBlank()) {
			request.setRequestId(UUID.randomUUID().toString());
		}
		jdbc.update("""
				INSERT INTO match_requests
					(request_id, sender_id, recipient_id, message, status, created_at, responded_at)
				VALUES (?, ?, ?, ?, ?, ?, ?)
				ON CONFLICT (request_id) DO UPDATE SET
					sender_id = EXCLUDED.sender_id,
					recipient_id = EXCLUDED.recipient_id,
					message = EXCLUDED.message,
					status = EXCLUDED.status,
					created_at = EXCLUDED.created_at,
					responded_at = EXCLUDED.responded_at
				""",
				UUID.fromString(request.getRequestId()),
				UUID.fromString(request.getSenderId()),
				UUID.fromString(request.getRecipientId()),
				request.getMessage() == null ? "" : request.getMessage(),
				request.getStatus().name(),
				toTimestamp(request.getCreatedAt()),
				toTimestamp(request.getRespondedAt()));
		return request;
	}

	private MatchRequest mapRequest(ResultSet resultSet, int rowNumber) throws SQLException {
		return MatchRequest.restore(
				resultSet.getString("request_id"),
				resultSet.getString("sender_id"),
				resultSet.getString("recipient_id"),
				resultSet.getString("message"),
				MatchRequestStatus.valueOf(resultSet.getString("status")),
				toLocalDateTime(resultSet.getTimestamp("created_at")),
				toLocalDateTime(resultSet.getTimestamp("responded_at")));
	}

	private static Timestamp toTimestamp(LocalDateTime value) {
		return value == null ? null
				: Timestamp.from(value.atZone(ZoneId.systemDefault()).toInstant());
	}

	private static LocalDateTime toLocalDateTime(Timestamp value) {
		return value == null ? null
				: value.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
	}
}
