package com.studybuddy.group;

import static org.junit.jupiter.api.Assertions.*;

import com.studybuddy.group.model.*;
import java.time.LocalTime;
import java.util.*;
import org.junit.jupiter.api.Test;

class GroupDomainTest {
  @Test
  void goalsKeepTheirOrderAndAvailabilityCopiesCannotChangeSavedState() {
    var studyGroup = group();
    studyGroup.updateDetails(
        "Revision",
        "",
        3,
        null,
        List.of(StudyGoal.CONCEPT_REVIEW, StudyGoal.EXAM_PREPARATION, StudyGoal.CONCEPT_REVIEW),
        List.of());
    assertEquals(
        List.of(StudyGoal.CONCEPT_REVIEW, StudyGoal.EXAM_PREPARATION), studyGroup.getStudyGoals());
    assertEquals(MeetingMode.EITHER, studyGroup.getMeetingMode());

    var monday = new TimeSlot("MONDAY", LocalTime.of(18, 0), LocalTime.of(19, 0));
    var tuesday = new TimeSlot("TUESDAY", LocalTime.of(18, 0), LocalTime.of(19, 0));
    studyGroup.addAvailability(monday);
    studyGroup.addAvailability(tuesday);
    studyGroup.addAvailability(monday);
    studyGroup.removeAvailability(monday);
    assertEquals(1, studyGroup.getAvailability().size());
    assertEquals("TUESDAY", studyGroup.getAvailability().getFirst().getDay());
    assertThrows(UnsupportedOperationException.class, () -> studyGroup.getAvailability().clear());
    assertThrows(UnsupportedOperationException.class, () -> studyGroup.getStudyGoals().clear());
  }

  private final String leader = UUID.randomUUID().toString(),
      applicant = UUID.randomUUID().toString();

  private StudyGroup group() {
    return StudyGroup.create(
        " Revision ", null, "IS442", leader, 2, MeetingMode.ONLINE, List.of(), List.of());
  }

  @Test
  void leaderCountsAndCapacityCannotBeExceeded() {
    var g = group();
    assertEquals("Revision", g.getGroupName());
    assertEquals(List.of(leader), g.getMemberIds());
    g.addMember(applicant);
    assertTrue(g.isFull());
    assertThrows(IllegalStateException.class, () -> g.addMember(UUID.randomUUID().toString()));
    assertThrows(IllegalStateException.class, () -> g.addMember(applicant));
    assertThrows(IllegalStateException.class, () -> g.removeMember(leader));
    g.removeMember(applicant);
    assertFalse(g.isFull());
  }

  @Test
  void shrinkingCannotEvictMembersAndClosureBlocksChanges() {
    var g = group();
    g.updateGroupInfo("Revision", "", 3);
    g.addMember(applicant);
    g.addMember(UUID.randomUUID().toString());
    assertThrows(IllegalStateException.class, () -> g.updateGroupInfo("Revision", "", 2));
    g.close();
    g.close();
    assertFalse(g.isActive());
    assertThrows(IllegalStateException.class, () -> g.removeMember(applicant));
    assertThrows(IllegalStateException.class, () -> g.updateGroupInfo("Edited", "", 3));
  }

  @Test
  void invalidInputsAndMutableCollectionsCannotBypassRules() {
    var g = group();
    assertThrows(IllegalArgumentException.class, () -> g.updateGroupInfo(" ", "", 2));
    assertThrows(IllegalArgumentException.class, () -> g.updateGroupInfo("Name", "", 1));
    assertThrows(UnsupportedOperationException.class, () -> g.getMemberIds().clear());
    var slot = new TimeSlot("MONDAY", LocalTime.of(18, 0), LocalTime.of(19, 0));
    g.addAvailability(slot);
    slot.setDay("INVALID");
    g.getAvailability().getFirst().setDay("INVALID");
    assertEquals("MONDAY", g.getAvailability().getFirst().getDay());
    assertThrows(IllegalArgumentException.class, () -> g.addAvailability(slot));
    assertThrows(
        IllegalArgumentException.class,
        () -> g.addAvailability(new TimeSlot("MONDAY", LocalTime.NOON, LocalTime.NOON)));
  }

  @Test
  void decisionsAreTerminalAndRestorePreservesHistory() {
    var r = GroupJoinRequest.create(applicant, UUID.randomUUID().toString());
    assertTrue(r.isPending());
    assertNull(r.getRespondedAt());
    r.accept();
    assertEquals(GroupJoinRequestStatus.ACCEPTED, r.getStatus());
    assertNotNull(r.getRespondedAt());
    assertThrows(IllegalStateException.class, r::reject);
    assertThrows(IllegalStateException.class, r::accept);
    var loaded =
        GroupJoinRequest.restore(
            r.getGroupJoinRequestId(),
            applicant,
            r.getGroupId(),
            r.getStatus(),
            r.getCreatedAt(),
            r.getRespondedAt());
    assertEquals(r.getRespondedAt(), loaded.getRespondedAt());
    assertFalse(loaded.isPending());
    var rejected = GroupJoinRequest.create(applicant, r.getGroupId());
    rejected.reject();
    assertEquals(GroupJoinRequestStatus.REJECTED, rejected.getStatus());
    var cancelled = GroupJoinRequest.create(applicant, r.getGroupId());
    cancelled.cancel();
    assertEquals(GroupJoinRequestStatus.CANCELLED, cancelled.getStatus());
    assertNotNull(cancelled.getRespondedAt());
    assertThrows(IllegalStateException.class, cancelled::cancel);
  }
}
