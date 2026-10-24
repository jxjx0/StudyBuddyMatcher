package com.studybuddy.connection.service;

import com.studybuddy.connection.client.StudentClient;
import com.studybuddy.connection.dto.StudentContactDto;
import com.studybuddy.connection.model.StudyBuddyConnection;
import com.studybuddy.connection.repository.MatchRequestRepository;
import com.studybuddy.connection.repository.StudyBuddyConnectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MatchRequestServiceTest {
	private static final String STUDENT_A = "student-a";
	private static final String STUDENT_B = "student-b";

	private MatchRequestService service;
	private MatchRequestRepository matchRequests;
	private StudyBuddyConnectionRepository connections;
	private StudentClient studentClient;

	@BeforeEach
	void setUp() {
		matchRequests = mock(MatchRequestRepository.class);
		connections = mock(StudyBuddyConnectionRepository.class);
		studentClient = mock(StudentClient.class);
		service = new MatchRequestService(matchRequests, connections, studentClient);
	}

	@Test
	void returnsPartnerContactForActiveConnectionMember() {
		StudyBuddyConnection connection = connection();
		when(connections.findById(connection.getConnectionId())).thenReturn(Optional.of(connection));

		StudentContactDto contact = new StudentContactDto(STUDENT_B, "Study Buddy", "buddy@example.edu",
				"+65 8123 4567");
		when(studentClient.findContactDetails(STUDENT_B)).thenReturn(contact);

		StudentContactDto result = service.findActiveConnectionContact(connection.getConnectionId(), STUDENT_A);

		assertSame(contact, result);
		verify(studentClient).findContactDetails(STUDENT_B);
	}

	@Test
	void doesNotFetchContactForNonMember() {
		StudyBuddyConnection connection = connection();
		when(connections.findById(connection.getConnectionId())).thenReturn(Optional.of(connection));

		ResponseStatusException exception = assertThrows(ResponseStatusException.class,
				() -> service.findActiveConnectionContact(connection.getConnectionId(), "unrelated-student"));

		assertEquals(404, exception.getStatusCode().value());
		verifyNoInteractions(studentClient);
	}

	@Test
	void doesNotFetchContactAfterConnectionEnds() {
		StudyBuddyConnection connection = connection();
		connection.end();
		when(connections.findById(connection.getConnectionId())).thenReturn(Optional.of(connection));

		ResponseStatusException exception = assertThrows(ResponseStatusException.class,
				() -> service.findActiveConnectionContact(connection.getConnectionId(), STUDENT_A));

		assertEquals(404, exception.getStatusCode().value());
		verifyNoInteractions(studentClient);
	}

	private StudyBuddyConnection connection() {
		StudyBuddyConnection connection = new StudyBuddyConnection();
		connection.setConnectionId("test-connection-id");
		connection.setStudentAId(STUDENT_A);
		connection.setStudentBId(STUDENT_B);
		return connection;
	}
}
