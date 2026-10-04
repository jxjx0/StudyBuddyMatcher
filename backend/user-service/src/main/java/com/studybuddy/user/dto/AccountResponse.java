package com.studybuddy.user.dto;

import com.studybuddy.user.model.Student;
import com.studybuddy.user.model.SystemAdministrator;
import com.studybuddy.user.model.User;

public record AccountResponse(String userId, String email, String name, String role, String accountStatus, String school,
		int currentCourseCount, int studyGoalCount, int availabilitySlotCount) {
	public static AccountResponse from(User user) {
		String name = user instanceof Student student ? student.getName()
				: user instanceof SystemAdministrator admin ? admin.getName() : "";
		String school = user instanceof Student student ? student.getSchool() : null;
		int currentCourseCount = user instanceof Student student ? student.getCurrentCourses().size() : 0;
		int studyGoalCount = user instanceof Student student ? student.getStudyGoals().size() : 0;
		int availabilitySlotCount = user instanceof Student student ? student.getWeeklyAvailability().size() : 0;
		return new AccountResponse(user.getUserId(), user.getEmail(), name,
				user instanceof SystemAdministrator ? "ADMIN" : "STUDENT", user.getAccountStatus().name(), school,
				currentCourseCount, studyGoalCount, availabilitySlotCount);
	}
}
