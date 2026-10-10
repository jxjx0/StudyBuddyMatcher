package com.studybuddy.group.repository;

import com.studybuddy.group.model.*;
import tools.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class StudyGroupRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public StudyGroupRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public List<StudyGroup> findAll() {
        // One statement avoids a cloud round trip per group and reads a consistent membership snapshot.
        return jdbc.query("""
            SELECT g.*, ARRAY(
                SELECT m.student_id::text FROM study_group_members m
                WHERE m.group_id = g.group_id
                ORDER BY m.joined_at, m.student_id
            ) AS member_ids
            FROM study_groups g
            ORDER BY g.created_at DESC, g.group_id
            """, (resultSet, rowNumber) -> {
                String[] memberIds = (String[]) resultSet.getArray("member_ids").getArray();
                return mapGroup(resultSet, List.of(memberIds));
            });
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Optional<StudyGroup> findById(String groupId) {
        return findGroup(groupId, false);
    }

    public Optional<StudyGroup> lockById(String groupId) {
        return findGroup(groupId, true);
    }

    private Optional<StudyGroup> findGroup(String groupId, boolean lockForUpdate) {
        String sql = "SELECT * FROM study_groups WHERE group_id=?";
        if (lockForUpdate) {
            sql += " FOR UPDATE";
        }
        List<StudyGroup> groups = jdbc.query(sql, this::mapGroupWithMembers, UUID.fromString(groupId));
        if (groups.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(groups.getFirst());
    }

    private StudyGroup mapGroupWithMembers(ResultSet resultSet, int rowNumber) throws SQLException {
        String groupId = resultSet.getString("group_id");
        // Read members after taking the group lock, so we see the previous lock holder's changes.
        List<String> memberIds = jdbc.queryForList("""
            SELECT student_id::text FROM study_group_members
            WHERE group_id=? ORDER BY joined_at, student_id
            """, String.class, UUID.fromString(groupId));
        return mapGroup(resultSet, memberIds);
    }

    private StudyGroup mapGroup(ResultSet resultSet, List<String> memberIds) throws SQLException {
        String[] savedGoals = (String[]) resultSet.getArray("study_goals").getArray();
        List<StudyGoal> studyGoals = new ArrayList<>();
        for (String goal : savedGoals) {
            studyGoals.add(StudyGoal.valueOf(goal));
        }
        TimeSlot[] availability = objectMapper.readValue(resultSet.getString("availability"), TimeSlot[].class);
        return StudyGroup.restore(
                resultSet.getString("group_id"),
                resultSet.getString("course_code"),
                resultSet.getString("leader_id"),
                resultSet.getString("group_name"),
                resultSet.getString("description"),
                resultSet.getInt("max_group_size"),
                GroupStatus.valueOf(resultSet.getString("status")),
                MeetingMode.valueOf(resultSet.getString("meeting_mode")),
                studyGoals,
                List.of(availability),
                memberIds);
    }

    /** Caller holds the group lock for updates; creation and membership are in the same transaction. */
    public StudyGroup save(StudyGroup group) {
        List<String> studyGoalNames = new ArrayList<>();
        for (StudyGoal goal : group.getStudyGoals()) {
            studyGoalNames.add(goal.name());
        }
        jdbc.update("""
            INSERT INTO study_groups(group_id, group_name, description, course_code, leader_id, max_group_size, status, meeting_mode, study_goals, availability)
            VALUES (?,?,?,?,?,?,?,?,CAST(? AS text[]),CAST(? AS jsonb))
            ON CONFLICT (group_id) DO UPDATE SET group_name=EXCLUDED.group_name, description=EXCLUDED.description,
              max_group_size=EXCLUDED.max_group_size, status=EXCLUDED.status, meeting_mode=EXCLUDED.meeting_mode,
              study_goals=EXCLUDED.study_goals, availability=EXCLUDED.availability, updated_at=now()
            """,
                UUID.fromString(group.getGroupId()),
                group.getGroupName(),
                group.getDescription(),
                group.getCourseCode(),
                UUID.fromString(group.getLeaderId()),
                group.getMaxGroupSize(),
                group.getStatus().name(),
                group.getMeetingMode().name(),
                studyGoalNames.toArray(String[]::new),
                objectMapper.writeValueAsString(group.getAvailability()));
        saveMembers(group);
        return group;
    }

    private void saveMembers(StudyGroup group) {
        UUID groupId = UUID.fromString(group.getGroupId());
        for (String memberId : group.getMemberIds()) {
            jdbc.update("""
                INSERT INTO study_group_members(group_id,student_id)
                VALUES (?,?) ON CONFLICT DO NOTHING
                """, groupId, UUID.fromString(memberId));
        }
        jdbc.update("DELETE FROM study_group_members WHERE group_id=? AND NOT (student_id=ANY(CAST(? AS uuid[])))",
                groupId, group.getMemberIds().toArray(String[]::new));
    }
}
