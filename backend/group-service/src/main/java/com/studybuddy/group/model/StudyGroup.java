package com.studybuddy.group.model;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** A group owns its membership rules. The leader occupies one place. */
public class StudyGroup {
  private final String groupId;
  private final String courseCode;
  private final String leaderId;
  private String groupName;
  private String description;
  private int maxGroupSize;
  private GroupStatus status;
  private MeetingMode meetingMode;
  private final List<String> memberIds;
  private List<StudyGoal> studyGoals;
  private List<TimeSlot> availability;

  private StudyGroup(
      String groupId,
      String courseCode,
      String leaderId,
      String groupName,
      String description,
      int capacity,
      GroupStatus status,
      MeetingMode meetingMode,
      List<StudyGoal> studyGoals,
      List<TimeSlot> availability,
      List<String> memberIds) {
    this.groupId = UUID.fromString(groupId).toString();
    if (courseCode == null || courseCode.isBlank() || courseCode.length() > 32) {
      throw new IllegalArgumentException("Choose a valid course");
    }
    this.courseCode = courseCode.trim();
    this.leaderId = UUID.fromString(leaderId).toString();
    this.memberIds = new ArrayList<>(memberIds);
    boolean leaderIsMember = this.memberIds.contains(this.leaderId);
    boolean membersAreUnique = new HashSet<>(memberIds).size() == memberIds.size();
    if (!leaderIsMember || !membersAreUnique) {
      throw new IllegalArgumentException("The leader must be a member and members must be unique");
    }

    // Validate saved details using the same rules as creation, then restore the saved status.
    this.status = GroupStatus.ACTIVE;
    updateDetails(groupName, description, capacity, meetingMode, studyGoals, availability);
    this.status = Objects.requireNonNull(status);
  }

  public static StudyGroup create(
      String groupName,
      String description,
      String courseCode,
      String leaderId,
      int capacity,
      MeetingMode meetingMode,
      List<StudyGoal> studyGoals,
      List<TimeSlot> availability) {
    return new StudyGroup(
        UUID.randomUUID().toString(),
        courseCode,
        leaderId,
        groupName,
        description,
        capacity,
        GroupStatus.ACTIVE,
        meetingMode,
        studyGoals,
        availability,
        List.of(leaderId));
  }

  /** Restore saved state without replaying membership or request actions. */
  public static StudyGroup restore(
      String groupId,
      String courseCode,
      String leaderId,
      String groupName,
      String description,
      int capacity,
      GroupStatus status,
      MeetingMode meetingMode,
      List<StudyGoal> studyGoals,
      List<TimeSlot> availability,
      List<String> memberIds) {
    return new StudyGroup(
        groupId,
        courseCode,
        leaderId,
        groupName,
        description,
        capacity,
        status,
        meetingMode,
        studyGoals,
        availability,
        memberIds);
  }

  public void updateDetails(
      String groupName,
      String description,
      int capacity,
      MeetingMode meetingMode,
      List<StudyGoal> studyGoals,
      List<TimeSlot> availability) {
    requireActive();
    if (groupName == null || groupName.isBlank() || groupName.trim().length() > 100) {
      throw new IllegalArgumentException("Group name must contain 1 to 100 characters");
    }
    if (description != null && description.length() > 2000) {
      throw new IllegalArgumentException("Description must be at most 2000 characters");
    }
    if (capacity < 2) {
      throw new IllegalArgumentException("Group capacity must be at least 2");
    }
    if (capacity < memberIds.size()) {
      throw new IllegalStateException("Capacity cannot be smaller than current membership");
    }

    List<StudyGoal> updatedGoals = List.of();
    if (studyGoals != null) {
      updatedGoals = List.copyOf(new LinkedHashSet<>(studyGoals));
    }
    List<TimeSlot> updatedAvailability = copySlots(availability == null ? List.of() : availability);
    for (TimeSlot slot : updatedAvailability) {
      validateSlot(slot);
    }

    this.groupName = groupName.trim();
    this.description = description == null ? "" : description.trim();
    this.maxGroupSize = capacity;
    this.meetingMode = meetingMode == null ? MeetingMode.EITHER : meetingMode;
    this.studyGoals = updatedGoals;
    this.availability = updatedAvailability;
  }

