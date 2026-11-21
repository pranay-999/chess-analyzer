package com.chessanalyzer;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSockets keep ONE connection open between browser and server, so the server
 * can push messages at any moment ("your opponent moved") instead of the browser
 * polling every second.
 *
 * STOMP is a small messaging protocol on top of it: the browser SUBSCRIBEs to
 * "destinations" like /topic/games/k3f9x2qp, and the server sends to them with
 * SimpMessagingTemplate.convertAndSend(...). The "simple broker" below is an
 * in-memory message router inside Spring (fine for one server).
 *
 * The /ws endpoint is protected by SecurityConfig (login required), and the
 * handshake is an ordinary HTTP request, so the session cookie identifies the user.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws");   // same-origin only (the default): other websites can't connect
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }
}
