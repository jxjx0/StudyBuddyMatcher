package com.studybuddy.connection.dto;

import java.time.Instant;

public record ChatMessage(
    String messageId, String connectionId, String senderId, String content, Instant sentAt) {}
