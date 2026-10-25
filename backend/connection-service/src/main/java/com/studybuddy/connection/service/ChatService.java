package com.studybuddy.connection.service;

import com.studybuddy.connection.dto.ChatMessage;
import com.studybuddy.connection.model.StudyBuddyConnection;
import com.studybuddy.connection.repository.ChatMessageRepository;
import com.studybuddy.connection.repository.StudyBuddyConnectionRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ChatService {
  private final StudyBuddyConnectionRepository connectionRepository;
  private final ChatMessageRepository messageRepository;

  public ChatService(
      StudyBuddyConnectionRepository connectionRepository, ChatMessageRepository messageRepository) {
    this.connectionRepository = connectionRepository;
    this.messageRepository = messageRepository;
  }

  public List<ChatMessage> history(String connectionId, String studentId) {
    requireActiveParticipant(connectionId, studentId);
    return messageRepository.findRecent(connectionId);
  }

  @Transactional
  public ChatMessage send(String connectionId, String studentId, String content) {
    StudyBuddyConnection connection = requireActiveParticipantConnection(connectionId, studentId);
    if (content == null || content.isBlank() || content.length() > 2000) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Message must contain 1 to 2000 characters");
    }
    return messageRepository.save(connection.getConnectionId(), studentId, content.trim());
  }

  public void requireActiveParticipant(String connectionId, String studentId) {
    requireActiveParticipantConnection(connectionId, studentId);
  }

  private StudyBuddyConnection requireActiveParticipantConnection(
      String connectionId, String studentId) {
    try {
      UUID.fromString(connectionId);
      UUID.fromString(studentId);
    } catch (IllegalArgumentException | NullPointerException exception) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    StudyBuddyConnection connection =
        connectionRepository
        .findById(connectionId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    if (!connection.isActive() || !connection.involves(studentId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    return connection;
  }
}
