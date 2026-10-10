package com.studybuddy.group.repository;

import com.studybuddy.group.model.*;
import tools.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.*;
import org.springframework.transaction.annotation.*;
import java.util.*;

@Repository
public class StudyGroupRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public StudyGroupRepository(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc=jdbc; this.mapper=mapper; }
    public List<StudyGroup> findAll() {
        // Fetch memberships in the same statement, instead of a cloud round trip per group.
        // One SELECT also keeps the group and its members in the same database snapshot.
        return jdbc.query("""
            SELECT g.*, ARRAY(
                SELECT m.student_id::text FROM study_group_members m
                WHERE m.group_id = g.group_id
                ORDER BY m.joined_at, m.student_id
            ) AS member_ids
            FROM study_groups g
            ORDER BY g.created_at DESC, g.group_id
            """, (rs, row) -> mapGroup(rs, List.of((String[]) rs.getArray("member_ids").getArray())));
    }
    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public Optional<StudyGroup> findById(String id) { return find(id, false); }
    public Optional<StudyGroup> lockById(String id) { return find(id, true); }
    private Optional<StudyGroup> find(String id, boolean lock) {
        return jdbc.query("SELECT * FROM study_groups WHERE group_id=?" + (lock ? " FOR UPDATE" : ""), this::map, UUID.fromString(id)).stream().findFirst();
    }
    private StudyGroup map(ResultSet rs, int row) throws SQLException {
        String id = rs.getString("group_id");
        // Separate statement after taking the group lock: see memberships committed by a previous lock holder.
        List<String> members = jdbc.queryForList("SELECT student_id::text FROM study_group_members WHERE group_id=? ORDER BY joined_at, student_id", String.class, UUID.fromString(id));
        return mapGroup(rs, members);
    }
    private StudyGroup mapGroup(ResultSet rs, List<String> members) throws SQLException {
        String id = rs.getString("group_id");
        List<StudyGoal> goals = Arrays.stream((String[])rs.getArray("study_goals").getArray()).map(StudyGoal::valueOf).toList();
        return StudyGroup.restore(id, rs.getString("course_code"), rs.getString("leader_id"), rs.getString("group_name"),
                rs.getString("description"), rs.getInt("max_group_size"), GroupStatus.valueOf(rs.getString("status")),
                MeetingMode.valueOf(rs.getString("meeting_mode")), goals,
                List.of(mapper.readValue(rs.getString("availability"), TimeSlot[].class)), members);
    }
    /** Caller holds the group lock for updates; creation and membership are in the same transaction. */
    public StudyGroup save(StudyGroup g) {
        jdbc.update("""
            INSERT INTO study_groups(group_id, group_name, description, course_code, leader_id, max_group_size, status, meeting_mode, study_goals, availability)
            VALUES (?,?,?,?,?,?,?,?,CAST(? AS text[]),CAST(? AS jsonb))
            ON CONFLICT (group_id) DO UPDATE SET group_name=EXCLUDED.group_name, description=EXCLUDED.description,
              max_group_size=EXCLUDED.max_group_size, status=EXCLUDED.status, meeting_mode=EXCLUDED.meeting_mode,
              study_goals=EXCLUDED.study_goals, availability=EXCLUDED.availability, updated_at=now()
            """, UUID.fromString(g.getGroupId()), g.getGroupName(), g.getDescription(), g.getCourseCode(), UUID.fromString(g.getLeaderId()),
                g.getMaxGroupSize(), g.getStatus().name(), g.getMeetingMode().name(), g.getStudyGoals().stream().map(Enum::name).toArray(String[]::new), mapper.writeValueAsString(g.getAvailability()));
        UUID id=UUID.fromString(g.getGroupId());
        for (String member : g.getMemberIds()) jdbc.update("INSERT INTO study_group_members(group_id,student_id) VALUES (?,?) ON CONFLICT DO NOTHING", id, UUID.fromString(member));
        jdbc.update("DELETE FROM study_group_members WHERE group_id=? AND NOT (student_id=ANY(CAST(? AS uuid[])))", id, g.getMemberIds().toArray(String[]::new));
        return g;
    }
}
