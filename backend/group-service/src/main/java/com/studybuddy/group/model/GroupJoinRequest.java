package com.studybuddy.group.model;

import java.time.Instant;
import java.util.UUID;

/** One student's request. Membership changes belong to the coordinating service. */
public class GroupJoinRequest {
  private final String groupJoinRequestId;
  private final String studentId;
  private final String groupId;
  private GroupJoinRequestStatus status;
  private final Instant createdAt;
  private Instant respondedAt;

  private GroupJoinRequest(
      String requestId,
      String studentId,
      String groupId,
      GroupJoinRequestStatus status,
      Instant createdAt,
      Instant respondedAt) {
    this.groupJoinRequestId = UUID.fromString(requestId).toString();
    this.studentId = UUID.fromString(studentId).toString();
    this.groupId = UUID.fromString(groupId).toString();
    boolean isPending = status == GroupJoinRequestStatus.PENDING;
    boolean hasNoResponse = respondedAt == null;
    if (status == null || createdAt == null || isPending != hasNoResponse) {
      throw new IllegalArgumentException("Invalid request history");
    }
    this.status = status;
    this.createdAt = createdAt;
    this.respondedAt = respondedAt;
  }

  public static GroupJoinRequest create(String studentId, String groupId) {
    return new GroupJoinRequest(
        UUID.randomUUID().toString(),
        studentId,
        groupId,
        GroupJoinRequestStatus.PENDING,
        Instant.now(),
        null);
  }

  public static GroupJoinRequest restore(
      String requestId,
      String studentId,
      String groupId,
      GroupJoinRequestStatus status,
      Instant createdAt,
      Instant respondedAt) {
    return new GroupJoinRequest(requestId, studentId, groupId, status, createdAt, respondedAt);
  }

  public void accept() {
    recordDecision(GroupJoinRequestStatus.ACCEPTED);
  }

  public void reject() {
    recordDecision(GroupJoinRequestStatus.REJECTED);
  }

  private void recordDecision(GroupJoinRequestStatus decision) {
    if (!isPending()) {
      throw new IllegalStateException("This request has already been decided");
    }
    status = decision;
    respondedAt = Instant.now();
  }

  public boolean isPending() {
    return status == GroupJoinRequestStatus.PENDING;
  }

  public String getGroupJoinRequestId() {
    return groupJoinRequestId;
  }

  public String getStudentId() {
    return studentId;
  }

  public String getGroupId() {
    return groupId;
  }

  public GroupJoinRequestStatus getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getRespondedAt() {
    return respondedAt;
  }
}
