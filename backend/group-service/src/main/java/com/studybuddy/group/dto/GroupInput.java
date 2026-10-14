package com.studybuddy.group.dto;

import com.studybuddy.group.model.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record GroupInput(
    @NotBlank @Size(max = 100) String groupName,
    @Size(max = 2000) String description,
    @Size(max = 32) String courseCode,
    List<@NotNull StudyGoal> studyGoals,
    MeetingMode meetingMode,
    List<@NotNull @Valid TimeSlot> availability,
    @Min(2) Integer maxGroupSize) {
  public int capacity() {
    return maxGroupSize == null ? 5 : maxGroupSize;
  }
}
