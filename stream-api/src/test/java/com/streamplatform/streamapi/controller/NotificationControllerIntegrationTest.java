package com.streamplatform.streamapi.controller;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamplatform.streamapi.entity.Notification;
import com.streamplatform.streamapi.entity.Stream;
import com.streamplatform.streamapi.entity.User;
import com.streamplatform.streamapi.repository.NotificationRepository;
import com.streamplatform.streamapi.repository.StreamRepository;
import com.streamplatform.streamapi.repository.UserRepository;
import com.streamplatform.streamapi.security.JwtService;
import com.streamplatform.streamapi.service.NotificationService;
import com.streamplatform.streamapi.service.StreamService;

import jakarta.servlet.Filter;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class NotificationControllerIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private Filter springSecurityFilterChain;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StreamRepository streamRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private StreamService streamService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User userA;
    private User userB;

    private String tokenA;
    private String tokenB;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilter(springSecurityFilterChain)
                .build();

        String suffixA = UUID.randomUUID().toString().substring(0, 8);
        userA = new User();
        userA.setUsername("user_a_" + suffixA);
        userA.setFullName("User Alice");
        userA.setEmail("alice_" + suffixA + "@example.com");
        userA.setPassword(passwordEncoder.encode("Password123!"));
        userA.setRole("ROLE_USER");
        userA.setEnabled(true);
        userA = userRepository.save(userA);
        tokenA = jwtService.generateToken(userA.getUsername());

        String suffixB = UUID.randomUUID().toString().substring(0, 8);
        userB = new User();
        userB.setUsername("user_b_" + suffixB);
        userB.setFullName("User Bob");
        userB.setEmail("bob_" + suffixB + "@example.com");
        userB.setPassword(passwordEncoder.encode("Password123!"));
        userB.setRole("ROLE_USER");
        userB.setEnabled(true);
        userB = userRepository.save(userB);
        tokenB = jwtService.generateToken(userB.getUsername());
    }

    @Test
    void getNotifications_Unauthenticated_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getNotifications_Authenticated_ReturnsOnlyUserNotifications() throws Exception {
        Notification nA = notificationService.createNotification(
                userA, "STREAM_LIVE", "Alice Stream", "Your stream is live", 100L);
        Notification nB = notificationService.createNotification(
                userB, "STREAM_LIVE", "Bob Stream", "Your stream is live", 200L);

        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(nA.getId().intValue())))
                .andExpect(jsonPath("$[0].title", is("Alice Stream")))
                .andExpect(jsonPath("$[0].read", is(false)));
    }

    @Test
    void getUnreadCount_Authenticated_ReturnsCorrectCount() throws Exception {
        notificationService.createNotification(userA, "INFO", "Notif 1", "Msg 1", null);
        notificationService.createNotification(userA, "INFO", "Notif 2", "Msg 2", null);

        mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count", is(2)))
                .andExpect(jsonPath("$.unreadCount", is(2)));
    }

    @Test
    void markAsRead_Authenticated_UpdatesStatusToRead() throws Exception {
        Notification n = notificationService.createNotification(userA, "INFO", "Notif 1", "Msg 1", null);
        assertFalse(n.isRead());

        mockMvc.perform(put("/api/notifications/" + n.getId() + "/read")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(n.getId().intValue())))
                .andExpect(jsonPath("$.read", is(true)));

        Notification updated = notificationRepository.findById(n.getId()).orElseThrow();
        assertTrue(updated.isRead());
    }

    @Test
    void markAsRead_OtherUserNotification_ReturnsForbidden() throws Exception {
        Notification nA = notificationService.createNotification(userA, "INFO", "Alice Only", "Msg", null);

        mockMvc.perform(put("/api/notifications/" + nA.getId() + "/read")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());
    }

    @Test
    void markAllAsRead_Authenticated_MarksAllNotificationsAsRead() throws Exception {
        notificationService.createNotification(userA, "INFO", "Notif 1", "Msg 1", null);
        notificationService.createNotification(userA, "INFO", "Notif 2", "Msg 2", null);

        mockMvc.perform(put("/api/notifications/read-all")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedCount", greaterThanOrEqualTo(2)));

        mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count", is(0)));
    }

    @Test
    void deleteNotification_Authenticated_DeletesNotification() throws Exception {
        Notification n = notificationService.createNotification(userA, "INFO", "To Delete", "Msg", null);

        mockMvc.perform(delete("/api/notifications/" + n.getId())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Notification deleted successfully")));

        assertFalse(notificationRepository.existsById(n.getId()));
    }

    @Test
    void deleteNotification_OtherUserNotification_ReturnsForbidden() throws Exception {
        Notification nA = notificationService.createNotification(userA, "INFO", "Alice Only", "Msg", null);

        mockMvc.perform(delete("/api/notifications/" + nA.getId())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());

        assertTrue(notificationRepository.existsById(nA.getId()));
    }

    @Test
    void streamLifecycle_PublishAndUnpublish_GeneratesNotifications() {
        // Create a stream owned by userA
        Stream stream = new Stream();
        stream.setTitle("Super Gamer Stream");
        stream.setCategory("Gaming");
        stream.setStreamKey("key_" + UUID.randomUUID().toString().substring(0, 10));
        stream.setStatus("OFFLINE");
        stream.setPublic(true);
        stream.setUser(userA);
        stream = streamRepository.save(stream);

        // 1. Trigger stream live
        boolean published = streamService.publishStream("live", stream.getStreamKey());
        assertTrue(published);

        // Verify owner received STREAM_LIVE notification
        var ownerNotifs = notificationRepository.findByUserOrderByCreatedAtDesc(userA);
        assertTrue(ownerNotifs.stream().anyMatch(n -> "STREAM_LIVE".equals(n.getType()) && n.getMessage().contains("Super Gamer Stream")));

        // Verify userB received broadcast notification for public stream
        var viewerNotifs = notificationRepository.findByUserOrderByCreatedAtDesc(userB);
        assertTrue(viewerNotifs.stream().anyMatch(n -> "STREAM_LIVE".equals(n.getType()) && n.getMessage().contains("Super Gamer Stream")));

        // 2. Trigger stream end
        boolean unpublished = streamService.unpublishStream("live", stream.getStreamKey());
        assertTrue(unpublished);

        // Verify owner received STREAM_ENDED notification
        var ownerNotifsAfterEnd = notificationRepository.findByUserOrderByCreatedAtDesc(userA);
        assertTrue(ownerNotifsAfterEnd.stream().anyMatch(n -> "STREAM_ENDED".equals(n.getType()) && n.getMessage().contains("ended")));
    }
}
