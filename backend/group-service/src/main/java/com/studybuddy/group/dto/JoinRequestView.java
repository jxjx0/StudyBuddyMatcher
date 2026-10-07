package com.studybuddy.group.dto;

import com.studybuddy.group.model.GroupJoinRequestStatus;
import java.time.Instant;

public record JoinRequestView(String groupJoinRequestId, String studentId, String studentName,
                              String groupId, String groupName, GroupJoinRequestStatus status,
                              Instant createdAt, Instant respondedAt) { }
