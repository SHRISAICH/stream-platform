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
import com.streamplatform.streamapi.dto.CreateChatMessageRequest;
import com.streamplatform.streamapi.entity.ChatMessage;
import com.streamplatform.streamapi.entity.Stream;
import com.streamplatform.streamapi.entity.User;
import com.streamplatform.streamapi.repository.ChatMessageRepository;
import com.streamplatform.streamapi.repository.StreamRepository;
import com.streamplatform.streamapi.repository.UserRepository;
import com.streamplatform.streamapi.security.JwtService;

import jakarta.servlet.Filter;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class ChatControllerIntegrationTest {

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
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private User streamOwner;
    private User chatUser;
    private User thirdPartyUser;

    private String streamOwnerToken;
    private String chatUserToken;
    private String thirdPartyToken;

    private Stream publicStream;
    private Stream privateStream;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilter(springSecurityFilterChain)
                .build();

        // 1. Stream Owner
        String ownerName = "owner_" + UUID.randomUUID().toString().substring(0, 8);
        streamOwner = new User();
        streamOwner.setUsername(ownerName);
        streamOwner.setFullName("Stream Owner");
        streamOwner.setEmail(ownerName + "@example.com");
        streamOwner.setPassword(passwordEncoder.encode("Password123!"));
        streamOwner.setRole("ROLE_USER");
        streamOwner.setEnabled(true);
        streamOwner = userRepository.save(streamOwner);
        streamOwnerToken = jwtService.generateToken(streamOwner.getUsername());

        // 2. Chat Viewer
        String viewerName = "viewer_" + UUID.randomUUID().toString().substring(0, 8);
        chatUser = new User();
        chatUser.setUsername(viewerName);
        chatUser.setFullName("Chat Viewer");
        chatUser.setEmail(viewerName + "@example.com");
        chatUser.setPassword(passwordEncoder.encode("Password123!"));
        chatUser.setRole("ROLE_USER");
        chatUser.setEnabled(true);
        chatUser = userRepository.save(chatUser);
        chatUserToken = jwtService.generateToken(chatUser.getUsername());

        // 3. Third-party User
        String thirdName = "third_" + UUID.randomUUID().toString().substring(0, 8);
        thirdPartyUser = new User();
        thirdPartyUser.setUsername(thirdName);
        thirdPartyUser.setFullName("Third Party");
        thirdPartyUser.setEmail(thirdName + "@example.com");
        thirdPartyUser.setPassword(passwordEncoder.encode("Password123!"));
        thirdPartyUser.setRole("ROLE_USER");
        thirdPartyUser.setEnabled(true);
        thirdPartyUser = userRepository.save(thirdPartyUser);
        thirdPartyToken = jwtService.generateToken(thirdPartyUser.getUsername());

        // Create Public Stream
        publicStream = new Stream();
        publicStream.setTitle("Public Chat Stream");
        publicStream.setDescription("Public chat test");
        publicStream.setCategory("Gaming");
        publicStream.setStreamKey("chat_pub_" + UUID.randomUUID().toString().replace("-", ""));
        publicStream.setStatus("OFFLINE");
        publicStream.setPublic(true);
        publicStream.setUser(streamOwner);
        publicStream = streamRepository.save(publicStream);

        // Create Private Stream
        privateStream = new Stream();
        privateStream.setTitle("Private Chat Stream");
        privateStream.setDescription("Private chat test");
        privateStream.setCategory("Technology");
        privateStream.setStreamKey("chat_priv_" + UUID.randomUUID().toString().replace("-", ""));
        privateStream.setStatus("OFFLINE");
        privateStream.setPublic(false);
        privateStream.setUser(streamOwner);
        privateStream = streamRepository.save(privateStream);
    }

    @Test
    @Transactional
    void authenticatedMessageCreation_Success() throws Exception {
        CreateChatMessageRequest request = new CreateChatMessageRequest("Hello live stream!");

        mockMvc.perform(post("/api/streams/" + publicStream.getId() + "/chat")
                        .header("Authorization", "Bearer " + chatUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.streamId", is(publicStream.getId().intValue())))
                .andExpect(jsonPath("$.userId", is(chatUser.getId().intValue())))
                .andExpect(jsonPath("$.username", is(chatUser.getUsername())))
                .andExpect(jsonPath("$.content", is("Hello live stream!")))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void unauthenticatedMessageCreation_RejectedWith401() throws Exception {
        CreateChatMessageRequest request = new CreateChatMessageRequest("Should be rejected");

        mockMvc.perform(post("/api/streams/" + publicStream.getId() + "/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void emptyMessageContent_RejectedWith400() throws Exception {
        CreateChatMessageRequest request = new CreateChatMessageRequest("   ");

        mockMvc.perform(post("/api/streams/" + publicStream.getId() + "/chat")
                        .header("Authorization", "Bearer " + chatUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void messageContentTooLong_RejectedWith400() throws Exception {
        String longMessage = "a".repeat(501);
        CreateChatMessageRequest request = new CreateChatMessageRequest(longMessage);

        mockMvc.perform(post("/api/streams/" + publicStream.getId() + "/chat")
                        .header("Authorization", "Bearer " + chatUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void publicStream_AnonymousCanRetrieveMessages() throws Exception {
        ChatMessage msg1 = new ChatMessage(publicStream, chatUser, "First public message");
        ChatMessage msg2 = new ChatMessage(publicStream, streamOwner, "Welcome everyone!");
        chatMessageRepository.save(msg1);
        chatMessageRepository.save(msg2);

        mockMvc.perform(get("/api/streams/" + publicStream.getId() + "/chat"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].content", is("First public message")))
                .andExpect(jsonPath("$[1].content", is("Welcome everyone!")));
    }

    @Test
    void privateStream_UnauthorizedUserCannotRetrieveMessages() throws Exception {
        mockMvc.perform(get("/api/streams/" + privateStream.getId() + "/chat")
                        .header("Authorization", "Bearer " + thirdPartyToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @Transactional
    void privateStream_OwnerCanRetrieveMessages() throws Exception {
        ChatMessage msg = new ChatMessage(privateStream, streamOwner, "Owner private note");
        chatMessageRepository.save(msg);

        mockMvc.perform(get("/api/streams/" + privateStream.getId() + "/chat")
                        .header("Authorization", "Bearer " + streamOwnerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].content", is("Owner private note")));
    }

    @Test
    @Transactional
    void authorCanDeleteOwnMessage_Success() throws Exception {
        ChatMessage msg = new ChatMessage(publicStream, chatUser, "Message to delete by author");
        msg = chatMessageRepository.save(msg);

        mockMvc.perform(delete("/api/chat/" + msg.getId())
                        .header("Authorization", "Bearer " + chatUserToken))
                .andExpect(status().isNoContent());

        assertFalse(chatMessageRepository.existsById(msg.getId()));
    }

    @Test
    @Transactional
    void streamOwnerCanDeleteOtherUserMessage_Success() throws Exception {
        // Stream owner moderates a message sent by chatUser
        ChatMessage msg = new ChatMessage(publicStream, chatUser, "Spam message to moderate");
        msg = chatMessageRepository.save(msg);

        mockMvc.perform(delete("/api/chat/" + msg.getId())
                        .header("Authorization", "Bearer " + streamOwnerToken))
                .andExpect(status().isNoContent());

        assertFalse(chatMessageRepository.existsById(msg.getId()));
    }

    @Test
    @Transactional
    void thirdPartyCannotDeleteOtherUserMessage_Forbidden() throws Exception {
        ChatMessage msg = new ChatMessage(publicStream, chatUser, "Legitimate viewer message");
        msg = chatMessageRepository.save(msg);

        mockMvc.perform(delete("/api/chat/" + msg.getId())
                        .header("Authorization", "Bearer " + thirdPartyToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedDelete_RejectedWith401() throws Exception {
        mockMvc.perform(delete("/api/chat/99999"))
                .andExpect(status().isUnauthorized());
    }
}
