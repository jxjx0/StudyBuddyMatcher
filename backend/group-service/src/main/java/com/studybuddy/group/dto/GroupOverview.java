package com.studybuddy.group.dto;

import java.util.List;

/** The three group tabs share one set of student, group and request reads. */
public record GroupOverview(List<GroupView> groups, List<GroupView> mine, List<JoinRequestView> requests) { }
