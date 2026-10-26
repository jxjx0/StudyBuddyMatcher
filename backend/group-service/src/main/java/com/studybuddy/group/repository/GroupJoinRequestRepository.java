package com.studybuddy.group.repository;

import com.studybuddy.group.model.GroupJoinRequest;
import com.studybuddy.group.model.GroupJoinRequestStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class GroupJoinRequestRepository {
  private final JdbcTemplate jdbc;

  public GroupJoinRequestRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<GroupJoinRequest> findById(String requestId) {
    return findRequest(requestId, false);
  }

  public Optional<GroupJoinRequest> lockById(String requestId) {
    return findRequest(requestId, true);
  }

  private Optional<GroupJoinRequest> findRequest(String requestId, boolean lockForUpdate) {
    String sql = "SELECT * FROM group_join_requests WHERE group_join_request_id=?";
    if (lockForUpdate) {
      sql += " FOR UPDATE";
    }
    List<GroupJoinRequest> requests = jdbc.query(sql, this::mapRequest, UUID.fromString(requestId));
    if (requests.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(requests.getFirst());
  }

  public List<GroupJoinRequest> findByGroupId(String groupId) {
    return jdbc.query(
        """
            SELECT * FROM group_join_requests WHERE group_id=?
            ORDER BY created_at DESC, group_join_request_id
            """,
        this::mapRequest,
        UUID.fromString(groupId));
  }

  public List<GroupJoinRequest> findByStudentId(String studentId) {
    return jdbc.query(
        """
            SELECT * FROM group_join_requests WHERE student_id=?
            ORDER BY created_at DESC, group_join_request_id
            """,
        this::mapRequest,
        UUID.fromString(studentId));
  }

  /** Caller holds the group lock before locking the request. */
  public Optional<GroupJoinRequest> lockPendingByGroupAndStudent(String groupId, String studentId) {
    List<GroupJoinRequest> requests =
        jdbc.query(
            """
                SELECT * FROM group_join_requests
                WHERE group_id=? AND student_id=? AND status='PENDING'
                FOR UPDATE
                """,
            this::mapRequest,
            UUID.fromString(groupId),
            UUID.fromString(studentId));
    return requests.stream().findFirst();
  }

  /** The existing partial unique index checks for a duplicate in the insert itself. */
  public boolean insertPending(GroupJoinRequest request) {
    if (!request.isPending()) {
      throw new IllegalArgumentException("Only pending requests can be created");
    }
    return jdbc.update(
            """
            INSERT INTO group_join_requests(group_join_request_id,student_id,group_id,status,created_at)
            VALUES (?,?,?,'PENDING',?)
            ON CONFLICT (group_id,student_id) WHERE status='PENDING' DO NOTHING
            """,
            UUID.fromString(request.getGroupJoinRequestId()),
            UUID.fromString(request.getStudentId()),
            UUID.fromString(request.getGroupId()),
            Timestamp.from(request.getCreatedAt()))
        == 1;
  }

  private GroupJoinRequest mapRequest(ResultSet resultSet, int rowNumber) throws SQLException {
    Timestamp respondedAt = resultSet.getTimestamp("responded_at");
    return GroupJoinRequest.restore(
        resultSet.getString("group_join_request_id"),
        resultSet.getString("student_id"),
        resultSet.getString("group_id"),
        GroupJoinRequestStatus.valueOf(resultSet.getString("status")),
        resultSet.getTimestamp("created_at").toInstant(),
        respondedAt == null ? null : respondedAt.toInstant());
  }

  public GroupJoinRequest save(GroupJoinRequest request) {
    Timestamp respondedAt = null;
    if (request.getRespondedAt() != null) {
      respondedAt = Timestamp.from(request.getRespondedAt());
    }
    jdbc.update(
        """
            INSERT INTO group_join_requests(group_join_request_id,student_id,group_id,status,created_at,responded_at)
            VALUES (?,?,?,?,?,?)
            ON CONFLICT (group_join_request_id) DO UPDATE
            SET status=EXCLUDED.status,responded_at=EXCLUDED.responded_at
            """,
        UUID.fromString(request.getGroupJoinRequestId()),
        UUID.fromString(request.getStudentId()),
        UUID.fromString(request.getGroupId()),
        request.getStatus().name(),
        Timestamp.from(request.getCreatedAt()),
        respondedAt);
    return request;
  }
}
