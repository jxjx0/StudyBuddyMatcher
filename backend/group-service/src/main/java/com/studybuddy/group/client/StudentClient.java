package com.studybuddy.group.client;

import com.studybuddy.group.dto.CourseDto;
import com.studybuddy.group.dto.StudentDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Fetches student and course details from user-service.
 *
 * This service only stores ids - leaderId, memberIds, courseCode - so
 * displaying a group means asking user-service for the names behind them.
 */
@Component
public class StudentClient {

	private final RestClient restClient;

	public StudentClient(RestClient.Builder builder,
						 @Value("${services.user-service-url}") String userServiceUrl) {
		this.restClient = builder.baseUrl(userServiceUrl).build();
	}

	/**
	 * A student's public profile - no contact number.
	 */
	public StudentDto findStudent(String userId) {
		return restClient.get()
				.uri("/api/students/{userId}", userId)
				.retrieve()
				.body(StudentDto.class);
	}

	/**
	 * Used to check a course code is real before creating a group for it.
	 */
	public CourseDto findCourse(String courseCode) {
		return restClient.get()
				.uri("/api/courses/{courseCode}", courseCode)
				.retrieve()
				.body(CourseDto.class);
	}
}
