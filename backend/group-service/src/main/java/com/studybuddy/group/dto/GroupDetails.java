package com.studybuddy.group.dto;

import java.util.List;

/** Group details and leader-only requests share the same student and group reads. */
public record GroupDetails(GroupView group, List<JoinRequestView> requests) {}
