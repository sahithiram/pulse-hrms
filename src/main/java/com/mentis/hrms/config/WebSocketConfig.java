package com.mentis.hrms.config;

import com.mentis.hrms.pm.security.PmAccessService;
import com.mentis.hrms.repository.EmployeeRepository;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
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

    private final PmAccessService access;
    private final EmployeeRepository employeeRepository;

    public WebSocketConfig(@Lazy PmAccessService access,
                           @Lazy EmployeeRepository employeeRepository) {
        this.access = access;
        this.employeeRepository = employeeRepository;
    }

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
            return true;
        }

        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Exception exception) { }
    }

    private static final class SimplePrincipal implements Principal {
        private final String name;
        SimplePrincipal(String name) { this.name = name; }
        @Override public String getName() { return name; }
        @Override public String toString() { return name; }
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor acc =
                        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (acc == null) return message;

                if (StompCommand.CONNECT.equals(acc.getCommand())
                        && acc.getSessionAttributes() != null) {
                    Object uid = acc.getSessionAttributes().get("userId");
                    if (uid != null) {
                        acc.setUser(new SimplePrincipal(String.valueOf(uid)));
                    }
                }

                if (StompCommand.SUBSCRIBE.equals(acc.getCommand())) {
                    String dest = acc.getDestination();

                    // existing per-user topic check
                    if (dest != null && dest.startsWith("/topic/user/")) {
                        String[] parts = dest.split("/");
                        if (parts.length >= 4) {
                            String requested = parts[3];
                            Principal me = acc.getUser();
                            if (me == null || !me.getName().equals(requested)) {
                                log.warn("Blocked topic subscription: {} (me={})", dest,
                                        me == null ? "anon" : me.getName());
                                return null;
                            }
                        }
                    }

                    // PM topic check (Step 6)
                    if (dest != null && dest.startsWith("/topic/pm/")) {
                        Principal me = acc.getUser();
                        if (me == null) {
                            log.warn("Blocked anonymous subscription: {}", dest);
                            return null;
                        }
                        if (dest.startsWith("/topic/pm/team/")
                                && !mayJoinTeamTopic(me.getName(), dest)) {
                            log.warn("Blocked team topic: {} (me={})", dest, me.getName());
                            return null;
                        }
                    }
                }
                return message;
            }
        });
    }

    /** me = employee code (session userId), not the primary key. Team topics are open to leads and members. */
    private boolean mayJoinTeamTopic(String employeeCode, String dest) {
        try {
            Long teamId = Long.valueOf(dest.substring("/topic/pm/team/".length()));
            Long pk = employeeRepository.findByEmployeeId(employeeCode)
                    .map(e -> e.getId()).orElse(null);
            return pk != null && (access.isTeamLead(pk, teamId) || access.isTeamMember(pk, teamId));
        } catch (Exception e) {
            return false;
        }
    }
}