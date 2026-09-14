package com.streamplatform.streamapi.controller;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamplatform.streamapi.dto.UpdateVideoRequest;
import com.streamplatform.streamapi.entity.User;
import com.streamplatform.streamapi.entity.Video;
import com.streamplatform.streamapi.repository.UserRepository;
import com.streamplatform.streamapi.repository.VideoRepository;
import com.streamplatform.streamapi.security.JwtService;

import jakarta.servlet.Filter;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class VideoControllerIntegrationTest {

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
    private VideoRepository videoRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private User ownerUser;
    private User otherUser;
    private String ownerToken;
    private String otherToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilter(springSecurityFilterChain)
                .build();

        String ownerName = "video_owner_" + UUID.randomUUID().toString().substring(0, 8);
        ownerUser = new User();
        ownerUser.setUsername(ownerName);
        ownerUser.setFullName("Video Owner");
        ownerUser.setEmail(ownerName + "@example.com");
        ownerUser.setPassword(passwordEncoder.encode("Password123!"));
        ownerUser.setRole("ROLE_USER");
        ownerUser.setEnabled(true);
        ownerUser = userRepository.save(ownerUser);
        ownerToken = jwtService.generateToken(ownerUser.getUsername());

        String otherName = "video_other_" + UUID.randomUUID().toString().substring(0, 8);
        otherUser = new User();
        otherUser.setUsername(otherName);
        otherUser.setFullName("Other User");
        otherUser.setEmail(otherName + "@example.com");
        otherUser.setPassword(passwordEncoder.encode("Password123!"));
        otherUser.setRole("ROLE_USER");
        otherUser.setEnabled(true);
        otherUser = userRepository.save(otherUser);
        otherToken = jwtService.generateToken(otherUser.getUsername());
    }

    @Test
    @Transactional
    void authenticatedUpload_Success() throws Exception {
        MockMultipartFile videoFile = new MockMultipartFile(
                "file",
                "test-clip.mp4",
                "video/mp4",
                "FAKE_VIDEO_CONTENT_BYTES_12345".getBytes());

        mockMvc.perform(multipart("/api/videos")
                        .file(videoFile)
                        .param("title", "My Awesome Tech Demo")
                        .param("description", "Demonstrating high-performance VOD")
                        .param("category", "Technology")
                        .param("public", "true")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.title", is("My Awesome Tech Demo")))
                .andExpect(jsonPath("$.category", is("Technology")))
                .andExpect(jsonPath("$.public", is(true)))
                .andExpect(jsonPath("$.playbackUrl", containsString("/api/videos/")))
                .andExpect(jsonPath("$.objectKey").doesNotExist());
    }

    @Test
    void unauthenticatedUpload_Rejected() throws Exception {
        MockMultipartFile videoFile = new MockMultipartFile(
                "file",
                "sample.mp4",
                "video/mp4",
                "dummy content".getBytes());

        mockMvc.perform(multipart("/api/videos")
                        .file(videoFile)
                        .param("title", "Unauthorized Video"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Transactional
    void publicVideoListing_ExcludesPrivateVideos() throws Exception {
        // Create public video
        Video publicVideo = new Video();
        publicVideo.setUser(ownerUser);
        publicVideo.setTitle("Public Video 101");
        publicVideo.setDescription("Publicly visible");
        publicVideo.setCategory("Technology");
        publicVideo.setObjectKey("videos/" + ownerUser.getId() + "/test-pub-" + UUID.randomUUID() + ".mp4");
        publicVideo.setOriginalFilename("pub.mp4");
        publicVideo.setContentType("video/mp4");
        publicVideo.setFileSize(1024L);
        publicVideo.setPublic(true);
        videoRepository.save(publicVideo);

        // Create private video
        Video privateVideo = new Video();
        privateVideo.setUser(ownerUser);
        privateVideo.setTitle("Private Video Secret");
        privateVideo.setDescription("Hidden video");
        privateVideo.setCategory("Technology");
        privateVideo.setObjectKey("videos/" + ownerUser.getId() + "/test-priv-" + UUID.randomUUID() + ".mp4");
        privateVideo.setOriginalFilename("priv.mp4");
        privateVideo.setContentType("video/mp4");
        privateVideo.setFileSize(2048L);
        privateVideo.setPublic(false);
        videoRepository.save(privateVideo);

        // Verify unauthenticated public GET
        mockMvc.perform(get("/api/videos/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.title == 'Public Video 101')]").exists())
                .andExpect(jsonPath("$[?(@.title == 'Private Video Secret')]").doesNotExist());
    }

    @Test
    @Transactional
    void ownerUpdate_Success_NonOwnerForbidden() throws Exception {
        Video video = new Video();
        video.setUser(ownerUser);
        video.setTitle("Original Title");
        video.setDescription("Original Description");
        video.setCategory("Technology");
        video.setObjectKey("videos/" + ownerUser.getId() + "/test-" + UUID.randomUUID() + ".mp4");
        video.setOriginalFilename("original.mp4");
        video.setContentType("video/mp4");
        video.setFileSize(512L);
        video.setPublic(true);
        video = videoRepository.save(video);

        UpdateVideoRequest updateReq = new UpdateVideoRequest();
        updateReq.setTitle("Updated Title by Owner");
        updateReq.setDescription("Updated Description");
        updateReq.setCategory("Gaming");
        updateReq.setPublic(true);

        // Owner update -> 200 OK
        mockMvc.perform(put("/api/videos/" + video.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq))
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("Updated Title by Owner")))
                .andExpect(jsonPath("$.category", is("Gaming")));

        // Non-owner update -> 403 Forbidden
        mockMvc.perform(put("/api/videos/" + video.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq))
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @Transactional
    void ownerDelete_Success_NonOwnerForbidden() throws Exception {
        Video video = new Video();
        video.setUser(ownerUser);
        video.setTitle("Delete Me");
        video.setDescription("To be deleted");
        video.setCategory("General");
        video.setObjectKey("videos/" + ownerUser.getId() + "/del-" + UUID.randomUUID() + ".mp4");
        video.setOriginalFilename("del.mp4");
        video.setContentType("video/mp4");
        video.setFileSize(512L);
        video.setPublic(true);
        video = videoRepository.save(video);

        // Non-owner delete -> 403 Forbidden
        mockMvc.perform(delete("/api/videos/" + video.getId())
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());

        // Owner delete -> 204 No Content
        mockMvc.perform(delete("/api/videos/" + video.getId())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        // Verify video is deleted
        mockMvc.perform(get("/api/videos/" + video.getId())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidFileType_Rejected() throws Exception {
        MockMultipartFile textFile = new MockMultipartFile(
                "file",
                "notes.txt",
                "text/plain",
                "this is not a video".getBytes());

        mockMvc.perform(multipart("/api/videos")
                        .file(textFile)
                        .param("title", "Invalid File Upload")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isBadRequest());
    }
}
