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

	public List<StudyBuddyConnection> findAll() {
		// TODO: implement
		return List.of();
	}

	public Optional<StudyBuddyConnection> findById(String connectionId) {
		// TODO: implement
		return Optional.empty();
	}

	public List<StudyBuddyConnection> findActiveByStudentId(String studentId) {
		// TODO: implement
		return List.of();
	}

	public StudyBuddyConnection save(StudyBuddyConnection connection) {
		// TODO: implement
		return connection;
	}
}
