package com.studybuddy.user.controller;

import com.studybuddy.user.dto.StudentContactDto;
import com.studybuddy.user.service.StudentService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class StudentControllerTest {
	private static final String CONTACT_SERVICE_SECRET = "test-contact-service-secret-with-32-chars";

	@Test
	void internalContactLookupRequiresServiceSecret() {
		StudentService studentService = mock(StudentService.class);
		StudentController controller = new StudentController(studentService, CONTACT_SERVICE_SECRET);

		assertThrows(AccessDeniedException.class,
				() -> controller.findContactDetailsForConnectionService("student-id", "wrong-secret"));
		verifyNoInteractions(studentService);
	}

	@Test
	void internalContactLookupReturnsDetailsWithValidServiceSecret() {
		StudentService studentService = mock(StudentService.class);
		StudentController controller = new StudentController(studentService, CONTACT_SERVICE_SECRET);
		StudentContactDto contact = new StudentContactDto("student-id", "Study Buddy", "buddy@example.edu",
				"+65 8123 4567");
		when(studentService.findContactDetails("student-id")).thenReturn(contact);

		assertSame(contact, controller.findContactDetailsForConnectionService("student-id", CONTACT_SERVICE_SECRET));
		verify(studentService).findContactDetails("student-id");
	}
}
