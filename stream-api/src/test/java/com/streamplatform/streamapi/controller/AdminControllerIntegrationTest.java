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
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamplatform.streamapi.dto.UpdateUserRoleRequest;
import com.streamplatform.streamapi.dto.UpdateUserStatusRequest;
import com.streamplatform.streamapi.entity.Stream;
import com.streamplatform.streamapi.entity.User;
import com.streamplatform.streamapi.entity.Video;
import com.streamplatform.streamapi.repository.StreamRepository;
import com.streamplatform.streamapi.repository.UserRepository;
import com.streamplatform.streamapi.repository.VideoRepository;
import com.streamplatform.streamapi.security.JwtService;

import jakarta.servlet.Filter;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class AdminControllerIntegrationTest {

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

    private final ObjectMapper objectMapper = new ObjectMapper();

    private User adminUser;
    private User normalUser;
    private String adminToken;
    private String normalToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters(springSecurityFilterChain)
                .build();

        String suffix = UUID.randomUUID().toString().substring(0, 8);

        adminUser = new User();
        adminUser.setUsername("admin_" + suffix);
        adminUser.setEmail("admin_" + suffix + "@example.com");
        adminUser.setFullName("Platform Admin");
        adminUser.setPassword(passwordEncoder.encode("Password123!"));
        adminUser.setRole("ADMIN");
        adminUser.setEnabled(true);
        adminUser = userRepository.save(adminUser);
        adminToken = jwtService.generateToken(adminUser.getUsername());

        normalUser = new User();
        normalUser.setUsername("regular_" + suffix);
        normalUser.setEmail("regular_" + suffix + "@example.com");
        normalUser.setFullName("Regular User");
        normalUser.setPassword(passwordEncoder.encode("Password123!"));
        normalUser.setRole("USER");
        normalUser.setEnabled(true);
        normalUser = userRepository.save(normalUser);
        normalToken = jwtService.generateToken(normalUser.getUsername());
    }

    @Test
    void nonAdminUser_forbiddenFromAllAdminEndpoints() throws Exception {
        // Stats
        mockMvc.perform(get("/api/admin/stats")
                        .header("Authorization", "Bearer " + normalToken))
                .andExpect(status().isForbidden());

        // Users
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + normalToken))
                .andExpect(status().isForbidden());

        // Update status
        mockMvc.perform(put("/api/admin/users/" + normalUser.getId() + "/status")
                        .header("Authorization", "Bearer " + normalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateUserStatusRequest(false))))
                .andExpect(status().isForbidden());

        // Delete stream
        mockMvc.perform(delete("/api/admin/streams/999")
                        .header("Authorization", "Bearer " + normalToken))
                .andExpect(status().isForbidden());

        // Delete video
        mockMvc.perform(delete("/api/admin/videos/999")
                        .header("Authorization", "Bearer " + normalToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticated_rejectedWithUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/stats"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminUser_canRetrieveStatsAndUsers() throws Exception {
        mockMvc.perform(get("/api/admin/stats")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers", greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.activeUsers", greaterThanOrEqualTo(2)));

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))));
    }

    @Test
    void adminUser_canUpdateUserStatusAndRole() throws Exception {
        // Toggle user active status to false
        mockMvc.perform(put("/api/admin/users/" + normalUser.getId() + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateUserStatusRequest(false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled", is(false)));

        User reloaded = userRepository.findById(normalUser.getId()).orElseThrow();
        assertFalse(reloaded.isEnabled());

        // Promote to ADMIN
        mockMvc.perform(put("/api/admin/users/" + normalUser.getId() + "/role")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateUserRoleRequest("ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role", is("ADMIN")));

        reloaded = userRepository.findById(normalUser.getId()).orElseThrow();
        assertEquals("ADMIN", reloaded.getRole());
    }

    @Test
    void adminUser_cannotDisableOrDemoteSelf() throws Exception {
        // Try disabling self -> 400 Bad Request
        mockMvc.perform(put("/api/admin/users/" + adminUser.getId() + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateUserStatusRequest(false))))
                .andExpect(status().isBadRequest());

        // Try demoting self -> 400 Bad Request
        mockMvc.perform(put("/api/admin/users/" + adminUser.getId() + "/role")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateUserRoleRequest("USER"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminUser_canModerateStreamsAndVideos() throws Exception {
        // Create stream
        Stream s = new Stream();
        s.setTitle("Inappropriate Stream");
        s.setCategory("Gaming");
        s.setStreamKey("key-mod-" + UUID.randomUUID());
        s.setStatus("LIVE");
        s.setUser(normalUser);
        s = streamRepository.save(s);

        // Create video
        Video v = new Video();
        v.setTitle("Inappropriate Video");
        v.setCategory("Gaming");
        v.setObjectKey("videos/mod-" + UUID.randomUUID() + ".mp4");
        v.setOriginalFilename("mod.mp4");
        v.setContentType("video/mp4");
        v.setFileSize(1000L);
        v.setStatus("READY");
        v.setUser(normalUser);
        v = videoRepository.save(v);

        // Admin lists streams
        mockMvc.perform(get("/api/admin/streams")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));

        // Admin deletes stream
        mockMvc.perform(delete("/api/admin/streams/" + s.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        assertFalse(streamRepository.existsById(s.getId()));

        // Admin lists videos
        mockMvc.perform(get("/api/admin/videos")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));

        // Admin deletes video
        mockMvc.perform(delete("/api/admin/videos/" + v.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        assertFalse(videoRepository.existsById(v.getId()));
    }
}
