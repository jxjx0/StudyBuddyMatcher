package com.studybuddy.user.config;

import com.studybuddy.user.model.AccountStatus;
import com.studybuddy.user.model.Course;
import com.studybuddy.user.model.GroupSizePreference;
import com.studybuddy.user.model.MeetingMode;
import com.studybuddy.user.model.StudyGoal;
import com.studybuddy.user.model.Student;
import com.studybuddy.user.model.SystemAdministrator;
import com.studybuddy.user.repository.CourseRepository;
import com.studybuddy.user.repository.StudentRepository;
import com.studybuddy.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Optional local/demo population. Disabled unless DEMO_DATA_ENABLED=true. */
@Component
public class DemoDataSeeder implements CommandLineRunner {
	private static final List<Course> COURSES = List.of(
			new Course("IS442", "Object Oriented Programming"), new Course("IS212", "Database Systems"),
			new Course("MKTG101", "Marketing Principles"), new Course("FIN201", "Corporate Finance"),
			new Course("ACC201", "Financial Accounting"), new Course("ACC305", "Management Accounting"),
			new Course("ECON101", "Principles of Economics"), new Course("ECON202", "Microeconomic Analysis"),
			new Course("COR-LAW", "Business Law"), new Course("LAW201", "Legal Research and Writing"));
	private static final List<String> SCHOOLS = List.of("School of Computing and Information Systems",
			"School of Business", "School of Accountancy", "School of Economics", "School of Law",
			"School of Social Sciences");
	private static final List<String> STUDENT_NAMES = List.of("Jack Tan", "Amy Chew", "Ryan Lim", "Chloe Ng", "Ethan Koh",
			"Sarah Lee", "Daniel Goh", "Jasmine Wong", "Aaron Chua", "Grace Teo", "Marcus Yeo", "Rachel Ong",
			"Lucas Ho", "Nicole Sim", "Caleb Low", "Hannah Toh", "Darren Seah", "Melissa Tay", "Nathan Quek",
			"Alicia Foo", "Brandon Tan", "Emily Chua", "Isaac Lim", "Megan Lee", "Adrian Koh", "Sharon Ng",
			"Kevin Goh", "Faith Wong", "Justin Teo", "Rebecca Yeo", "Nicholas Ong", "Joanne Ho", "Samuel Sim",
			"Amanda Low", "Benjamin Toh", "Cynthia Seah", "Dylan Tay", "Elaine Quek", "Felix Foo", "Gwen Tan",
			"Howard Chew", "Irene Lim", "Jeremy Ng", "Kayla Koh", "Leon Lee", "Michelle Goh", "Noah Wong",
			"Olivia Teo", "Patrick Yeo", "Queenie Ong");
	private final UserRepository users;
	private final StudentRepository studentProfiles;
	private final CourseRepository courses;
	private final PasswordEncoder passwords;
	private final boolean enabled;
	private final String adminEmail;
	private final String adminPassword;
	private final String studentPassword;
	public DemoDataSeeder(UserRepository users, StudentRepository studentProfiles, CourseRepository courses, PasswordEncoder passwords,
			@Value("${app.seed.demo-data:false}") boolean enabled,
			@Value("${app.seed.admin-email:admin@studybuds.sg}") String adminEmail,
			@Value("${app.seed.admin-password:}") String adminPassword,
			@Value("${app.seed.student-password:}") String studentPassword) {
		this.users = users; this.studentProfiles = studentProfiles; this.courses = courses; this.passwords = passwords;
		this.enabled = enabled; this.adminEmail = adminEmail; this.adminPassword = adminPassword; this.studentPassword = studentPassword;
	}
	@Override public void run(String... args) {
		if (!enabled) return;
		if (adminPassword.length() < 8 || studentPassword.length() < 8)
			throw new IllegalStateException("DEMO_DATA_ENABLED requires SEED_ADMIN_PASSWORD and SEED_STUDENT_PASSWORD (8+ characters each)");
		// Saved copies carry the database-generated course_id, which the students table stores.
		List<Course> savedCourses = COURSES.stream().map(courses::save).toList();
		if (users.findByEmail(adminEmail).isEmpty()) {
			users.save(new SystemAdministrator(null, adminEmail, passwords.encode(adminPassword), "StudyBuds Admin"));
		}
		for (int i = 1; i <= 50; i++) {
			String email = String.format("student%02d@smu.edu.sg", i);
			Student student = users.findByEmail(email).filter(Student.class::isInstance).map(Student.class::cast)
					.orElseGet(() -> new Student(null, email, passwords.encode(studentPassword), AccountStatus.ACTIVE));
			student.setAccountStatus(AccountStatus.ACTIVE); student.setName(STUDENT_NAMES.get(i - 1));
			student.setSchool(SCHOOLS.get((i - 1) % SCHOOLS.size()));
			student.setProgramme(switch ((i - 1) % SCHOOLS.size()) {
				case 0 -> "Information Systems"; case 1 -> "Business Management"; case 2 -> "Accountancy";
				case 3 -> "Economics"; case 4 -> "Law"; default -> "Social Sciences";
			});
			student.setYearOfStudy(1 + i % 4); student.setContactNumber(String.format("+65 8000 %04d", i));
			student.getStudyGoals().clear(); student.getCurrentCourses().clear(); student.getCoursesForStudying().clear(); student.getWeeklyAvailability().clear();
			// Seeded with the student number, so each student differs but keeps the same picks on every restart.
			Random random = new Random(i);
			// 2-4 current courses, 1 to all of them needing a buddy, and 1-4 study goals.
			List<Course> current = pickRandom(savedCourses, 2 + random.nextInt(3), random);
			student.getCurrentCourses().addAll(current);
			student.getCoursesForStudying().addAll(pickRandom(current, 1 + random.nextInt(current.size()), random));
			student.getStudyGoals().addAll(pickRandom(List.of(StudyGoal.values()), 1 + random.nextInt(StudyGoal.values().length), random));
			student.setMeetingMode(MeetingMode.values()[random.nextInt(MeetingMode.values().length)]);
			student.setGroupSizePreference(GroupSizePreference.values()[random.nextInt(GroupSizePreference.values().length)]);
			student.getWeeklyAvailability().add(new com.studybuddy.user.model.TimeSlot(
					new String[]{"MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"}[i % 5],
					LocalTime.of(16 + i % 4, 0), LocalTime.of(18 + i % 4, 0)));
			users.save(student);
			// The Discover page and profile lookups read the students table, so keep it in step.
			if (!studentProfiles.update(student)) studentProfiles.save(student);
		}
	}

	/** {@code count} distinct items from {@code items}, in random order. */
	private static <T> List<T> pickRandom(List<T> items, int count, Random random) {
		List<T> shuffled = new ArrayList<>(items);
		Collections.shuffle(shuffled, random);
		return shuffled.subList(0, count);
	}
}
