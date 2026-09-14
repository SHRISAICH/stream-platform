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

import com.streamplatform.streamapi.entity.Stream;
import com.streamplatform.streamapi.entity.User;
import com.streamplatform.streamapi.entity.Video;
import com.streamplatform.streamapi.repository.StreamRepository;
import com.streamplatform.streamapi.repository.UserRepository;
import com.streamplatform.streamapi.repository.VideoRepository;
import com.streamplatform.streamapi.security.JwtService;

import jakarta.servlet.Filter;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class AnalyticsControllerIntegrationTest {

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
    private VideoRepository videoRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User creator;
    private String token;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters(springSecurityFilterChain)
                .build();

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        creator = new User();
        creator.setUsername("creator_" + suffix);
        creator.setEmail("creator_" + suffix + "@example.com");
        creator.setFullName("Analytics Creator");
        creator.setPassword(passwordEncoder.encode("Password123!"));
        creator.setRole("ROLE_USER");
        creator.setEnabled(true);
        creator = userRepository.save(creator);

        token = jwtService.generateToken(creator.getUsername());
    }

    @Test
    void getCreatorAnalytics_unauthorizedWithoutToken() throws Exception {
        mockMvc.perform(get("/api/analytics/creator"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getCreatorAnalytics_returnsAccurateCreatorData() throws Exception {
        // Create 1 live stream, 1 scheduled stream, 1 ended stream
        Stream s1 = new Stream();
        s1.setTitle("Live Stream");
        s1.setCategory("Gaming");
        s1.setStreamKey("key-live-" + UUID.randomUUID());
        s1.setStatus("LIVE");
        s1.setPeakViewers(15);
        s1.setDurationSeconds(1200L);
        s1.setUser(creator);
        streamRepository.save(s1);

        Stream s2 = new Stream();
        s2.setTitle("Scheduled Stream");
        s2.setCategory("Technology");
        s2.setStreamKey("key-sched-" + UUID.randomUUID());
        s2.setStatus("SCHEDULED");
        s2.setScheduledStartTime(LocalDateTime.now().plusDays(2));
        s2.setUser(creator);
        streamRepository.save(s2);

        Stream s3 = new Stream();
        s3.setTitle("Ended Stream");
        s3.setCategory("Gaming");
        s3.setStreamKey("key-ended-" + UUID.randomUUID());
        s3.setStatus("ENDED");
        s3.setPeakViewers(42);
        s3.setDurationSeconds(3600L);
        s3.setUser(creator);
        streamRepository.save(s3);

        // Create 1 video
        Video v1 = new Video();
        v1.setTitle("Highlight Video");
        v1.setCategory("Gaming");
        v1.setObjectKey("videos/highlight-" + UUID.randomUUID() + ".mp4");
        v1.setOriginalFilename("highlight.mp4");
        v1.setContentType("video/mp4");
        v1.setFileSize(5000000L);
        v1.setStatus("READY");
        v1.setViews(120L);
        v1.setUser(creator);
        videoRepository.save(v1);

        mockMvc.perform(get("/api/analytics/creator")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalStreams", is(3)))
                .andExpect(jsonPath("$.liveStreams", is(1)))
                .andExpect(jsonPath("$.scheduledStreams", is(1)))
                .andExpect(jsonPath("$.completedStreams", is(1)))
                .andExpect(jsonPath("$.peakConcurrentViewers", is(42)))
                .andExpect(jsonPath("$.totalDurationSeconds", is(4800)))
                .andExpect(jsonPath("$.totalVideos", is(1)))
                .andExpect(jsonPath("$.totalVideoViews", is(120)))
                .andExpect(jsonPath("$.totalStorageBytes", is(5000000)))
                .andExpect(jsonPath("$.recentStreams.length()", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.categoryDistribution.Gaming", is(3)))
                .andExpect(jsonPath("$.categoryDistribution.Technology", is(1)));
    }
}
