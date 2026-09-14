package com.streamplatform.streamapi.security;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.streamplatform.streamapi.entity.Stream;
import com.streamplatform.streamapi.entity.User;
import com.streamplatform.streamapi.repository.StreamRepository;
import com.streamplatform.streamapi.repository.UserRepository;

import jakarta.servlet.Filter;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class JwtAuthenticationIntegrationTest {

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
    private PasswordEncoder passwordEncoder;

    private static final String TEST_USERNAME = "jwttestuser";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilter(springSecurityFilterChain)
                .build();

        if (!userRepository.existsByUsername(TEST_USERNAME)) {
            User user = new User();
            user.setFullName("JWT Test User");
            user.setUsername(TEST_USERNAME);
            user.setEmail("jwttestuser@example.com");
            user.setPassword(passwordEncoder.encode("password123"));
            user.setRole("USER");
            user.setEnabled(true);
            userRepository.save(user);
        }
    }

    @Test
    void whenNoAuthorizationHeader_thenReturns401Unauthorized() throws Exception {
        mockMvc.perform(get("/api/streams"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Full authentication is required to access this resource"))
                .andExpect(jsonPath("$.path").value("/api/streams"));
    }

    @Test
    void whenMalformedToken_thenReturns401Unauthorized() throws Exception {
        mockMvc.perform(get("/api/streams")
                        .header("Authorization", "Bearer invalid.malformed.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.path").value("/api/streams"));
    }

    @Test
    void whenExpiredToken_thenReturns401Unauthorized() throws Exception {
        String expiredToken = jwtService.generateToken(TEST_USERNAME, -60000);

        mockMvc.perform(get("/api/streams")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("JWT token has expired"))
                .andExpect(jsonPath("$.path").value("/api/streams"));
    }

    @Test
    void whenValidToken_thenAllowsAccess() throws Exception {
        String validToken = jwtService.generateToken(TEST_USERNAME);

        mockMvc.perform(get("/api/streams")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk());
    }

    @Test
    void whenExpiredTokenOnPublicEndpoint_thenNotBlockedByFilter() throws Exception {
        String expiredToken = jwtService.generateToken(TEST_USERNAME, -60000);

        mockMvc.perform(post("/api/auth/login")
                        .header("Authorization", "Bearer " + expiredToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameOrEmail\":\"" + TEST_USERNAME + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void whenValidToken_thenCanGetMeWithoutPassword() throws Exception {
        String validToken = jwtService.generateToken(TEST_USERNAME);

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.username").value(TEST_USERNAME))
                .andExpect(jsonPath("$.email").value("jwttestuser@example.com"))
                .andExpect(jsonPath("$.fullName").value("JWT Test User"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void whenNoToken_thenGetMeReturns401() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.path").value("/api/users/me"));
    }

    @Test
    @Transactional
    void whenPublicStreamsRequested_thenReturns200WithoutAuthAndExcludesPrivateAndStreamKeys() throws Exception {
        User user = userRepository.findByUsername(TEST_USERNAME).orElseThrow();

        String pubSuffix = UUID.randomUUID().toString().substring(0, 8);
        Stream pubStream = new Stream();
        pubStream.setTitle("Public Stream " + pubSuffix);
        pubStream.setDescription("Public Description");
        pubStream.setCategory("Gaming");
        pubStream.setStreamKey("pub-" + UUID.randomUUID());
        pubStream.setStatus("OFFLINE");
        pubStream.setPublic(true);
        pubStream.setUser(user);
        streamRepository.save(pubStream);

        String privSuffix = UUID.randomUUID().toString().substring(0, 8);
        Stream privStream = new Stream();
        privStream.setTitle("Private Stream " + privSuffix);
        privStream.setDescription("Private Description");
        privStream.setCategory("Secret");
        privStream.setStreamKey("priv-" + UUID.randomUUID());
        privStream.setStatus("OFFLINE");
        privStream.setPublic(false);
        privStream.setUser(user);
        streamRepository.save(privStream);

        mockMvc.perform(get("/api/streams/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[*].title").value(hasItem("Public Stream " + pubSuffix)))
                .andExpect(jsonPath("$[*].title").value(not(hasItem("Private Stream " + privSuffix))))
                .andExpect(jsonPath("$[*].streamKey").doesNotExist());
    }

    @Test
    void whenModifyingStreamsWithoutAuth_thenReturns401Unauthorized() throws Exception {
        mockMvc.perform(post("/api/streams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"New Stream\",\"category\":\"Tech\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/streams/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Updated Stream\",\"category\":\"Tech\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/streams/1"))
                .andExpect(status().isUnauthorized());
    }
}
