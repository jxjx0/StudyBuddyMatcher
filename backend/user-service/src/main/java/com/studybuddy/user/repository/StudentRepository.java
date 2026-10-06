package com.studybuddy.user.repository;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.studybuddy.user.model.AccountStatus;
import com.studybuddy.user.model.Course;
import com.studybuddy.user.model.GroupSizePreference;
import com.studybuddy.user.model.MeetingMode;
import com.studybuddy.user.model.Student;
import com.studybuddy.user.model.StudyGoal;
import com.studybuddy.user.model.TimeSlot;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * PostgreSQL storage for student profiles in the students table.
 *
 * For now profiles are also kept in user_accounts.profile_data by
 * UserRepository, which remains
 * the source the app reads from. The account itself (email, password, status)
 * lives only in
 * user_accounts, so a student must be saved there first: students.user_id
 * references it.
 */

// StudentRepository calls from students table
@Repository
public class StudentRepository {
	private final JdbcTemplate jdbc;
	private final ObjectMapper mapper;

	public StudentRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
		this.jdbc = jdbc;
		this.mapper = mapper;
	}

	/** Inserts a new profile; throws if this user already has one. */
	public void save(Student student) {
		try {
			jdbc.update("""
					INSERT INTO students(name, school, programme, year_of_study, contact_number, current_courses,
					 courses_for_studying, meeting_mode, weekly_availability, group_size_preference, study_goals, user_id)
					VALUES (?, ?, ?, ?, ?, CAST(? AS uuid[]), CAST(? AS uuid[]), ?, CAST(? AS jsonb), ?, CAST(? AS text[]), CAST(? AS uuid))
					""", values(student));
		} catch (DuplicateKeyException e) {
			throw new IllegalArgumentException("A student profile already exists for this user", e);
		}
	}

	/** Replaces an existing profile; returns false if this user has none yet. */
	public boolean update(Student student) {
		return jdbc.update("""
				UPDATE students SET name = ?, school = ?, programme = ?, year_of_study = ?, contact_number = ?,
				 current_courses = CAST(? AS uuid[]), courses_for_studying = CAST(? AS uuid[]), meeting_mode = ?,
				 weekly_availability = CAST(? AS jsonb), group_size_preference = ?, study_goals = CAST(? AS text[])
				WHERE user_id = CAST(? AS uuid)
				""", values(student)) > 0;
	}

	// Column values in the order both statements above use them, with user_id last.
	private Object[] values(Student student) {
		String availability;
		try {
			availability = mapper.writeValueAsString(student.getWeeklyAvailability());
		} catch (JacksonException e) {
			throw new IllegalStateException("Could not serialize weekly availability", e);
		}
		return new Object[] { student.getName(), student.getSchool(), student.getProgramme(),
				student.getYearOfStudy(), student.getContactNumber(), courseIds(student.getCurrentCourses()),
				courseIds(student.getCoursesForStudying()),
				student.getMeetingMode() == null ? null : student.getMeetingMode().name(), availability,
				student.getGroupSizePreference() == null ? null : student.getGroupSizePreference().name(),
				student.getStudyGoals().stream().map(StudyGoal::name).toArray(String[]::new), student.getUserId() };
	}

	// Email and account status come from user_accounts; the password hash is not loaded.
	private static final String SELECT_STUDENTS = """
			SELECT s.*, u.email, u.account_status
			FROM students s JOIN user_accounts u ON u.user_id = s.user_id
			""";

	/** All profiles in the students table, ordered by email like UserRepository.findAllStudents(). */
	public List<Student> findAll() {
		Map<UUID, Course> courses = allCourses();
		return jdbc.query(SELECT_STUDENTS + " ORDER BY u.email", (rs, row) -> mapStudent(rs, courses));
	}

	/** Loads a profile from the students table, with email and account status but no password hash. */
	public Optional<Student> findById(String userId) {
		try {
			UUID.fromString(userId);
		} catch (IllegalArgumentException | NullPointerException e) {
			return Optional.empty();
		}
		Map<UUID, Course> courses = allCourses();
		return jdbc.query(SELECT_STUDENTS + " WHERE s.user_id = CAST(? AS uuid)", (rs, row) -> mapStudent(rs, courses), userId)
				.stream().findFirst();
	}

	private Student mapStudent(ResultSet rs, Map<UUID, Course> courses) throws SQLException {
		Student student = new Student();
		student.setUserId(rs.getString("user_id"));
		student.setEmail(rs.getString("email"));
		student.setAccountStatus(AccountStatus.valueOf(rs.getString("account_status")));
		student.setName(rs.getString("name"));
		student.setSchool(rs.getString("school"));
		student.setProgramme(rs.getString("programme"));
		student.setYearOfStudy(rs.getInt("year_of_study"));
		student.setContactNumber(rs.getString("contact_number"));
		String mode = rs.getString("meeting_mode");
		student.setMeetingMode(mode == null ? null : MeetingMode.valueOf(mode));
		String groupSize = rs.getString("group_size_preference");
		student.setGroupSizePreference(groupSize == null ? null : GroupSizePreference.valueOf(groupSize));
		student.getCurrentCourses().addAll(findCourses(rs.getArray("current_courses"), courses));
		student.getCoursesForStudying().addAll(findCourses(rs.getArray("courses_for_studying"), courses));
		for (String goal : strings(rs.getArray("study_goals")))
			student.getStudyGoals().add(StudyGoal.valueOf(goal));
		String availability = rs.getString("weekly_availability");
		if (availability != null) {
			try {
				student.getWeeklyAvailability().addAll(List.of(mapper.readValue(availability, TimeSlot[].class)));
			} catch (JacksonException e) {
				throw new IllegalStateException("Could not read weekly availability", e);
			}
		}
		return student;
	}

	// The arrays only hold course ids, so the code and name come from the courses table. It is
	// small, so it is read once per call rather than once per student: each query is a round trip
	// to the database, and two per student made listing every student take tens of seconds.
	private Map<UUID, Course> allCourses() {
		Map<UUID, Course> courses = new HashMap<>();
		jdbc.query("SELECT course_id, course_code, course_name FROM courses", rs -> {
			UUID id = rs.getObject("course_id", UUID.class);
			courses.put(id, new Course(id, rs.getString("course_code"), rs.getString("course_name")));
		});
		return courses;
	}

	// Ids with no matching course (e.g. a deleted course) are skipped.
	private static List<Course> findCourses(Array ids, Map<UUID, Course> courses) throws SQLException {
		return Arrays.stream(strings(ids)).map(id -> courses.get(UUID.fromString(id))).filter(Objects::nonNull).toList();
	}

	private static String[] courseIds(List<Course> courses) {
		return courses.stream().map(Course::getCourseId).filter(Objects::nonNull).map(UUID::toString)
				.toArray(String[]::new);
	}

	private static String[] strings(Array array) throws SQLException {
		if (array == null)
			return new String[0];
		Object[] values = (Object[]) array.getArray();
		String[] result = new String[values.length];
		for (int i = 0; i < values.length; i++)
			result[i] = values[i].toString();
		return result;
	}
}
