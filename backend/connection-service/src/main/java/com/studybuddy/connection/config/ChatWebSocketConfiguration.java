package com.studybuddy.connection.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class ChatWebSocketConfiguration implements WebSocketMessageBrokerConfigurer {
  private final ChatAuthenticationInterceptor authenticationInterceptor;
  private final String[] allowedOriginPatterns;

  public ChatWebSocketConfiguration(
      ChatAuthenticationInterceptor authenticationInterceptor,
      @Value("${app.websocket.allowed-origin-patterns}") String allowedOriginPatterns) {
    this.authenticationInterceptor = authenticationInterceptor;
    this.allowedOriginPatterns = allowedOriginPatterns.split(",");
    for (int index = 0; index < this.allowedOriginPatterns.length; index++) {
      this.allowedOriginPatterns[index] = this.allowedOriginPatterns[index].trim();
    }
  }

  @Bean
  ThreadPoolTaskScheduler chatHeartbeatTaskScheduler() {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.setThreadNamePrefix("chat-heartbeat-");
    return scheduler;
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry
        .enableSimpleBroker("/topic")
        .setHeartbeatValue(new long[] {10_000, 10_000})
        .setTaskScheduler(chatHeartbeatTaskScheduler());
    registry.setApplicationDestinationPrefixes("/app");
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.addEndpoint("/ws").setAllowedOriginPatterns(allowedOriginPatterns);
  }

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(authenticationInterceptor);
  }
}
