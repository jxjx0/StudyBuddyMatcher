package com.studybuddy.connection.repository;

import com.studybuddy.connection.model.StudyBuddyConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Storage for study buddy connections backed by PostgreSQL.
 */
@Repository
public class StudyBuddyConnectionRepository {

	private final JdbcTemplate jdbcTemplate;

	public StudyBuddyConnectionRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	private final RowMapper<StudyBuddyConnection> rowMapper = new RowMapper<>() {
		@Override
		public StudyBuddyConnection mapRow(ResultSet rs, int rowNum) throws SQLException {
			StudyBuddyConnection conn = new StudyBuddyConnection();
			conn.setConnectionId(rs.getString("connection_id"));
			conn.setStudentAId(rs.getString("student_a_id"));
			conn.setStudentBId(rs.getString("student_b_id"));
			conn.setActive(rs.getBoolean("active"));

			Timestamp startedAt = rs.getTimestamp("started_at");
			if (startedAt != null) {
				conn.setStartedAt(startedAt.toLocalDateTime());
			}

			Timestamp endedAt = rs.getTimestamp("ended_at");
			if (endedAt != null) {
				conn.setEndedAt(endedAt.toLocalDateTime());
			}
			return conn;
		}
	};

	public List<StudyBuddyConnection> findAll() {
		return jdbcTemplate.query(
				"SELECT connection_id, student_a_id, student_b_id, started_at, ended_at, active FROM study_buddy_connections ORDER BY started_at DESC",
				rowMapper
		);
	}

	public Optional<StudyBuddyConnection> findById(String connectionId) {
		if (connectionId == null) return Optional.empty();
		try {
			UUID id = UUID.fromString(connectionId);
			List<StudyBuddyConnection> results = jdbcTemplate.query(
					"SELECT connection_id, student_a_id, student_b_id, started_at, ended_at, active FROM study_buddy_connections WHERE connection_id = ?",
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

	public Optional<StudyBuddyConnection> findLatestByParticipants(String firstStudentId,
																   String secondStudentId) {
		if (firstStudentId == null || secondStudentId == null) return Optional.empty();
		try {
			UUID firstId = UUID.fromString(firstStudentId);
			UUID secondId = UUID.fromString(secondStudentId);
			List<StudyBuddyConnection> results = jdbcTemplate.query(
					"""
					SELECT connection_id, student_a_id, student_b_id, started_at, ended_at, active
					FROM study_buddy_connections
					WHERE (student_a_id = ? AND student_b_id = ?)
					   OR (student_a_id = ? AND student_b_id = ?)
					ORDER BY active DESC, started_at DESC
					LIMIT 1
					""",
					rowMapper,
					firstId,
					secondId,
					secondId,
					firstId
			);
			return results.stream().findFirst();
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}

	public List<StudyBuddyConnection> findActiveByStudentId(String studentId) {
		if (studentId == null) return List.of();
		try {
			UUID id = UUID.fromString(studentId);
			return jdbcTemplate.query(
					"SELECT connection_id, student_a_id, student_b_id, started_at, ended_at, active FROM study_buddy_connections WHERE active = true AND (student_a_id = ? OR student_b_id = ?) ORDER BY started_at DESC",
					rowMapper,
					id,
					id
			);
		} catch (IllegalArgumentException e) {
			return List.of();
		}
	}

	public StudyBuddyConnection save(StudyBuddyConnection connection) {
		if (connection == null) return null;
		if (connection.getConnectionId() == null || connection.getConnectionId().trim().isEmpty()) {
			connection.setConnectionId(UUID.randomUUID().toString());
		}

		Timestamp startedAt = connection.getStartedAt() != null ? Timestamp.valueOf(connection.getStartedAt()) : Timestamp.valueOf(java.time.LocalDateTime.now());
		Timestamp endedAt = connection.getEndedAt() != null ? Timestamp.valueOf(connection.getEndedAt()) : null;

		jdbcTemplate.update(
				"""
				INSERT INTO study_buddy_connections (connection_id, student_a_id, student_b_id, started_at, ended_at, active)
				VALUES (?, ?, ?, ?, ?, ?)
				ON CONFLICT (connection_id) DO UPDATE
				SET started_at = EXCLUDED.started_at,
				    active = EXCLUDED.active,
				    ended_at = EXCLUDED.ended_at
				""",
				UUID.fromString(connection.getConnectionId()),
				UUID.fromString(connection.getStudentAId()),
				UUID.fromString(connection.getStudentBId()),
				startedAt,
				endedAt,
				connection.isActive()
		);

		return connection;
	}
}
