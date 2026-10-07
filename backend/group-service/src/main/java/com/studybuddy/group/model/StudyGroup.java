package com.studybuddy.group.model;

import java.time.DayOfWeek;
import java.util.*;

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

    private StudyGroup(String id, String course, String leader, String name, String description,
                       int capacity, GroupStatus status, MeetingMode mode, List<StudyGoal> goals,
                       List<TimeSlot> slots, List<String> members) {
        groupId = UUID.fromString(id).toString();
        if (course == null || course.isBlank() || course.length() > 32)
            throw new IllegalArgumentException("Choose a valid course");
        courseCode = course.trim();
        leaderId = UUID.fromString(leader).toString();
        memberIds = new ArrayList<>(members);
        if (!memberIds.contains(leaderId) || new HashSet<>(members).size() != members.size())
            throw new IllegalArgumentException("The leader must be a member and members must be unique");
        this.status = GroupStatus.ACTIVE;
        updateDetails(name, description, capacity, mode, goals, slots);
        this.status = Objects.requireNonNull(status);
    }

    public static StudyGroup create(String name, String description, String course, String leader,
                                    int capacity, MeetingMode mode, List<StudyGoal> goals, List<TimeSlot> slots) {
        return new StudyGroup(UUID.randomUUID().toString(), course, leader, name, description, capacity,
                GroupStatus.ACTIVE, mode, goals, slots, List.of(leader));
    }

    /** Restore saved state without replaying membership or request actions. */
    public static StudyGroup restore(String id, String course, String leader, String name, String description,
                                     int capacity, GroupStatus status, MeetingMode mode, List<StudyGoal> goals,
                                     List<TimeSlot> slots, List<String> members) {
        return new StudyGroup(id, course, leader, name, description, capacity, status, mode, goals, slots, members);
    }

    public void updateDetails(String name, String description, int capacity, MeetingMode mode,
                              List<StudyGoal> goals, List<TimeSlot> slots) {
        requireActive();
        if (name == null || name.isBlank() || name.trim().length() > 100)
            throw new IllegalArgumentException("Group name must contain 1 to 100 characters");
        if (description != null && description.length() > 2000)
            throw new IllegalArgumentException("Description must be at most 2000 characters");
        if (capacity < 2) throw new IllegalArgumentException("Group capacity must be at least 2");
        if (capacity < memberIds.size()) throw new IllegalStateException("Capacity cannot be smaller than current membership");
        List<StudyGoal> newGoals = goals == null ? List.of() : List.copyOf(new LinkedHashSet<>(goals));
        List<TimeSlot> newSlots = copySlots(slots == null ? List.of() : slots);
        for (TimeSlot slot : newSlots) {
            try { DayOfWeek.valueOf(slot.getDay()); }
            catch (RuntimeException e) { throw new IllegalArgumentException("Choose a valid availability day"); }
            if (slot.getStartTime() == null || slot.getEndTime() == null || !slot.getEndTime().isAfter(slot.getStartTime()))
                throw new IllegalArgumentException("Availability end must be after start on the same day");
        }
        this.groupName = name.trim();
        this.description = description == null ? "" : description.trim();
        this.maxGroupSize = capacity;
        this.meetingMode = mode == null ? MeetingMode.EITHER : mode;
        this.studyGoals = newGoals;
        this.availability = newSlots;
    }
    public void updateGroupInfo(String name, String description, int capacity) {
        updateDetails(name, description, capacity, meetingMode, studyGoals, availability);
    }
    public void addMember(String studentId) {
        requireActive();
        UUID.fromString(studentId);
        if (hasMember(studentId)) throw new IllegalStateException("Student is already a member");
        if (isFull()) throw new IllegalStateException("This group is full");
        memberIds.add(studentId);
    }
    public void removeMember(String studentId) {
        requireActive();
        if (isLedBy(studentId)) throw new IllegalStateException("The leader cannot be removed");
        if (!memberIds.remove(studentId)) throw new IllegalStateException("Student is not a member");
    }
    public void addStudyGoal(StudyGoal goal) {
        var goals = new ArrayList<>(studyGoals); goals.add(Objects.requireNonNull(goal));
        updateDetails(groupName, description, maxGroupSize, meetingMode, goals, availability);
    }
    public void removeStudyGoal(StudyGoal goal) {
        var goals = new ArrayList<>(studyGoals); goals.remove(goal);
        updateDetails(groupName, description, maxGroupSize, meetingMode, goals, availability);
    }
    public void addAvailability(TimeSlot slot) {
        var slots = new ArrayList<>(availability); slots.add(slot);
        updateDetails(groupName, description, maxGroupSize, meetingMode, studyGoals, slots);
    }
    public void removeAvailability(TimeSlot slot) {
        var slots = new ArrayList<>(availability);
        slots.removeIf(s -> Objects.equals(s.getDay(), slot.getDay()) && Objects.equals(s.getStartTime(), slot.getStartTime()) && Objects.equals(s.getEndTime(), slot.getEndTime()));
        updateDetails(groupName, description, maxGroupSize, meetingMode, studyGoals, slots);
    }
    private static List<TimeSlot> copySlots(List<TimeSlot> slots) {
        return slots.stream().map(s -> {
            if (s == null) throw new IllegalArgumentException("Availability cannot contain an empty slot");
            return new TimeSlot(s.getDay(), s.getStartTime(), s.getEndTime());
        }).toList();
    }
    public void requireActive() { if (!isActive()) throw new IllegalStateException("This group is closed"); }
    public boolean isFull() { return memberIds.size() >= maxGroupSize; }
    public boolean isLedBy(String id) { return leaderId.equals(id); }
    public boolean hasMember(String id) { return memberIds.contains(id); }
    public void close() { status = GroupStatus.CLOSED; }
    public boolean isActive() { return status == GroupStatus.ACTIVE; }
    public String getGroupId() { return groupId; }
    public String getGroupName() { return groupName; }
    public String getDescription() { return description; }
    public String getCourseCode() { return courseCode; }
    public int getMaxGroupSize() { return maxGroupSize; }
    public GroupStatus getStatus() { return status; }
    public String getLeaderId() { return leaderId; }
    public MeetingMode getMeetingMode() { return meetingMode; }
    public List<String> getMemberIds() { return List.copyOf(memberIds); }
    public List<StudyGoal> getStudyGoals() { return List.copyOf(studyGoals); }
    public List<TimeSlot> getAvailability() { return copySlots(availability); }
}
