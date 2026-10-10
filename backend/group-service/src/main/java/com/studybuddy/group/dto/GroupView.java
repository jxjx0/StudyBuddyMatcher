package com.studybuddy.group.dto;

import com.studybuddy.group.model.*;
import java.util.List;

public record GroupView(
    String groupId,
    String groupName,
    String description,
    String courseCode,
    List<StudyGoal> studyGoals,
    MeetingMode meetingMode,
    List<TimeSlot> availability,
    int maxGroupSize,
    int memberCount,
    GroupStatus status,
    String leaderId,
    List<Member> members,
    boolean isLeader,
    boolean isMember,
    boolean leaderAvailable,
    boolean canRequest,
    GroupJoinRequestStatus myRequestStatus) {
  public record Member(String studentId, String name, boolean available) {}
}
