package com.studybuddy.connection.repository;

import com.studybuddy.connection.dto.ChatMessage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ChatMessageRepository {
  private static final int MAX_HISTORY = 100;
  private final JdbcTemplate jdbc;

  public ChatMessageRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<ChatMessage> findRecent(String connectionId) {
    List<ChatMessage> messages =
        jdbc.query(
            """
                SELECT message_id, connection_id, sender_id, content, sent_at
                FROM (
                  SELECT message_id, connection_id, sender_id, content, sent_at
                  FROM connection_chat_messages
                  WHERE connection_id = ?
                  ORDER BY sent_at DESC, message_id DESC
                  LIMIT ?
                ) recent
                ORDER BY sent_at, message_id
                """,
            this::mapMessage,
            UUID.fromString(connectionId),
            MAX_HISTORY);
    return List.copyOf(messages);
  }

  public ChatMessage save(String connectionId, String senderId, String content) {
    UUID messageId = UUID.randomUUID();
    return jdbc.queryForObject(
        """
            INSERT INTO connection_chat_messages(message_id, connection_id, sender_id, content)
            VALUES (?, ?, ?, ?)
            RETURNING message_id, connection_id, sender_id, content, sent_at
            """,
        this::mapMessage,
        messageId,
        UUID.fromString(connectionId),
        UUID.fromString(senderId),
        content);
  }

  private ChatMessage mapMessage(ResultSet resultSet, int rowNumber) throws SQLException {
    Timestamp sentAt = resultSet.getTimestamp("sent_at");
    return new ChatMessage(
        resultSet.getString("message_id"),
        resultSet.getString("connection_id"),
        resultSet.getString("sender_id"),
        resultSet.getString("content"),
        sentAt.toInstant());
  }
}
