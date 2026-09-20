package com.studybuddy.connection.client;

import com.studybuddy.common.dto.StudentContactDto;
import com.studybuddy.common.dto.StudentDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Fetches student details from user-service.
 *
 * This service only stores ids - senderId, recipientId - so anything else
 * about a student has to be asked for. The base URL comes from configuration
 * rather than being written into the Java.
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
	 * Contact details, for use once a match request has been accepted.
	 *
	 * TODO: only call this after checking the two students are connected.
	 */
	public StudentContactDto findContactDetails(String userId) {
		return restClient.get()
				.uri("/api/students/{userId}/contact", userId)
				.retrieve()
				.body(StudentContactDto.class);
	}
}
