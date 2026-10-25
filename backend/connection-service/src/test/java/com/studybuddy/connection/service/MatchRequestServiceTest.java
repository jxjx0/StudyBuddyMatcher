package com.studybuddy.connection.service;

import com.studybuddy.connection.client.StudentClient;
import com.studybuddy.connection.dto.StudentContactDto;
import com.studybuddy.connection.model.MatchRequest;
import com.studybuddy.connection.model.MatchRequestStatus;
import com.studybuddy.connection.model.StudyBuddyConnection;
import com.studybuddy.connection.repository.MatchRequestRepository;
import com.studybuddy.connection.repository.StudyBuddyConnectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MatchRequestServiceTest {
	private static final String STUDENT_A = "00000000-0000-0000-0000-000000000001";
	private static final String STUDENT_B = "00000000-0000-0000-0000-000000000002";

	private MatchRequestService service;
	private StudyBuddyConnectionRepository connections;
	private MatchRequestRepository requests;
	private StudentClient studentClient;

	@BeforeEach
	void setUp() {
		connections = mock(StudyBuddyConnectionRepository.class);
		requests = mock(MatchRequestRepository.class);
		when(connections.findActiveByStudentId(anyString())).thenReturn(List.of());
		when(connections.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(requests.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		studentClient = mock(StudentClient.class);
		service = new MatchRequestService(requests, connections, studentClient);
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

	@Test
	void sendingReciprocalRequestAcceptsExistingRequestAndCreatesConnection() {
		MatchRequest original = pendingRequest(STUDENT_A, STUDENT_B);
		when(requests.findByRecipientId(STUDENT_B)).thenReturn(List.of(original));
		when(requests.findBySenderId(STUDENT_A)).thenReturn(List.of(original));

		MatchRequest accepted = service.send(STUDENT_B, STUDENT_A, "");

		assertSame(original, accepted);
		assertEquals(MatchRequestStatus.ACCEPTED, accepted.getStatus());
		verify(connections).save(any(StudyBuddyConnection.class));
		assertEquals(0, service.findPendingFor(STUDENT_B).size());
		assertEquals(0, service.findOutgoingPendingFor(STUDENT_A).size());
	}

	@Test
	void duplicateOutgoingRequestReturnsConflictInsteadOfServerError() {
		MatchRequest request = pendingRequest(STUDENT_A, STUDENT_B);
		when(requests.findByRecipientId(STUDENT_A)).thenReturn(List.of());
		when(requests.findBySenderId(STUDENT_A)).thenReturn(List.of(request));

		ResponseStatusException exception = assertThrows(ResponseStatusException.class,
				() -> service.send(STUDENT_A, STUDENT_B, ""));

		assertEquals(409, exception.getStatusCode().value());
	}

	@Test
	void senderCanCancelPendingRequest() {
		MatchRequest request = pendingRequest(STUDENT_A, STUDENT_B);
		when(requests.findById(request.getRequestId())).thenReturn(Optional.of(request));
		when(requests.findByRecipientId(STUDENT_B)).thenReturn(List.of(request));
		when(requests.findBySenderId(STUDENT_A)).thenReturn(List.of(request));

		MatchRequest cancelled = service.cancel(request.getRequestId(), STUDENT_A);

		assertSame(request, cancelled);
		assertEquals(MatchRequestStatus.CANCELLED, cancelled.getStatus());
		assertEquals(0, service.findPendingFor(STUDENT_B).size());
		assertEquals(0, service.findOutgoingPendingFor(STUDENT_A).size());
	}

	@Test
	void onlySenderCanCancelPendingRequest() {
		MatchRequest request = pendingRequest(STUDENT_A, STUDENT_B);
		when(requests.findById(request.getRequestId())).thenReturn(Optional.of(request));

		ResponseStatusException exception = assertThrows(ResponseStatusException.class,
				() -> service.cancel(request.getRequestId(), STUDENT_B));

		assertEquals(409, exception.getStatusCode().value());
		assertEquals(MatchRequestStatus.PENDING, request.getStatus());
	}

	private StudyBuddyConnection connection() {
		StudyBuddyConnection connection = new StudyBuddyConnection();
		connection.setStudentAId(STUDENT_A);
		connection.setStudentBId(STUDENT_B);
		connection.setConnectionId("00000000-0000-0000-0000-000000000003");
		return connection;
	}

	private MatchRequest pendingRequest(String senderId, String recipientId) {
		MatchRequest request = new MatchRequest();
		request.setRequestId("00000000-0000-0000-0000-000000000004");
		request.setSenderId(senderId);
		request.setRecipientId(recipientId);
		request.setMessage("Let's study");
		return request;
	}
}
