package com.studybuddy.user.service;

import com.studybuddy.user.dto.CourseDto;
import com.studybuddy.user.dto.StudentContactDto;
import com.studybuddy.user.dto.StudentDto;
import com.studybuddy.user.dto.StudentRequest;
import com.studybuddy.user.model.Course;
import com.studybuddy.user.model.Student;
import com.studybuddy.user.repository.CourseRepository;
import com.studybuddy.user.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StudentService {
	private final UserRepository userRepository;
	private final CourseRepository courseRepository;
	private final UserAccountService accounts;
	public StudentService(UserRepository userRepository, CourseRepository courseRepository, UserAccountService accounts) {
		this.userRepository = userRepository; this.courseRepository = courseRepository; this.accounts = accounts;
	}
	public List<StudentDto> findAll() { return userRepository.findAllStudents().stream().map(this::toDto).toList(); }
	public StudentDto findById(String userId) { return toDto(findStudent(userId)); }
	public StudentContactDto findContactDetails(String userId) {
		Student student = findStudent(userId);
		return new StudentContactDto(student.getUserId(), student.getName(), student.getContactNumber());
	}
	public StudentDto create(StudentRequest request) {
		Student student = new Student();
		student.setEmail(request.getEmail()); student.setPasswordHash(request.getPassword());
		apply(student, request);
		return toDto((Student) accounts.create(student));
	}
	public StudentDto update(String userId, StudentRequest request) {
		Student student = findStudent(userId);
		if (request.getEmail() != null && !request.getEmail().isBlank()) student.setEmail(request.getEmail());
		apply(student, request);
		return toDto((Student) userRepository.save(student));
	}
	private Student findStudent(String userId) {
		return userRepository.findStudentById(userId).orElseThrow(() -> new AccountNotFoundException("Student not found"));
	}
	private void apply(Student student, StudentRequest request) {
		student.setName(request.getName()); student.setSchool(request.getSchool()); student.setProgramme(request.getProgramme());
		student.setYearOfStudy(request.getYearOfStudy()); student.setContactNumber(request.getContactNumber());
		student.setMeetingMode(request.getMeetingMode()); student.setGroupSizePreference(request.getGroupSizePreference());
		if (request.getStudyGoals() != null) { student.getStudyGoals().clear(); student.getStudyGoals().addAll(request.getStudyGoals()); }
		if (request.getCurrentCourseCodes() != null) setCourses(student.getCurrentCourses(), request.getCurrentCourseCodes());
		if (request.getCoursesForStudyingCodes() != null) setCourses(student.getCoursesForStudying(), request.getCoursesForStudyingCodes());
		if (request.getWeeklyAvailability() != null) { student.getWeeklyAvailability().clear(); student.getWeeklyAvailability().addAll(request.getWeeklyAvailability()); }
	}
	private void setCourses(List<Course> target, List<String> codes) {
		target.clear();
		codes.stream().map(courseRepository::findByCode).flatMap(java.util.Optional::stream).forEach(target::add);
	}
	private StudentDto toDto(Student student) {
		StudentDto dto = new StudentDto();
		dto.setUserId(student.getUserId()); dto.setName(student.getName()); dto.setSchool(student.getSchool());
		dto.setProgramme(student.getProgramme()); dto.setYearOfStudy(student.getYearOfStudy());
		dto.setAccountStatus(student.getAccountStatus()); dto.setMeetingMode(student.getMeetingMode());
		dto.setGroupSizePreference(student.getGroupSizePreference()); dto.setStudyGoals(List.copyOf(student.getStudyGoals()));
		dto.setWeeklyAvailability(List.copyOf(student.getWeeklyAvailability()));
		dto.setCurrentCourses(student.getCurrentCourses().stream().map(c -> new CourseDto(c.getCourseId(), c.getCourseCode(), c.getCourseName())).toList());
		dto.setCoursesForStudying(student.getCoursesForStudying().stream().map(c -> new CourseDto(c.getCourseId(), c.getCourseCode(), c.getCourseName())).toList());
		return dto;
	}
}