  private void validateSlot(TimeSlot slot) {
    try {
      DayOfWeek.valueOf(slot.getDay());
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("Choose a valid availability day");
    }
    if (slot.getStartTime() == null
        || slot.getEndTime() == null
        || !slot.getEndTime().isAfter(slot.getStartTime())) {
      throw new IllegalArgumentException("Availability end must be after start on the same day");
    }
  }

  public void updateGroupInfo(String groupName, String description, int capacity) {
    updateDetails(groupName, description, capacity, meetingMode, studyGoals, availability);
  }

  public void addMember(String studentId) {
    requireActive();
    UUID.fromString(studentId);
    if (hasMember(studentId)) {
      throw new IllegalStateException("Student is already a member");
    }
    if (isFull()) {
      throw new IllegalStateException("This group is full");
    }
    memberIds.add(studentId);
  }

  public void removeMember(String studentId) {
    requireActive();
    if (isLedBy(studentId)) {
      throw new IllegalStateException("The leader cannot be removed");
    }
    if (!memberIds.remove(studentId)) {
      throw new IllegalStateException("Student is not a member");
    }
  }

  public void addStudyGoal(StudyGoal goal) {
    List<StudyGoal> updatedGoals = new ArrayList<>(studyGoals);
    updatedGoals.add(Objects.requireNonNull(goal));
    updateDetails(groupName, description, maxGroupSize, meetingMode, updatedGoals, availability);
  }

  public void removeStudyGoal(StudyGoal goal) {
    List<StudyGoal> updatedGoals = new ArrayList<>(studyGoals);
    updatedGoals.remove(goal);
    updateDetails(groupName, description, maxGroupSize, meetingMode, updatedGoals, availability);
  }

  public void addAvailability(TimeSlot slot) {
    List<TimeSlot> updatedAvailability = new ArrayList<>(availability);
    updatedAvailability.add(slot);
    updateDetails(
        groupName, description, maxGroupSize, meetingMode, studyGoals, updatedAvailability);
  }

  public void removeAvailability(TimeSlot slotToRemove) {
    List<TimeSlot> updatedAvailability = new ArrayList<>();
    for (TimeSlot slot : availability) {
      boolean sameDay = Objects.equals(slot.getDay(), slotToRemove.getDay());
      boolean sameStart = Objects.equals(slot.getStartTime(), slotToRemove.getStartTime());
      boolean sameEnd = Objects.equals(slot.getEndTime(), slotToRemove.getEndTime());
      if (!sameDay || !sameStart || !sameEnd) {
        updatedAvailability.add(slot);
      }
    }
    updateDetails(
        groupName, description, maxGroupSize, meetingMode, studyGoals, updatedAvailability);
  }

  private static List<TimeSlot> copySlots(List<TimeSlot> slots) {
    List<TimeSlot> copiedSlots = new ArrayList<>();
    for (TimeSlot slot : slots) {
      if (slot == null) {
        throw new IllegalArgumentException("Availability cannot contain an empty slot");
      }
      copiedSlots.add(new TimeSlot(slot.getDay(), slot.getStartTime(), slot.getEndTime()));
    }
    return List.copyOf(copiedSlots);
  }

  public void requireActive() {
    if (!isActive()) {
      throw new IllegalStateException("This group is closed");
    }
  }

  public boolean isFull() {
    return memberIds.size() >= maxGroupSize;
  }

  public boolean isLedBy(String studentId) {
    return leaderId.equals(studentId);
  }

  public boolean hasMember(String studentId) {
    return memberIds.contains(studentId);
  }

  public void close() {
    status = GroupStatus.CLOSED;
  }

  public boolean isActive() {
    return status == GroupStatus.ACTIVE;
  }

  public String getGroupId() {
    return groupId;
  }

  public String getGroupName() {
    return groupName;
  }

  public String getDescription() {
    return description;
  }

  public String getCourseCode() {
    return courseCode;
  }

  public int getMaxGroupSize() {
    return maxGroupSize;
  }

  public GroupStatus getStatus() {
    return status;
  }

  public String getLeaderId() {
    return leaderId;
  }

  public MeetingMode getMeetingMode() {
    return meetingMode;
  }

  public List<String> getMemberIds() {
    return List.copyOf(memberIds);
  }

  public List<StudyGoal> getStudyGoals() {
    return List.copyOf(studyGoals);
  }

  public List<TimeSlot> getAvailability() {
    return copySlots(availability);
  }
}
