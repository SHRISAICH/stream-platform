
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

import com.streamplatform.streamapi.entity.Stream;
import com.streamplatform.streamapi.entity.User;
import com.streamplatform.streamapi.repository.StreamRepository;
import com.streamplatform.streamapi.repository.UserRepository;
import com.streamplatform.streamapi.service.SrsService;

import jakarta.servlet.Filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest
class SrsWebhookIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private Filter springSecurityFilterChain;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StreamRepository streamRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SrsService srsService;

    private static final String TEST_USER = "srswebhookuser";
    private User testUser;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilter(springSecurityFilterChain)
                .build();

        testUser = userRepository.findByUsername(TEST_USER).orElseGet(() -> {
            User user = new User();
            user.setFullName("SRS Webhook User");
            user.setUsername(TEST_USER);
            user.setEmail("srswebhook@example.com");
            user.setPassword(passwordEncoder.encode("password123"));
            user.setRole("USER");
            user.setEnabled(true);
            return userRepository.save(user);
        });
    }

    private Stream createStream(String status) {
        Stream stream = new Stream();
        stream.setTitle("SRS Test Stream " + UUID.randomUUID().toString().substring(0, 8));
        stream.setDescription("Testing SRS Webhook");
        stream.setCategory("Gaming");
        stream.setStreamKey("srs-test-" + UUID.randomUUID());
        stream.setStatus(status);
        stream.setPublic(true);
        stream.setUser(testUser);
        return streamRepository.save(stream);
    }

    @Test
    @Transactional
    void whenOnPublishWithValidStreamKey_thenReturnsSuccessAndMarksStreamLive() throws Exception {
        Stream stream = createStream("OFFLINE");
        String streamKey = stream.getStreamKey();

        String payload = """
                {
                    "action": "on_publish",
                    "client_id": 1985,
                    "ip": "127.0.0.1",
                    "vhost": "__defaultVhost__",
                    "app": "live",
                    "stream": "%s",
                    "param": ""
                }
                """.formatted(streamKey);

        mockMvc.perform(post("/api/srs/on-publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(content().string("0"));

        Stream updatedStream = streamRepository.findByStreamKey(streamKey).orElseThrow();
        assertThat(updatedStream.getStatus()).isEqualTo("LIVE");
    }

    @Test
    @Transactional
    void whenOnPublishAliasWithValidStreamKey_thenReturnsSuccessAndMarksStreamLive() throws Exception {
        Stream stream = createStream("OFFLINE");
        String streamKey = stream.getStreamKey();

        String payload = """
                {
                    "action": "on_publish",
                    "client_id": 1985,
                    "ip": "127.0.0.1",
                    "vhost": "__defaultVhost__",
                    "app": "live",
                    "stream": "%s"
                }
                """.formatted(streamKey);

        mockMvc.perform(post("/api/srs/on_publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(content().string("0"));

        Stream updatedStream = streamRepository.findByStreamKey(streamKey).orElseThrow();
        assertThat(updatedStream.getStatus()).isEqualTo("LIVE");
    }

    @Test
    void whenOnPublishWithInvalidStreamKey_thenRejectsPublishAndStreamNotLive() throws Exception {
        String invalidKey = "invalid-stream-key-" + UUID.randomUUID();

        String payload = """
                {
                    "action": "on_publish",
                    "client_id": 1985,
                    "ip": "127.0.0.1",
                    "vhost": "__defaultVhost__",
                    "app": "live",
                    "stream": "%s",
                    "param": ""
                }
                """.formatted(invalidKey);

        mockMvc.perform(post("/api/srs/on-publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden())
                .andExpect(content().string("1"));

        assertThat(streamRepository.findByStreamKey(invalidKey)).isEmpty();
    }

    @Test
    @Transactional
    void whenOnPublishWithInvalidApp_thenRejectsPublish() throws Exception {
        Stream stream = createStream("OFFLINE");
        String streamKey = stream.getStreamKey();

        String payload = """
                {
                    "action": "on_publish",
                    "client_id": 1985,
                    "ip": "127.0.0.1",
                    "vhost": "__defaultVhost__",
                    "app": "vod",
                    "stream": "%s",
                    "param": ""
                }
                """.formatted(streamKey);

        mockMvc.perform(post("/api/srs/on-publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden())
                .andExpect(content().string("1"));

        Stream unchangedStream = streamRepository.findByStreamKey(streamKey).orElseThrow();
        assertThat(unchangedStream.getStatus()).isEqualTo("OFFLINE");
    }

    @Test
    @Transactional
    void whenOnPublishFormUrlEncoded_thenAcceptsValidStreamKey() throws Exception {
        Stream stream = createStream("OFFLINE");
        String streamKey = stream.getStreamKey();

        mockMvc.perform(post("/api/srs/on-publish")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("action", "on_publish")
                        .param("app", "live")
                        .param("stream", streamKey))
                .andExpect(status().isOk())
                .andExpect(content().string("0"));

        Stream updatedStream = streamRepository.findByStreamKey(streamKey).orElseThrow();
        assertThat(updatedStream.getStatus()).isEqualTo("LIVE");
    }

    @Test
    void whenOnPublishFormUrlEncodedWithInvalidKey_thenRejectsPublish() throws Exception {
        mockMvc.perform(post("/api/srs/on-publish")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("action", "on_publish")
                        .param("app", "live")
                        .param("stream", "unknown-key-xyz"))
                .andExpect(status().isForbidden())
                .andExpect(content().string("1"));
    }

    @Test
    @Transactional
    void whenOnPublishWithStreamKeyInParam_thenAcceptsValidStreamKey() throws Exception {
        Stream stream = createStream("OFFLINE");
        String streamKey = stream.getStreamKey();

        String payload = """
                {
                    "action": "on_publish",
                    "client_id": 1985,
                    "ip": "127.0.0.1",
                    "vhost": "__defaultVhost__",
                    "app": "live",
                    "stream": "",
                    "param": "?streamKey=%s"
                }
                """.formatted(streamKey);

        mockMvc.perform(post("/api/srs/on-publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(content().string("0"));

        Stream updatedStream = streamRepository.findByStreamKey(streamKey).orElseThrow();
        assertThat(updatedStream.getStatus()).isEqualTo("LIVE");
    }

    @Test
    @Transactional
    void whenOnPublishWithStreamPrefixLive_thenAcceptsValidStreamKey() throws Exception {
        Stream stream = createStream("OFFLINE");
        String streamKey = stream.getStreamKey();

        String payload = """
                {
                    "action": "on_publish",
                    "client_id": 1985,
                    "ip": "127.0.0.1",
                    "vhost": "__defaultVhost__",
                    "app": "live",
                    "stream": "live/%s"
                }
                """.formatted(streamKey);

        mockMvc.perform(post("/api/srs/on-publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(content().string("0"));

        Stream updatedStream = streamRepository.findByStreamKey(streamKey).orElseThrow();
        assertThat(updatedStream.getStatus()).isEqualTo("LIVE");
    }

    @Test
    void whenOnPublishWithEmptyStreamKey_thenRejectsPublish() throws Exception {
        String payload = """
                {
                    "action": "on_publish",
                    "client_id": 1985,
                    "ip": "127.0.0.1",
                    "vhost": "__defaultVhost__",
                    "app": "live",
                    "stream": ""
                }
                """;

        mockMvc.perform(post("/api/srs/on-publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden())
                .andExpect(content().string("1"));
    }

    @Test
    @Transactional
    void whenOnUnpublishForExistingStream_thenMarksStreamOffline() throws Exception {
        Stream stream = createStream("LIVE");
        String streamKey = stream.getStreamKey();

        String payload = """
                {
                    "action": "on_unpublish",
                    "client_id": 1985,
                    "ip": "127.0.0.1",
                    "vhost": "__defaultVhost__",
                    "app": "live",
                    "stream": "%s",
                    "param": ""
                }
                """.formatted(streamKey);

        mockMvc.perform(post("/api/srs/on-unpublish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(content().string("0"));

        Stream updatedStream = streamRepository.findByStreamKey(streamKey).orElseThrow();
        assertThat(updatedStream.getStatus()).isEqualTo("OFFLINE");
    }

    @Test
    @Transactional
    void whenOnUnpublishAliasForExistingStream_thenMarksStreamOffline() throws Exception {
        Stream stream = createStream("LIVE");
        String streamKey = stream.getStreamKey();

        String payload = """
                {
                    "action": "on_unpublish",
                    "client_id": 1985,
                    "ip": "127.0.0.1",
                    "vhost": "__defaultVhost__",
                    "app": "live",
                    "stream": "%s"
                }
                """.formatted(streamKey);

        mockMvc.perform(post("/api/srs/on_unpublish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(content().string("0"));

        Stream updatedStream = streamRepository.findByStreamKey(streamKey).orElseThrow();
        assertThat(updatedStream.getStatus()).isEqualTo("OFFLINE");
    }

    @Test
    void whenOnUnpublishWithInvalidStreamKey_thenRejectsAndNoStreamModified() throws Exception {
        String invalidKey = "invalid-key-" + UUID.randomUUID();

        String payload = """
                {
                    "action": "on_unpublish",
                    "client_id": 1985,
                    "ip": "127.0.0.1",
                    "vhost": "__defaultVhost__",
                    "app": "live",
                    "stream": "%s",
                    "param": ""
                }
                """.formatted(invalidKey);

        mockMvc.perform(post("/api/srs/on-unpublish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden())
                .andExpect(content().string("1"));
    }

    @Test
    void whenOnUnpublishWithMissingStreamKey_thenRejects() throws Exception {
        String payload = """
                {
                    "action": "on_unpublish",
                    "client_id": 1985,
                    "ip": "127.0.0.1",
                    "vhost": "__defaultVhost__",
                    "app": "live",
                    "stream": ""
                }
                """;

        mockMvc.perform(post("/api/srs/on-unpublish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden())
                .andExpect(content().string("1"));
    }

    @Test
    @Transactional
    void whenOnUnpublishWithInvalidApp_thenRejectsAndStreamRemainsLive() throws Exception {
        Stream stream = createStream("LIVE");
        String streamKey = stream.getStreamKey();

        String payload = """
                {
                    "action": "on_unpublish",
                    "client_id": 1985,
                    "ip": "127.0.0.1",
                    "vhost": "__defaultVhost__",
                    "app": "vod",
                    "stream": "%s"
                }
                """.formatted(streamKey);

        mockMvc.perform(post("/api/srs/on-unpublish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden())
                .andExpect(content().string("1"));

        Stream unchangedStream = streamRepository.findByStreamKey(streamKey).orElseThrow();
        assertThat(unchangedStream.getStatus()).isEqualTo("LIVE");
    }

    @Test
    @Transactional
    void whenOnUnpublishFormUrlEncoded_thenAcceptsValidStreamKey() throws Exception {
        Stream stream = createStream("LIVE");
        String streamKey = stream.getStreamKey();

        mockMvc.perform(post("/api/srs/on-unpublish")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("action", "on_unpublish")
                        .param("app", "live")
                        .param("stream", streamKey))
                .andExpect(status().isOk())
                .andExpect(content().string("0"));

        Stream updatedStream = streamRepository.findByStreamKey(streamKey).orElseThrow();
        assertThat(updatedStream.getStatus()).isEqualTo("OFFLINE");
    }

    @Test
    @Transactional
    void whenOnUnpublishForOneStream_thenUnrelatedStreamIsNotModified() throws Exception {
        Stream stream1 = createStream("LIVE");
        Stream stream2 = createStream("LIVE");

        String payload = """
                {
                    "action": "on_unpublish",
                    "client_id": 1985,
                    "ip": "127.0.0.1",
                    "vhost": "__defaultVhost__",
                    "app": "live",
                    "stream": "%s"
                }
                """.formatted(stream1.getStreamKey());

        mockMvc.perform(post("/api/srs/on-unpublish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(content().string("0"));

        Stream updatedStream1 = streamRepository.findByStreamKey(stream1.getStreamKey()).orElseThrow();
        Stream updatedStream2 = streamRepository.findByStreamKey(stream2.getStreamKey()).orElseThrow();

        assertThat(updatedStream1.getStatus()).isEqualTo("OFFLINE");
        assertThat(updatedStream2.getStatus()).isEqualTo("LIVE");
    }

    @Test
    @Transactional
    void whenSrsWebhookCalledWithoutJwt_thenAllowedWithoutAuthentication() throws Exception {
        Stream stream = createStream("LIVE");
        String streamKey = stream.getStreamKey();

        String payload = """
                {
                    "action": "on_unpublish",
                    "client_id": 1985,
                    "ip": "127.0.0.1",
                    "vhost": "__defaultVhost__",
                    "app": "live",
                    "stream": "%s"
                }
                """.formatted(streamKey);

        mockMvc.perform(post("/api/srs/on-unpublish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(content().string("0"));
    }

    @Test
    void whenOnPlayWithStream_thenReturnsSuccess() throws Exception {
        mockMvc.perform(post("/api/srs/on-play")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"on_play\",\"client_id\":\"viewer-1\",\"stream\":\"any-key\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string("0"));

        mockMvc.perform(post("/api/srs/on_play")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"on_play\",\"client_id\":\"viewer-2\",\"stream\":\"any-key\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string("0"));
    }

    @Test
    void whenOnStopWithStream_thenReturnsSuccess() throws Exception {
        mockMvc.perform(post("/api/srs/on-stop")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"on_stop\",\"client_id\":\"viewer-1\",\"stream\":\"any-key\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string("0"));

        mockMvc.perform(post("/api/srs/on_stop")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"on_stop\",\"client_id\":\"viewer-2\",\"stream\":\"any-key\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string("0"));
    }

    @Test
    @Transactional
    void whenPublicStreamsQueried_thenViewerCountIsPresentAndOfflineStreamsHaveZeroViewers() throws Exception {
        Stream stream = createStream("OFFLINE");

        mockMvc.perform(get("/api/streams/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[?(@.id == " + stream.getId() + ")].viewerCount").value(0))
                .andExpect(jsonPath("$[?(@.id == " + stream.getId() + ")].status").value("OFFLINE"));
    }

    @Test
    @Transactional
    void whenPublicStreamsQueried_thenStreamKeyIsNotExposed() throws Exception {
        Stream stream = createStream("OFFLINE");

        mockMvc.perform(get("/api/streams/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[?(@.id == " + stream.getId() + ")].streamKey").doesNotExist());
    }

    @Test
    @Transactional
    void whenLiveStreamHasNoViewers_thenZeroViewersReportedAndPublisherExcluded() throws Exception {
        Stream stream = createStream("LIVE");

        mockMvc.perform(get("/api/streams/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[?(@.id == " + stream.getId() + ")].viewerCount").value(0))
                .andExpect(jsonPath("$[?(@.id == " + stream.getId() + ")].status").value("LIVE"));
    }

    @Test
    @Transactional
    void whenLiveStreamHasOneViewer_thenViewerCountIsOne() throws Exception {
        Stream stream = createStream("LIVE");
        String streamKey = stream.getStreamKey();

        mockMvc.perform(post("/api/srs/on-play")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"on_play\",\"client_id\":\"viewer-1\",\"stream\":\"" + streamKey + "\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string("0"));

        mockMvc.perform(get("/api/streams/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + stream.getId() + ")].viewerCount").value(1));
    }

    @Test
    @Transactional
    void whenLiveStreamHasMultipleViewers_thenViewerCountReflectsAllViewers() throws Exception {
        Stream stream = createStream("LIVE");
        String streamKey = stream.getStreamKey();

        mockMvc.perform(post("/api/srs/on-play")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"on_play\",\"client_id\":\"viewer-1\",\"stream\":\"" + streamKey + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/srs/on-play")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"on_play\",\"client_id\":\"viewer-2\",\"stream\":\"" + streamKey + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/srs/on-play")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"on_play\",\"client_id\":\"viewer-3\",\"stream\":\"" + streamKey + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/streams/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + stream.getId() + ")].viewerCount").value(3));

        // When one viewer disconnects via on_stop
        mockMvc.perform(post("/api/srs/on-stop")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"on_stop\",\"client_id\":\"viewer-2\",\"stream\":\"" + streamKey + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/streams/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + stream.getId() + ")].viewerCount").value(2));
    }

    @Test
    @Transactional
    void whenOfflineStreamHasActivePlaySessions_thenViewerCountIsAlwaysZero() throws Exception {
        Stream stream = createStream("OFFLINE");
        String streamKey = stream.getStreamKey();

        srsService.registerPlaySession(streamKey, "stale-viewer-1");
        srsService.registerPlaySession(streamKey, "stale-viewer-2");

        mockMvc.perform(get("/api/streams/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + stream.getId() + ")].viewerCount").value(0))
                .andExpect(jsonPath("$[?(@.id == " + stream.getId() + ")].status").value("OFFLINE"));
    }

    @Test
    @Transactional
    void whenPlayStopOnInvalidOrMissingStream_thenUnrelatedStreamIsNotAffected() throws Exception {
        Stream validStream = createStream("LIVE");
        String validKey = validStream.getStreamKey();

        srsService.registerPlaySession(validKey, "valid-viewer-1");

        // on_play with missing / unknown stream
        mockMvc.perform(post("/api/srs/on-play")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"on_play\",\"client_id\":\"bogus-1\",\"stream\":\"\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/srs/on-play")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"on_play\",\"client_id\":\"bogus-2\",\"stream\":\"unknown-key\"}"))
                .andExpect(status().isOk());

        // on_stop with missing / unknown stream
        mockMvc.perform(post("/api/srs/on-stop")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"on_stop\",\"client_id\":\"bogus-1\",\"stream\":\"\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/streams/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + validStream.getId() + ")].viewerCount").value(1));
    }

    @Test
    @Transactional
    void whenLiveStreamIsUnpublished_thenViewerCountDropsToZero() throws Exception {
        Stream stream = createStream("LIVE");
        String streamKey = stream.getStreamKey();

        srsService.registerPlaySession(streamKey, "viewer-1");
        srsService.registerPlaySession(streamKey, "viewer-2");

        // unpublish
        mockMvc.perform(post("/api/srs/on-unpublish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"on_unpublish\",\"app\":\"live\",\"stream\":\"" + streamKey + "\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string("0"));

        mockMvc.perform(get("/api/streams/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + stream.getId() + ")].viewerCount").value(0))
                .andExpect(jsonPath("$[?(@.id == " + stream.getId() + ")].status").value("OFFLINE"));
    }

    @Test
    void whenProtectedEndpointsCalledWithoutJwt_thenStillProtected() throws Exception {
        mockMvc.perform(get("/api/streams"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/streams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Protected\",\"category\":\"Tech\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }
}

