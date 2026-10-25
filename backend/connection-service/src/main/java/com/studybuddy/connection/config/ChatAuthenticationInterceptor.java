package com.studybuddy.connection.config;

import com.studybuddy.connection.service.ChatService;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;

@Component
public class ChatAuthenticationInterceptor implements ChannelInterceptor {
  private final JwtDecoder jwtDecoder;
  private final ChatService chatService;

  public ChatAuthenticationInterceptor(JwtDecoder jwtDecoder, ChatService chatService) {
    this.jwtDecoder = jwtDecoder;
    this.chatService = chatService;
  }

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor =
        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
    if (accessor == null || accessor.getMessageType() == SimpMessageType.HEARTBEAT) {
      return message;
    }

    if (accessor.getCommand() == StompCommand.CONNECT) {
      authenticate(accessor, message);
      return message;
    }

    Principal principal = accessor.getUser();
    if (principal == null) {
      throw new MessageDeliveryException(message, "Authentication is required");
    }

    if (accessor.getCommand() == StompCommand.SUBSCRIBE) {
      UUID connectionId = connectionIdFromTopic(accessor.getDestination(), message);
      chatService.requireActiveParticipant(connectionId.toString(), principal.getName());
    } else if (accessor.getCommand() == StompCommand.SEND) {
      String destination = accessor.getDestination();
      if (destination == null
          || !destination.matches("^/app/connections/[0-9a-fA-F-]{36}/send$")) {
        throw new MessageDeliveryException(message, "Unsupported chat destination");
      }
    } else if (accessor.getCommand() != StompCommand.DISCONNECT
        && accessor.getCommand() != StompCommand.UNSUBSCRIBE
        && accessor.getCommand() != StompCommand.ACK
        && accessor.getCommand() != StompCommand.NACK) {
      throw new MessageDeliveryException(message, "Unsupported chat action");
    }
    return message;
  }

  private void authenticate(StompHeaderAccessor accessor, Message<?> message) {
    List<String> authorization = accessor.getNativeHeader("Authorization");
    if (authorization == null || authorization.size() != 1) {
      throw new MessageDeliveryException("Bearer token is required");
    }
    String header = authorization.getFirst();
    if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
      throw new MessageDeliveryException("Bearer token is required");
    }
    try {
      Jwt jwt = jwtDecoder.decode(header.substring(7).trim());
      List<String> roles = jwt.getClaimAsStringList("roles");
      String studentId = UUID.fromString(jwt.getClaimAsString("uid")).toString();
      if (roles == null || !roles.contains("ROLE_STUDENT")) {
        throw new MessageDeliveryException("Only students can use chat");
      }
      accessor.setUser((Principal) () -> studentId);
    } catch (MessageDeliveryException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new MessageDeliveryException(message, "Invalid chat credentials", exception);
    }
  }

  private UUID connectionIdFromTopic(String destination, Message<?> message) {
    if (destination == null
        || !destination.matches("^/topic/connections/[0-9a-fA-F-]{36}$")) {
      throw new MessageDeliveryException("Unsupported chat subscription");
    }
    try {
      return UUID.fromString(destination.substring("/topic/connections/".length()));
    } catch (IllegalArgumentException exception) {
      throw new MessageDeliveryException(message, "Invalid connection", exception);
    }
  }
}
