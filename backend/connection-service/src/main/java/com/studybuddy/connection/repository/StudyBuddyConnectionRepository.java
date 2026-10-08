package com.studybuddy.connection.repository;

import com.studybuddy.connection.model.StudyBuddyConnection;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Storage for study buddy connections. Stubbed - nothing is stored yet.
 */
@Repository
public class StudyBuddyConnectionRepository {

	private final Map<String, StudyBuddyConnection> storage = new ConcurrentHashMap<>();

	public List<StudyBuddyConnection> findAll() {
		return new ArrayList<>(storage.values());
	}

	public Optional<StudyBuddyConnection> findById(String connectionId) {
		if (connectionId == null) return Optional.empty();
		return Optional.ofNullable(storage.get(connectionId));
	}

	public List<StudyBuddyConnection> findActiveByStudentId(String studentId) {
		if (studentId == null) return List.of();

		List<StudyBuddyConnection> allConnections = new ArrayList<>(storage.values());
		List<StudyBuddyConnection> activeConnections = new ArrayList<>();

		for (int i = 0; i < allConnections.size(); i++) {
			StudyBuddyConnection conn = allConnections.get(i);

			if (conn.isActive() && conn.involves(studentId)) {
				activeConnections.add(conn);
			}
		}
		return activeConnections;
	}

	public StudyBuddyConnection save(StudyBuddyConnection connection) {
		if (connection == null) return null;
		if (connection.getConnectionId() == null || connection.getConnectionId().trim().isEmpty()) {
			connection.setConnectionId(UUID.randomUUID().toString());
		}
		storage.put(connection.getConnectionId(), connection);
		return connection;
	}
}
