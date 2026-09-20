package com.studybuddy.user.repository;

import com.studybuddy.user.model.Student;
import com.studybuddy.user.model.User;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Storage for every account, students and administrators alike.
 *
 * One repository rather than one per subtype, so an account and its profile
 * cannot get out of step - deleting an account cannot leave an orphan behind.
 *
 * Nothing is stored yet. Each method is a stub. When persistence is added this
 * becomes a Spring Data JPA interface and the method names stay the same, so
 * the service layer above does not change.
 */
@Repository
public class UserRepository {

	public List<User> findAll() {
		// TODO: implement
		return List.of();
	}

	public Optional<User> findById(String userId) {
		// TODO: implement
		return Optional.empty();
	}

	/**
	 * Only the accounts that are students, for profile browsing and matching.
	 */
	public List<Student> findAllStudents() {
		// TODO: implement
		return List.of();
	}

	public Optional<Student> findStudentById(String userId) {
		// TODO: implement
		return Optional.empty();
	}

	public User save(User user) {
		// TODO: implement - generate an id when the user does not have one
		return user;
	}

	public boolean deleteById(String userId) {
		// TODO: implement
		return false;
	}
}
