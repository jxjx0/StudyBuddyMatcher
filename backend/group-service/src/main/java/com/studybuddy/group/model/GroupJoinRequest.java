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

    private GroupJoinRequest(String id, String student, String group, GroupJoinRequestStatus status,
                             Instant created, Instant responded) {
        groupJoinRequestId = UUID.fromString(id).toString();
        studentId = UUID.fromString(student).toString();
        groupId = UUID.fromString(group).toString();
        if (status == null || created == null || (status == GroupJoinRequestStatus.PENDING) != (responded == null))
            throw new IllegalArgumentException("Invalid request history");
        this.status = status; createdAt = created; respondedAt = responded;
    }
    public static GroupJoinRequest create(String student, String group) {
        return new GroupJoinRequest(UUID.randomUUID().toString(), student, group, GroupJoinRequestStatus.PENDING, Instant.now(), null);
    }
    public static GroupJoinRequest restore(String id, String student, String group, GroupJoinRequestStatus status, Instant created, Instant responded) {
        return new GroupJoinRequest(id, student, group, status, created, responded);
    }
    public void accept() { decide(GroupJoinRequestStatus.ACCEPTED); }
    public void reject() { decide(GroupJoinRequestStatus.REJECTED); }
    private void decide(GroupJoinRequestStatus decision) {
        if (!isPending()) throw new IllegalStateException("This request has already been decided");
        status = decision; respondedAt = Instant.now();
    }
    public boolean isPending() { return status == GroupJoinRequestStatus.PENDING; }
    public String getGroupJoinRequestId() { return groupJoinRequestId; }
    public String getStudentId() { return studentId; }
    public String getGroupId() { return groupId; }
    public GroupJoinRequestStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getRespondedAt() { return respondedAt; }
}
