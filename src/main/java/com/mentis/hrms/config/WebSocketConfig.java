package com.mentis.hrms.config;

import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.security.Principal;
import java.util.Map;

@Configuration
@EnableWebSocketMessageBroker
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue", "/user");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .addInterceptors(new SessionPrincipalHandshakeInterceptor())
                .withSockJS()
                .setStreamBytesLimit(512 * 1024)
                .setHttpMessageCacheSize(1000)
                .setDisconnectDelay(30 * 1000);

        registry.addEndpoint("/ws-native")
                .setAllowedOriginPatterns("*")
                .addInterceptors(new SessionPrincipalHandshakeInterceptor());
    }

    /**
     * Reads HttpSession attribute "userId" (set by the HRMS login)
     * and attaches it as a Principal on the WebSocket session.
     */
    public static class SessionPrincipalHandshakeInterceptor implements HandshakeInterceptor {
        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                       WebSocketHandler wsHandler, Map<String, Object> attributes) {
            if (request instanceof ServletServerHttpRequest sreq) {
                HttpSession session = sreq.getServletRequest().getSession(false);
                if (session != null) {
                    Object uid = session.getAttribute("userId");
                    if (uid != null) {
                        attributes.put("userId", String.valueOf(uid));
                    }
                }
            }
            // permit anonymous handshake so SockJS info requests still work;
            // the inbound interceptor below rejects subscriptions without a Principal
            return true;
        }

        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Exception exception) { }
    }

    /**
     * Plain Principal — no Spring Security dependency.
     * STOMP only needs getName(); we don't need authorities here because
     * message-level auth is handled by PmAccessService at the REST layer.
     */
    private static final class SimplePrincipal implements Principal {
        private final String name;
        SimplePrincipal(String name) { this.name = name; }
        @Override public String getName() { return name; }
        @Override public String toString() { return name; }
    }

    /**
     * Attaches a Principal on CONNECT from the handshake attributes and
     * blocks subscriptions to another user's personal topic.
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor acc =
                        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (acc == null) return message;

                // Attach principal on CONNECT from handshake attributes
                if (StompCommand.CONNECT.equals(acc.getCommand())
                        && acc.getSessionAttributes() != null) {
                    Object uid = acc.getSessionAttributes().get("userId");
                    if (uid != null) {
                        acc.setUser(new SimplePrincipal(String.valueOf(uid)));
                    }
                }

                // Authorize /topic/user/{id}/... subscriptions
                if (StompCommand.SUBSCRIBE.equals(acc.getCommand())) {
                    String dest = acc.getDestination();
                    if (dest != null && dest.startsWith("/topic/user/")) {
                        String[] parts = dest.split("/");
                        // /topic/user/{userId}/notifications → parts[3]
                        if (parts.length >= 4) {
                            String requested = parts[3];
                            Principal me = acc.getUser();
                            if (me == null || !me.getName().equals(requested)) {
                                log.warn("Blocked topic subscription: {} (me={})", dest,
                                        me == null ? "anon" : me.getName());
                                return null; // silently drop the frame
                            }
                        }
                    }
                }
                return message;
            }
        });
    }
}
