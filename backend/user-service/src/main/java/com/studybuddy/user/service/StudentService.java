package com.studybuddy.user.service;

import com.studybuddy.user.dto.CourseDto;
import com.studybuddy.user.dto.StudentContactDto;
import com.studybuddy.user.dto.StudentDto;
import com.studybuddy.user.dto.StudentRequest;
import com.studybuddy.user.model.AccountStatus;
import com.studybuddy.user.model.Course;
import com.studybuddy.user.model.Student;
import com.studybuddy.user.model.User;
import com.studybuddy.user.repository.CourseRepository;
import com.studybuddy.user.repository.StudentRepository;
import com.studybuddy.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class StudentService {
	private final UserRepository userRepository;
	private final CourseRepository courseRepository;
	private final UserAccountService accounts;
	private final StudentRepository studentRepository;

	public StudentService(UserRepository userRepository, CourseRepository courseRepository,
			UserAccountService accounts, StudentRepository studentRepository) {
		this.userRepository = userRepository;
		this.courseRepository = courseRepository;
		this.accounts = accounts;
		this.studentRepository = studentRepository;
	}

	public List<StudentDto> findAll() {
		return studentRepository.findAll().stream().map(this::toDto).toList();
	}

	@Transactional
	public StudentDto findById(String userId) {
		return toDto(findStudent(userId));
	}

	@Transactional
	public StudentContactDto findContactDetails(String userId) {
		Student student = findStudent(userId);
		User account = userRepository.findById(userId)
				.orElseThrow(() -> new AccountNotFoundException("Student not found"));
		return new StudentContactDto(student.getUserId(), student.getName(), account.getEmail(),
				student.getContactNumber());
	}

	//@Transactional = "undo everything if something goes wrong" 
	@Transactional
	public StudentDto create(StudentRequest request) {
		Student student = new Student(null, request.getEmail(), request.getPassword(), AccountStatus.ACTIVE);
		apply(student, request);
		Student created = (Student) accounts.create(student);
		return toDto(created);
	}

	@Transactional
	public StudentDto update(String userId, StudentRequest request) {
		Student student = findStudent(userId);
		if (request.getEmail() != null && !request.getEmail().isBlank())
			student.setEmail(request.getEmail());
		apply(student, request);
		return toDto(save(student));
	}

	// Profiles are kept in both user_accounts.profile_data and the students table for now.
	// Callers are @Transactional, so if either write fails neither is kept.
	// E.g. if the write 1 were to fail, the write 2 would be rolled back, and vice versa.
	private Student save(Student student) {
		// Students loaded from the students table carry no password hash, and UserRepository.save
		// writes every account column, so copy the stored hash across before saving.
		User account = userRepository.findById(student.getUserId())
				.orElseThrow(() -> new AccountNotFoundException("Student not found"));
		student.setPasswordHash(account.getPasswordHash());
		Student saved = (Student) userRepository.save(student);
		studentRepository.update(saved);
		return saved;
	}

	private Student findStudent(String userId) {
		return studentRepository.findById(userId).orElseGet(() -> {
			Student account = userRepository.findStudentById(userId)
					.orElseThrow(() -> new AccountNotFoundException("Student not found"));
			ensureRequiredProfileFields(account);
			if (studentRepository.saveIfAbsent(account)) userRepository.save(account);
			return studentRepository.findById(userId)
					.orElseThrow(() -> new AccountNotFoundException("Student profile could not be created"));
		});
	}

	private static void ensureRequiredProfileFields(Student student) {
		if (student.getName() == null || student.getName().isBlank() || student.getName().length() > 100)
			student.setName("Student");
		if (student.getSchool() == null || student.getSchool().isBlank() || student.getSchool().length() > 200)
			student.setSchool("Undeclared");
		if (student.getProgramme() == null || student.getProgramme().isBlank() || student.getProgramme().length() > 200)
			student.setProgramme("Undeclared");
		if (student.getYearOfStudy() < 1 || student.getYearOfStudy() > 5)
			student.setYearOfStudy(1);
	}

	// apply() copies the data from a StudentRequest (the incoming JSON) onto a Student object.
	private void apply(Student student, StudentRequest request) {
		student.updateProfile(request.getName(), request.getSchool(), request.getProgramme(),
				request.getYearOfStudy(), request.getContactNumber());
		student.setMeetingMode(request.getMeetingMode());
		// Left out: a new student has no preference (null) and an update keeps the current value.
		if (request.getGroupSizePreference() != null)
			student.setGroupSizePreference(request.getGroupSizePreference());
		if (request.getStudyGoals() != null) {
			student.getStudyGoals().clear();
			student.getStudyGoals().addAll(request.getStudyGoals());
		}
		// Unknown course codes are rejected; addCourse ignores duplicates.
		if (request.getCurrentCourseCodes() != null) {
			student.getCurrentCourses().clear();
			for (String code : request.getCurrentCourseCodes()) {
				student.addCourse(findCourse(code));
			}
		}
		// Courses for studying must be current courses, so they are applied after the current courses.
		// The rule is checked on the code first, so a code that is not a current course gets that
		// message even if the course does not exist at all.
		if (request.getCoursesForStudyingCodes() != null) {
			student.getCoursesForStudying().clear();
			for (String code : request.getCoursesForStudyingCodes()) {
				if (!student.takesCourse(code))
					throw new IllegalArgumentException(code + " is not one of your current courses");
				student.addCourseForStudying(findCourse(code));
			}
		} else {
			// The current courses may have changed: drop study courses the student no longer takes.
			student.getCoursesForStudying().removeIf(course -> !student.takesCourse(course));
		}
		if (request.getWeeklyAvailability() != null) {
			student.getWeeklyAvailability().clear();
			student.getWeeklyAvailability().addAll(request.getWeeklyAvailability());
		}
	}

	@Transactional
	public StudentDto addCourse(String userId, String courseCode) {
		Student student = findStudent(userId);
		student.addCourse(findCourse(courseCode));
		return toDto(save(student));
	}

	@Transactional
	public StudentDto removeCourse(String userId, String courseCode) {
		Student student = findStudent(userId);
		student.removeCourse(findCourse(courseCode));
		return toDto(save(student));
	}

	@Transactional
	public StudentDto addCourseForStudying(String userId, String courseCode) {
		Student student = findStudent(userId);
		student.addCourseForStudying(findCourse(courseCode));
		return toDto(save(student));
	}

	@Transactional
	public StudentDto removeCourseForStudying(String userId, String courseCode) {
		Student student = findStudent(userId);
		student.removeCourseForStudying(findCourse(courseCode));
		return toDto(save(student));
	}

	private Course findCourse(String courseCode) {
		return courseRepository.findByCode(courseCode.trim().toUpperCase())
				.orElseThrow(() -> new AccountNotFoundException("Course " + courseCode.trim() + " does not exist"));
	}

	private StudentDto toDto(Student student) {
		StudentDto dto = new StudentDto();
		dto.setUserId(student.getUserId());
		dto.setName(student.getName());
		dto.setSchool(student.getSchool());
		dto.setProgramme(student.getProgramme());
		dto.setYearOfStudy(student.getYearOfStudy());
		dto.setAccountStatus(student.getAccountStatus());
		dto.setMeetingMode(student.getMeetingMode());
		dto.setGroupSizePreference(student.getGroupSizePreference());
		dto.setStudyGoals(List.copyOf(student.getStudyGoals()));
		dto.setWeeklyAvailability(List.copyOf(student.getWeeklyAvailability()));
		dto.setCurrentCourses(student.getCurrentCourses().stream()
				.map(c -> new CourseDto(c.getCourseId(), c.getCourseCode(), c.getCourseName())).toList());
		dto.setCoursesForStudying(student.getCoursesForStudying().stream()
				.map(c -> new CourseDto(c.getCourseId(), c.getCourseCode(), c.getCourseName())).toList());
		return dto;
	}
}
