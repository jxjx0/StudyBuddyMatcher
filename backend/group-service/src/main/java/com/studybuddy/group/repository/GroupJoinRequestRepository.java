package com.studybuddy.group.repository;

import com.studybuddy.group.model.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.*;
import java.util.*;

@Repository
public class GroupJoinRequestRepository {
    private final JdbcTemplate jdbc;
    public GroupJoinRequestRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public Optional<GroupJoinRequest> findById(String id) { return find(id,false); }
    public Optional<GroupJoinRequest> lockById(String id) { return find(id,true); }
    private Optional<GroupJoinRequest> find(String id, boolean lock) {
        return jdbc.query("SELECT * FROM group_join_requests WHERE group_join_request_id=?"+(lock ? " FOR UPDATE" : ""), this::map, UUID.fromString(id)).stream().findFirst();
    }
    public List<GroupJoinRequest> findByGroupId(String id) { return jdbc.query("SELECT * FROM group_join_requests WHERE group_id=? ORDER BY created_at DESC, group_join_request_id", this::map, UUID.fromString(id)); }
    public List<GroupJoinRequest> findByStudentId(String id) { return jdbc.query("SELECT * FROM group_join_requests WHERE student_id=? ORDER BY created_at DESC, group_join_request_id", this::map, UUID.fromString(id)); }
    private GroupJoinRequest map(ResultSet rs,int row) throws SQLException {
        Timestamp responded=rs.getTimestamp("responded_at");
        return GroupJoinRequest.restore(rs.getString("group_join_request_id"), rs.getString("student_id"), rs.getString("group_id"),
            GroupJoinRequestStatus.valueOf(rs.getString("status")), rs.getTimestamp("created_at").toInstant(), responded == null ? null : responded.toInstant());
    }
    public GroupJoinRequest save(GroupJoinRequest r) {
        jdbc.update("""
            INSERT INTO group_join_requests(group_join_request_id,student_id,group_id,status,created_at,responded_at) VALUES (?,?,?,?,?,?)
            ON CONFLICT (group_join_request_id) DO UPDATE SET status=EXCLUDED.status,responded_at=EXCLUDED.responded_at
            """, UUID.fromString(r.getGroupJoinRequestId()), UUID.fromString(r.getStudentId()), UUID.fromString(r.getGroupId()), r.getStatus().name(), Timestamp.from(r.getCreatedAt()), r.getRespondedAt()==null ? null : Timestamp.from(r.getRespondedAt()));
        return r;
    }
}
