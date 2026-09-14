package com.streamplatform.streamapi.service.impl;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamplatform.streamapi.service.SrsService;

@Service
public class SrsServiceImpl implements SrsService {

    private static final Logger logger = LoggerFactory.getLogger(SrsServiceImpl.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Map<String, Set<String>> activeSessions = new ConcurrentHashMap<>();

    @Value("${srs.api.url}")
    private String srsApiUrl;

    public SrsServiceImpl() {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public boolean isStreamLive(String streamKey) {
        if (streamKey == null || streamKey.isBlank()) {
            return false;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(srsApiUrl + "/api/v1/streams/"))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return false;
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode streams = root.path("streams");

            if (!streams.isArray()) {
                return false;
            }

            for (JsonNode stream : streams) {
                String name = stream.path("name").asText();

                if (streamKey.equals(name)) {
                    return stream.path("publish")
                            .path("active")
                            .asBoolean(false);
                }
            }

            return false;

        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public int getViewerCount(String streamKey) {
        if (streamKey == null || streamKey.isBlank()) {
            return 0;
        }

        Map<String, Integer> counts = getViewerCounts();
        return counts.getOrDefault(streamKey, 0);
    }

    @Override
    public Map<String, Integer> getViewerCounts() {
        Map<String, Integer> result = new HashMap<>();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(srsApiUrl + "/api/v1/streams/"))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode streams = root.path("streams");

                if (streams.isArray()) {
                    for (JsonNode stream : streams) {
                        String name = stream.path("name").asText();
                        boolean publishActive = stream.path("publish")
                                .path("active")
                                .asBoolean(false);

                        if (publishActive) {
                            int totalClients = stream.path("clients").asInt(0);
                            int viewers = Math.max(0, totalClients - 1);

                            // Check if supplementary on_play/on_stop session tracker has higher count
                            Set<String> sessionSet = activeSessions.get(name);
                            if (sessionSet != null && sessionSet.size() > viewers) {
                                viewers = sessionSet.size();
                            }

                            result.put(name, viewers);
                        } else {
                            result.put(name, 0);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Could not retrieve streams from SRS HTTP API: {}", e.getMessage());
        }

        // If SRS API did not return or returned 0, ensure any active session tracker is considered
        for (Map.Entry<String, Set<String>> entry : activeSessions.entrySet()) {
            if (!result.containsKey(entry.getKey())) {
                result.put(entry.getKey(), Math.max(0, entry.getValue().size()));
            }
        }

        return Collections.unmodifiableMap(result);
    }

    @Override
    public void registerPlaySession(String streamKey, String clientId) {
        if (streamKey == null || streamKey.isBlank() || clientId == null || clientId.isBlank()) {
            return;
        }
        activeSessions.computeIfAbsent(streamKey.trim(), k -> ConcurrentHashMap.newKeySet()).add(clientId.trim());
        logger.debug("Registered viewer play session for stream key: {}", streamKey);
    }

    @Override
    public void unregisterPlaySession(String streamKey, String clientId) {
        if (streamKey == null || streamKey.isBlank() || clientId == null || clientId.isBlank()) {
            return;
        }
        Set<String> set = activeSessions.get(streamKey.trim());
        if (set != null) {
            set.remove(clientId.trim());
            if (set.isEmpty()) {
                activeSessions.remove(streamKey.trim());
            }
        }
        logger.debug("Unregistered viewer play session for stream key: {}", streamKey);
    }

    @Override
    public void clearPlaySessions(String streamKey) {
        if (streamKey == null || streamKey.isBlank()) {
            return;
        }
        activeSessions.remove(streamKey.trim());
        logger.debug("Cleared viewer play sessions for stream key: {}", streamKey);
    }
}
