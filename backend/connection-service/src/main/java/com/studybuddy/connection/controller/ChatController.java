package com.studybuddy.connection.controller;

import com.studybuddy.connection.dto.ChatMessage;
import com.studybuddy.connection.dto.SendChatMessageRequest;
import com.studybuddy.connection.service.ChatService;
import java.security.Principal;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

@Controller
@Validated
public class ChatController {
  private final ChatService chatService;
  private final SimpMessagingTemplate messagingTemplate;

  public ChatController(ChatService chatService, SimpMessagingTemplate messagingTemplate) {
    this.chatService = chatService;
    this.messagingTemplate = messagingTemplate;
  }

  @MessageMapping("/connections/{connectionId}/send")
  public void send(
      @DestinationVariable UUID connectionId,
      @Payload @Valid SendChatMessageRequest request,
      Principal principal) {
    ChatMessage message =
        chatService.send(connectionId.toString(), principal.getName(), request.content());
    messagingTemplate.convertAndSend("/topic/connections/" + connectionId, message);
  }
}
