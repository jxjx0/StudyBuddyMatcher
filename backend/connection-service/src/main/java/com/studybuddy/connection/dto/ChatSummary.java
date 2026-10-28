package com.studybuddy.connection.dto;

/** One conversation in a student's chat list. {@code lastMessage} is null before anyone writes. */
public record ChatSummary(String connectionId, ChatMessage lastMessage, int unreadCount) {}
