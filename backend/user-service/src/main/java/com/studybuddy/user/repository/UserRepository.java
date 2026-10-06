package com.studybuddy.user.repository;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.studybuddy.user.model.AccountStatus;
import com.studybuddy.user.model.Course;
import com.studybuddy.user.model.GroupSizePreference;
import com.studybuddy.user.model.MeetingMode;
import com.studybuddy.user.model.Student;
import com.studybuddy.user.model.StudyGoal;
import com.studybuddy.user.model.SystemAdministrator;
import com.studybuddy.user.model.TimeSlot;
import com.studybuddy.user.model.User;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** PostgreSQL storage for user accounts and student profile documents. */
@Repository
public class UserRepository {
	// A record (Java 16+) is a short way to write a class that just holds data.
	private record StudentProfile(String name, String school, String programme, int yearOfStudy,
			String contactNumber, List<Course> currentCourses, List<Course> coursesForStudying,
			MeetingMode meetingMode, List<TimeSlot> weeklyAvailability, GroupSizePreference groupSizePreference,
			List<StudyGoal> studyGoals) { }

	private final JdbcTemplate jdbc; // A Spring class for running SQL against the database (PostgreSQL here).
	// ObjectMapper: converts between Java and JSON; main class of Jackson library
	private final ObjectMapper mapper; // turns one database row into one Java object
	private final RowMapper<User> userMapper; 

	public UserRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
		this.jdbc = jdbc;
		this.mapper = mapper;
		this.userMapper = this::mapUser;
		// :: means "use this method as the implementation." It's shorthand for a lambda:
		// this.userMapper = (rs, row) -> this.mapUser(rs, row);
	}

	private User mapUser(ResultSet rs, int row) throws SQLException {
		String role = rs.getString("user_role");
		User user = "ADMIN".equals(role) ? new SystemAdministrator() : new Student();
		user.setUserId(rs.getString("user_id"));
		user.setEmail(rs.getString("email"));
		user.setPasswordHash(rs.getString("password_hash"));
		user.setAccountStatus(AccountStatus.valueOf(rs.getString("account_status")));
		String profile = rs.getString("profile_data");
		if (profile != null && user instanceof Student student) {
			StudentProfile saved;
			try { saved = mapper.readValue(profile, StudentProfile.class); }
			// JacksonException: JSON conversion failed
			catch (JacksonException e) { throw new IllegalStateException("Could not read saved student profile", e); }
			student.setName(saved.name());
			student.setSchool(saved.school());
			student.setProgramme(saved.programme());
			student.setYearOfStudy(saved.yearOfStudy());
			student.setContactNumber(saved.contactNumber());
			student.getCurrentCourses().addAll(saved.currentCourses());
			student.getCoursesForStudying().addAll(saved.coursesForStudying());
			student.setMeetingMode(saved.meetingMode());
			student.getWeeklyAvailability().addAll(saved.weeklyAvailability());
			student.setGroupSizePreference(saved.groupSizePreference());
			student.getStudyGoals().addAll(saved.studyGoals());
		} else if (profile != null && user instanceof SystemAdministrator admin) {
			try { admin.setName(mapper.readTree(profile).path("name").asText("System Administrator")); }
			catch (JacksonException e) { throw new IllegalStateException("Could not read saved administrator profile", e); }
		}
		return user;
	}

	public List<User> findAll() {
		return jdbc.query("SELECT * FROM user_accounts ORDER BY email", userMapper);
	}

	public Optional<User> findById(String userId) {
		if (!isUuid(userId)) return Optional.empty();
		return queryOne("SELECT * FROM user_accounts WHERE user_id = CAST(? AS uuid)", userId);
	}

	public Optional<User> findByEmail(String email) {
		return queryOne("SELECT * FROM user_accounts WHERE email = ?", email.trim().toLowerCase(Locale.ROOT));
	}

	public List<Student> findAllStudents() {
		return jdbc.query("SELECT * FROM user_accounts WHERE user_role = 'STUDENT' ORDER BY email", userMapper)
				.stream().map(Student.class::cast).toList();
	}

	public Optional<Student> findStudentById(String userId) {
		return findById(userId).filter(Student.class::isInstance).map(Student.class::cast);
	}

	public User save(User user) {
		if (user.getUserId() == null || user.getUserId().isBlank()) user.setUserId(UUID.randomUUID().toString());
		String role = user instanceof SystemAdministrator ? "ADMIN" : "STUDENT";
		String profile;
		try {
			if (user instanceof Student student) {
				profile = mapper.writeValueAsString(new StudentProfile(student.getName(), student.getSchool(),
						student.getProgramme(), student.getYearOfStudy(), student.getContactNumber(),
						student.getCurrentCourses(), student.getCoursesForStudying(), student.getMeetingMode(),
						student.getWeeklyAvailability(), student.getGroupSizePreference(), student.getStudyGoals()));
			} else {
				profile = mapper.writeValueAsString(java.util.Map.of("name", ((SystemAdministrator) user).getName()));
			}
		} catch (JacksonException e) {
			throw new IllegalStateException("Could not serialize account profile", e);
		}
		try {
			jdbc.update("""
					INSERT INTO user_accounts(user_id, email, password_hash, user_role, account_status, profile_data)
					VALUES (CAST(? AS uuid), ?, ?, ?, ?, CAST(? AS jsonb))
					ON CONFLICT (user_id) DO UPDATE SET email = EXCLUDED.email,
					 password_hash = EXCLUDED.password_hash, user_role = EXCLUDED.user_role,
					 account_status = EXCLUDED.account_status, profile_data = EXCLUDED.profile_data
					""", user.getUserId(), user.getEmail(), user.getPasswordHash(), role,
					user.getAccountStatus().name(), profile);
		} catch (DataIntegrityViolationException e) {
			throw new DataIntegrityViolationException("An account with this email already exists", e);
		}
		return user;
	}

	public boolean deleteById(String userId) {
		if (!isUuid(userId)) return false;
		return jdbc.update("DELETE FROM user_accounts WHERE user_id = CAST(? AS uuid)", userId) > 0;
	}

	public long countByRole(String role) {
		return jdbc.queryForObject("SELECT count(*) FROM user_accounts WHERE user_role = ?", Long.class, role);
	}

	public long countByRoleAndStatus(String role, String status) {
		return jdbc.queryForObject("SELECT count(*) FROM user_accounts WHERE user_role = ? AND account_status = ?",
				Long.class, role, status);
	}

	// user_id is a PostgreSQL uuid; an id that is not a valid UUID cannot match any account.
	private static boolean isUuid(String value) {
		if (value == null) return false;
		try { UUID.fromString(value); return true; }
		catch (IllegalArgumentException e) { return false; }
	}

	private Optional<User> queryOne(String sql, String value) {
		List<User> result = jdbc.query(sql, userMapper, value);
		return result.stream().findFirst();
	}
}
