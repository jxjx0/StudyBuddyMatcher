package com.studybuddy.matching.client;

import com.studybuddy.matching.dto.StudentDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Fetches student data from user-service over HTTP.
 *
 * This is the boundary between the two services. matching-service owns no
 * student data of its own - it has to ask. Everything arrives as StudentDto,
 * which has no contact number, so matching can never see one.
 *
 * The base URL comes from configuration rather than being hard-coded, so the
 * service can be repointed without a rebuild.
 */
@Component
public class StudentClient {

	private final RestClient restClient;

	public StudentClient(RestClient.Builder builder,
						 @Value("${services.user-service-url}") String userServiceUrl) {
		this.restClient = builder.baseUrl(userServiceUrl).build();
	}

	/**
	 * Every student, as candidates to score against.
	 */
	public List<StudentDto> findAllStudents() {
		return restClient.get()
				.uri("/api/students")
				.retrieve()
				.body(new org.springframework.core.ParameterizedTypeReference<List<StudentDto>>() {
				});
	}

	public StudentDto findStudent(String userId) {
		return restClient.get()
				.uri("/api/students/{userId}", userId)
				.retrieve()
				.body(StudentDto.class);
	}
}
