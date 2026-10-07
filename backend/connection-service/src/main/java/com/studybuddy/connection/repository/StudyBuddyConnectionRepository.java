package com.studybuddy.connection.repository;

import com.studybuddy.connection.model.StudyBuddyConnection;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Storage for study buddy connections. Stubbed - nothing is stored yet.
 */
@Repository
public class StudyBuddyConnectionRepository {

	private final java.util.Map<String, StudyBuddyConnection> storage = new java.util.concurrent.ConcurrentHashMap<>();

	public List<StudyBuddyConnection> findAll() {
		return new java.util.ArrayList<>(storage.values());
	}

	public Optional<StudyBuddyConnection> findById(String connectionId) {
		if (connectionId == null) return Optional.empty();
		return Optional.ofNullable(storage.get(connectionId));
	}

	public List<StudyBuddyConnection> findActiveByStudentId(String studentId) {
		if (studentId == null) return List.of();
		return storage.values().stream()
				.filter(conn -> conn.isActive() && conn.involves(studentId))
				.collect(java.util.stream.Collectors.toList());
	}

	public StudyBuddyConnection save(StudyBuddyConnection connection) {
		if (connection == null) return null;
		if (connection.getConnectionId() == null || connection.getConnectionId().trim().isEmpty()) {
			connection.setConnectionId(java.util.UUID.randomUUID().toString());
		}
		storage.put(connection.getConnectionId(), connection);
		return connection;
	}
}
