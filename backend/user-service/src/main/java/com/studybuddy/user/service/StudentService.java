package com.studybuddy.user.service;

import com.studybuddy.user.dto.StudentContactDto;
import com.studybuddy.user.dto.StudentDto;
import com.studybuddy.user.dto.StudentRequest;
import com.studybuddy.user.repository.CourseRepository;
import com.studybuddy.user.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Student profiles and study preferences.
 *
 * Account concerns - creating, suspending, deleting - belong to
 * {@link UserAccountService}. This class is about what a student puts on their
 * profile.
 */
@Service
public class StudentService {

	private final UserRepository userRepository;
	private final CourseRepository courseRepository;

	public StudentService(UserRepository userRepository, CourseRepository courseRepository) {
		this.userRepository = userRepository;
		this.courseRepository = courseRepository;
	}

	/**
	 * Every student, contact numbers withheld.
	 */
	public List<StudentDto> findAll() {
		// TODO: implement - map userRepository.findAllStudents() to StudentDto
		return List.of();
	}

	/**
	 * One student's public profile. Never includes the contact number.
	 */
	public StudentDto findById(String userId) {
		// TODO: implement - decide how a missing record is reported (the team has
		// not yet agreed an exception + @RestControllerAdvice approach)
		return null;
	}

	/**
	 * Contact details, released only once a match request has been accepted.
	 * connection-service calls this after it accepts a request.
	 */
	public StudentContactDto findContactDetails(String userId) {
		// TODO: implement - verify the caller is entitled to see this
		return null;
	}

	public StudentDto create(StudentRequest request) {
		// TODO: implement - build a Student from the request and save it
		return null;
	}

	public StudentDto update(String userId, StudentRequest request) {
		// TODO: implement
		return null;
	}
}
