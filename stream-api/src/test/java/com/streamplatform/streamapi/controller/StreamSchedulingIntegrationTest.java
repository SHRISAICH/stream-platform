package com.streamplatform.streamapi.controller;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamplatform.streamapi.dto.ScheduleStreamRequest;
import com.streamplatform.streamapi.entity.Stream;
import com.streamplatform.streamapi.entity.User;
import com.streamplatform.streamapi.repository.StreamRepository;
import com.streamplatform.streamapi.repository.UserRepository;
import com.streamplatform.streamapi.security.JwtService;
import com.streamplatform.streamapi.service.StreamService;

import jakarta.servlet.Filter;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class StreamSchedulingIntegrationTest {

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
    private StreamService streamService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private User userA;
    private User userB;
    private String tokenA;
    private String tokenB;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters(springSecurityFilterChain)
                .build();

        String suffix = UUID.randomUUID().toString().substring(0, 8);

        userA = new User();
        userA.setUsername("schedule_a_" + suffix);
        userA.setEmail("schedule_a_" + suffix + "@example.com");
        userA.setFullName("User Schedule A");
        userA.setPassword(passwordEncoder.encode("Password123!"));
        userA.setRole("ROLE_USER");
        userA.setEnabled(true);
        userA = userRepository.save(userA);
        tokenA = jwtService.generateToken(userA.getUsername());

        userB = new User();
        userB.setUsername("schedule_b_" + suffix);
        userB.setEmail("schedule_b_" + suffix + "@example.com");
        userB.setFullName("User Schedule B");
        userB.setPassword(passwordEncoder.encode("Password123!"));
        userB.setRole("ROLE_USER");
        userB.setEnabled(true);
        userB = userRepository.save(userB);
        tokenB = jwtService.generateToken(userB.getUsername());
    }

    @Test
    void scheduleStream_success() throws Exception {
        LocalDateTime future = LocalDateTime.now().plusDays(2);
        ScheduleStreamRequest req = new ScheduleStreamRequest(
                "Upcoming Launch Event",
                "Product release scheduled stream",
                "Technology",
                true,
                future,
                future.plusHours(2)
        );

        mockMvc.perform(post("/api/streams/schedule")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("Upcoming Launch Event")))
                .andExpect(jsonPath("$.category", is("Technology")))
                .andExpect(jsonPath("$.status", is("SCHEDULED")))
                .andExpect(jsonPath("$.viewerCount", is(0)))
                .andExpect(jsonPath("$.streamKey").isNotEmpty());
    }

    @Test
    void scheduleStream_invalidPastDate() throws Exception {
        ScheduleStreamRequest req = new ScheduleStreamRequest(
                "Past Event",
                "Cannot schedule in past",
                "Gaming",
                true,
                LocalDateTime.now().minusHours(2),
                null
        );

        mockMvc.perform(post("/api/streams/schedule")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void scheduleStream_invalidEndTimeBeforeStartTime() throws Exception {
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        ScheduleStreamRequest req = new ScheduleStreamRequest(
                "Bad End Time",
                "End time is before start",
                "Music",
                true,
                start,
                start.minusHours(1)
        );

        mockMvc.perform(post("/api/streams/schedule")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getPublicScheduledStreams_accessiblePublicly() throws Exception {
        LocalDateTime future = LocalDateTime.now().plusDays(1);
        ScheduleStreamRequest req = new ScheduleStreamRequest(
                "Public Future Stream",
                "For everyone to see",
                "Creative",
                true,
                future,
                null
        );

        mockMvc.perform(post("/api/streams/schedule")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        // Fetch anonymously without token
        mockMvc.perform(get("/api/streams/scheduled"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    void cancelScheduledStream_ownerAndOtherUserBehavior() throws Exception {
        ScheduleStreamRequest req = new ScheduleStreamRequest(
                "Stream to be cancelled",
                "Cancellation test",
                "Entertainment",
                true,
                LocalDateTime.now().plusDays(3),
                null
        );
        String responseContent = mockMvc.perform(post("/api/streams/schedule")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Long createdId = objectMapper.readTree(responseContent).get("id").asLong();

        // User B tries to cancel User A's stream -> 403 Forbidden
        mockMvc.perform(put("/api/streams/" + createdId + "/cancel")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());

        // User A cancels their own stream -> 200 OK
        mockMvc.perform(put("/api/streams/" + createdId + "/cancel")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CANCELLED")));

        Stream stream = streamRepository.findById(createdId).orElseThrow();
        assertEquals("CANCELLED", stream.getStatus());
    }

    @Test
    void publishAndUnpublishScheduledStream_lifecycleTransitions() throws Exception {
        ScheduleStreamRequest req = new ScheduleStreamRequest(
                "Lifecycle Test Stream",
                "Testing scheduled -> live -> ended",
                "Sports",
                true,
                LocalDateTime.now().plusDays(1),
                null
        );
        String responseContent = mockMvc.perform(post("/api/streams/schedule")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Long createdId = objectMapper.readTree(responseContent).get("id").asLong();
        String streamKey = objectMapper.readTree(responseContent).get("streamKey").asText();

        // SRS on_publish confirms stream is live
        boolean published = streamService.publishStream("live", streamKey);
        assertTrue(published);

        Stream liveStream = streamRepository.findById(createdId).orElseThrow();
        assertEquals("LIVE", liveStream.getStatus());
        assertNotNull(liveStream.getStartedAt());

        // SRS on_unpublish confirms stream ends
        boolean unpublished = streamService.unpublishStream("live", streamKey);
        assertTrue(unpublished);

        Stream endedStream = streamRepository.findById(createdId).orElseThrow();
        assertEquals("ENDED", endedStream.getStatus());
        assertNotNull(endedStream.getEndedAt());
    }
}
