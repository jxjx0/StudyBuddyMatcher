package com.studybuddy.connection.repository;

import com.studybuddy.connection.model.StudyBuddyConnection;
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
public class StudyBuddyConnectionRepository {
	private final JdbcTemplate jdbc;

	private final RowMapper<StudyBuddyConnection> rowMapper = this::mapConnection;

	public StudyBuddyConnectionRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public List<StudyBuddyConnection> findAll() {
		return jdbc.query(
				"SELECT * FROM study_buddy_connections ORDER BY started_at DESC, connection_id",
				rowMapper);
	}

	public Optional<StudyBuddyConnection> findById(String connectionId) {
		if (connectionId == null) return Optional.empty();
		List<StudyBuddyConnection> connections = jdbc.query(
				"SELECT * FROM study_buddy_connections WHERE connection_id = ?",
				rowMapper, UUID.fromString(connectionId));
		return connections.stream().findFirst();
	}

	public List<StudyBuddyConnection> findActiveByStudentId(String studentId) {
		if (studentId == null) return List.of();
		return jdbc.query("""
				SELECT * FROM study_buddy_connections
				WHERE active = true AND (student_a_id = ? OR student_b_id = ?)
				ORDER BY started_at DESC, connection_id
				""",
				rowMapper, UUID.fromString(studentId), UUID.fromString(studentId));
	}

	public StudyBuddyConnection save(StudyBuddyConnection connection) {
		if (connection == null) return null;
		if (connection.getConnectionId() == null || connection.getConnectionId().isBlank()) {
			connection.setConnectionId(UUID.randomUUID().toString());
		}
		jdbc.update("""
				INSERT INTO study_buddy_connections
					(connection_id, student_a_id, student_b_id, started_at, ended_at, active)
				VALUES (?, ?, ?, ?, ?, ?)
				ON CONFLICT (connection_id) DO UPDATE SET
					student_a_id = EXCLUDED.student_a_id,
					student_b_id = EXCLUDED.student_b_id,
					started_at = EXCLUDED.started_at,
					ended_at = EXCLUDED.ended_at,
					active = EXCLUDED.active
				""",
				UUID.fromString(connection.getConnectionId()),
				UUID.fromString(connection.getStudentAId()),
				UUID.fromString(connection.getStudentBId()),
				toTimestamp(connection.getStartedAt()),
				toTimestamp(connection.getEndedAt()),
				connection.isActive());
		return connection;
	}

	private StudyBuddyConnection mapConnection(ResultSet resultSet, int rowNumber) throws SQLException {
		Timestamp endedAt = resultSet.getTimestamp("ended_at");
		return StudyBuddyConnection.restore(
				resultSet.getString("connection_id"),
				resultSet.getString("student_a_id"),
				resultSet.getString("student_b_id"),
				toLocalDateTime(resultSet.getTimestamp("started_at")),
				toLocalDateTime(endedAt),
				resultSet.getBoolean("active"));
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
