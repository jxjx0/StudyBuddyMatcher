package com.studybuddy.connection.dto;

/** One conversation in a student's chat list. {@code lastMessage} is null before anyone writes. */
public record ChatSummary(
    String connectionId,
    String partnerId,
    boolean active,
    boolean hidden,
    ChatMessage lastMessage,
    int unreadCount) {}
