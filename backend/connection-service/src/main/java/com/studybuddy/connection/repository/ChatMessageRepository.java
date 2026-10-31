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
  private static final String MESSAGE_ID_COLUMN = "message_id";
  private final JdbcTemplate jdbc;

  public ChatMessageRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<ChatMessage> findRecent(String connectionId, String studentId) {
    List<ChatMessage> messages =
        jdbc.query(
            """
                SELECT message_id, connection_id, sender_id, content, sent_at
                FROM (
                  SELECT m.message_id, m.connection_id, m.sender_id, m.content, m.sent_at
                  FROM connection_chat_messages m
                  LEFT JOIN connection_chat_deletions d
                    ON d.connection_id = m.connection_id AND d.student_id = ?
                  WHERE m.connection_id = ?
                    AND m.sent_at > coalesce(d.deleted_at, '-infinity')
                  ORDER BY m.sent_at DESC, m.message_id DESC
                  LIMIT ?
                ) recent
                ORDER BY sent_at, message_id
                """,
            this::mapMessage,
            UUID.fromString(studentId),
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
   * Lists active connections and ended connections with visible history. A student's deletion
   * timestamp hides their earlier messages without affecting the other participant's view.
   */
  public List<ChatSummary> findSummaries(String studentId) {
    UUID student = UUID.fromString(studentId);
    List<ChatSummary> summaries =
        jdbc.query(
            """
                SELECT c.connection_id,
                       CASE WHEN c.student_a_id = ? THEN c.student_b_id ELSE c.student_a_id END
                         AS partner_id,
                       c.active,
                       (d.deleted_at IS NOT NULL AND latest.message_id IS NULL) AS hidden,
                       latest.message_id, latest.sender_id, latest.content, latest.sent_at,
                       CASE WHEN c.active THEN
                         (SELECT count(*)
                          FROM connection_chat_messages unread
                          WHERE unread.connection_id = c.connection_id
                            AND unread.sender_id <> ?
                            AND unread.sent_at > greatest(
                              coalesce(r.last_read_at, '-infinity'),
                              coalesce(d.deleted_at, '-infinity')))
                       ELSE 0 END AS unread_count
                FROM study_buddy_connections c
                LEFT JOIN connection_chat_reads r
                  ON r.connection_id = c.connection_id AND r.student_id = ?
                LEFT JOIN connection_chat_deletions d
                  ON d.connection_id = c.connection_id AND d.student_id = ?
                LEFT JOIN LATERAL (
                  SELECT message_id, sender_id, content, sent_at
                  FROM connection_chat_messages m
                  WHERE m.connection_id = c.connection_id
                    AND m.sent_at > coalesce(d.deleted_at, '-infinity')
                  ORDER BY sent_at DESC, message_id DESC
                  LIMIT 1
                ) latest ON true
                WHERE (c.student_a_id = ? OR c.student_b_id = ?)
                  AND (c.active OR latest.message_id IS NOT NULL)
                ORDER BY latest.sent_at DESC NULLS LAST, c.started_at DESC
                """,
            (resultSet, rowNumber) -> {
              String connectionId = resultSet.getString("connection_id");
              ChatMessage lastMessage =
                  resultSet.getString(MESSAGE_ID_COLUMN) == null
                      ? null
                      : new ChatMessage(
                          resultSet.getString(MESSAGE_ID_COLUMN),
                          connectionId,
                          resultSet.getString("sender_id"),
                          resultSet.getString("content"),
                          resultSet.getTimestamp("sent_at").toInstant());
              return new ChatSummary(
                  connectionId,
                  resultSet.getString("partner_id"),
                  resultSet.getBoolean("active"),
                  resultSet.getBoolean("hidden"),
                  lastMessage,
                  resultSet.getInt("unread_count"));
            },
            student,
            student,
            student,
            student,
            student,
            student);
    return List.copyOf(summaries);
  }

  /** Hides existing history for this student without deleting shared messages. */
  public void deleteForStudent(String connectionId, String studentId) {
    jdbc.update(
        """
            INSERT INTO connection_chat_deletions(connection_id, student_id, deleted_at)
            VALUES (?, ?, now())
            ON CONFLICT (connection_id, student_id)
            DO UPDATE SET deleted_at = EXCLUDED.deleted_at
            """,
        UUID.fromString(connectionId),
        UUID.fromString(studentId));
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
        resultSet.getString(MESSAGE_ID_COLUMN),
        resultSet.getString("connection_id"),
        resultSet.getString("sender_id"),
        resultSet.getString("content"),
        sentAt.toInstant());
  }
}
