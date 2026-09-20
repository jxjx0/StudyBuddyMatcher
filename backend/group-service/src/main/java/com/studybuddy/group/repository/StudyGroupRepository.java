package com.studybuddy.group.repository;

import com.studybuddy.group.model.StudyGroup;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Storage for study groups. Stubbed - nothing is stored yet.
 */
@Repository
public class StudyGroupRepository {

	public List<StudyGroup> findAll() {
		// TODO: implement
		return List.of();
	}

	public Optional<StudyGroup> findById(String groupId) {
		// TODO: implement
		return Optional.empty();
	}

	public List<StudyGroup> findByCourseCode(String courseCode) {
		// TODO: implement
		return List.of();
	}

	public StudyGroup save(StudyGroup group) {
		// TODO: implement - generate an id when the group does not have one
		return group;
	}
}
