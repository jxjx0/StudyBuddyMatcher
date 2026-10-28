package com.studybuddy.connection.repository;

import com.studybuddy.connection.dto.ChatMessage;
import com.studybuddy.connection.dto.ChatSummary;
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

  /**
   * One row per active connection of the student: its latest message (if any) and how many
   * messages from the partner arrived after the student last read the conversation.
   */
  public List<ChatSummary> findSummaries(String studentId) {
    UUID student = UUID.fromString(studentId);
    List<ChatSummary> summaries =
        jdbc.query(
            """
                SELECT c.connection_id,
                       latest.message_id, latest.sender_id, latest.content, latest.sent_at,
                       (SELECT count(*)
                        FROM connection_chat_messages unread
                        WHERE unread.connection_id = c.connection_id
                          AND unread.sender_id <> ?
                          AND unread.sent_at > coalesce(r.last_read_at, '-infinity')) AS unread_count
                FROM study_buddy_connections c
                LEFT JOIN connection_chat_reads r
                  ON r.connection_id = c.connection_id AND r.student_id = ?
                LEFT JOIN LATERAL (
                  SELECT message_id, sender_id, content, sent_at
                  FROM connection_chat_messages m
                  WHERE m.connection_id = c.connection_id
                  ORDER BY sent_at DESC, message_id DESC
                  LIMIT 1
                ) latest ON true
                WHERE c.active AND (c.student_a_id = ? OR c.student_b_id = ?)
                """,
            (resultSet, rowNumber) -> {
              String connectionId = resultSet.getString("connection_id");
              ChatMessage lastMessage =
                  resultSet.getString("message_id") == null
                      ? null
                      : new ChatMessage(
                          resultSet.getString("message_id"),
                          connectionId,
                          resultSet.getString("sender_id"),
                          resultSet.getString("content"),
                          resultSet.getTimestamp("sent_at").toInstant());
              return new ChatSummary(connectionId, lastMessage, resultSet.getInt("unread_count"));
            },
            student,
            student,
            student,
            student);
    return List.copyOf(summaries);
  }

  /** Marks everything sent in the conversation up to now as read by the student. */
  public void markRead(String connectionId, String studentId) {
    jdbc.update(
        """
            INSERT INTO connection_chat_reads(connection_id, student_id, last_read_at)
            VALUES (?, ?, now())
            ON CONFLICT (connection_id, student_id)
            DO UPDATE SET last_read_at = greatest(connection_chat_reads.last_read_at, excluded.last_read_at)
            """,
        UUID.fromString(connectionId),
        UUID.fromString(studentId));
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
