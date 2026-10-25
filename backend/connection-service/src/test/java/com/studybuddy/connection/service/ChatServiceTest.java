package com.studybuddy.connection.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.studybuddy.connection.dto.ChatMessage;
import com.studybuddy.connection.model.StudyBuddyConnection;
import com.studybuddy.connection.repository.ChatMessageRepository;
import com.studybuddy.connection.repository.StudyBuddyConnectionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ChatServiceTest {
  private static final String STUDENT_A = "00000000-0000-0000-0000-000000000001";
  private static final String STUDENT_B = "00000000-0000-0000-0000-000000000002";
  private static final String CONNECTION = "00000000-0000-0000-0000-000000000003";

  private StudyBuddyConnectionRepository connections;
  private ChatMessageRepository messages;
  private ChatService service;

  @BeforeEach
  void setUp() {
    connections = mock(StudyBuddyConnectionRepository.class);
    messages = mock(ChatMessageRepository.class);
    service = new ChatService(connections, messages);
  }

  @Test
  void connectedStudentsCanReadTheirConversationHistory() {
    var message =
        new ChatMessage("message-id", CONNECTION, STUDENT_A, "Hello", Instant.now());
    when(connections.findById(CONNECTION)).thenReturn(Optional.of(connection()));
    when(messages.findRecent(CONNECTION)).thenReturn(List.of(message));

    assertEquals(List.of(message), service.history(CONNECTION, STUDENT_B));
  }

  @Test
  void unrelatedStudentsCannotReadOrSendMessages() {
    when(connections.findById(CONNECTION)).thenReturn(Optional.of(connection()));

    assertThrows(
        ResponseStatusException.class, () -> service.history(CONNECTION, "00000000-0000-0000-0000-000000000004"));
    assertThrows(
        ResponseStatusException.class,
        () -> service.send(CONNECTION, "00000000-0000-0000-0000-000000000004", "Hello"));
    verify(messages, never()).findRecent(anyString());
    verify(messages, never()).save(anyString(), anyString(), anyString());
  }

  @Test
  void endedConnectionsCannotAccessChatHistoryOrSendMessages() {
    StudyBuddyConnection connection = connection();
    connection.end();
    when(connections.findById(CONNECTION)).thenReturn(Optional.of(connection));

    assertThrows(
        ResponseStatusException.class, () -> service.history(CONNECTION, STUDENT_A));
    assertThrows(
        ResponseStatusException.class, () -> service.send(CONNECTION, STUDENT_A, "Hello"));
    verify(messages, never()).save(anyString(), anyString(), anyString());
  }

  @Test
  void sendsTrimmedMessagesForTheAuthenticatedStudent() {
    when(connections.findById(CONNECTION)).thenReturn(Optional.of(connection()));
    var saved = new ChatMessage("message-id", CONNECTION, STUDENT_A, "Hello", Instant.now());
    when(messages.save(CONNECTION, STUDENT_A, "Hello")).thenReturn(saved);

    assertEquals(saved, service.send(CONNECTION, STUDENT_A, "  Hello  "));
    verify(messages).save(CONNECTION, STUDENT_A, "Hello");
  }

  @Test
  void blankOrOversizedMessagesAreRejected() {
    when(connections.findById(CONNECTION)).thenReturn(Optional.of(connection()));

    assertThrows(
        ResponseStatusException.class, () -> service.send(CONNECTION, STUDENT_A, "  "));
    assertThrows(
        ResponseStatusException.class,
        () -> service.send(CONNECTION, STUDENT_A, "x".repeat(2001)));
    verify(messages, never()).save(anyString(), anyString(), anyString());
  }

  private StudyBuddyConnection connection() {
    StudyBuddyConnection connection = new StudyBuddyConnection();
    connection.setConnectionId(CONNECTION);
    connection.setStudentAId(STUDENT_A);
    connection.setStudentBId(STUDENT_B);
    return connection;
  }
}
