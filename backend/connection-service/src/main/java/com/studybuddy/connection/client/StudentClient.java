package com.studybuddy.connection.client;

import com.studybuddy.connection.dto.StudentContactDto;
import com.studybuddy.connection.dto.StudentDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;

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
	private final String contactServiceSecret;

	public StudentClient(RestClient.Builder builder,
						 @Value("${services.user-service-url}") String userServiceUrl,
						 @Value("${services.contact-service-secret:}") String contactServiceSecret) {
		if (contactServiceSecret == null
				|| contactServiceSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
			throw new IllegalStateException("CONTACT_SERVICE_SECRET must contain at least 32 characters");
		}
		this.restClient = builder.baseUrl(userServiceUrl).build();
		this.contactServiceSecret = contactServiceSecret;
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
	 * Contact details are fetched only after connection-service verifies an active connection.
	 */
	public StudentContactDto findContactDetails(String userId) {
		return restClient.get()
				.uri("/api/students/internal/{userId}/contact", userId)
				.header("X-Contact-Service-Secret", contactServiceSecret)
				.retrieve()
				.body(StudentContactDto.class);
	}
}
