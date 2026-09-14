package com.streamplatform.streamapi.controller;

import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.streamplatform.streamapi.dto.SrsCallbackRequest;
import com.streamplatform.streamapi.entity.Stream;
import com.streamplatform.streamapi.repository.StreamRepository;
import com.streamplatform.streamapi.service.SrsService;
import com.streamplatform.streamapi.service.StreamService;

@RestController
@RequestMapping({"/api/srs", "/api/streams"})
public class SrsWebhookController {

    private static final Logger logger = LoggerFactory.getLogger(SrsWebhookController.class);

    private final StreamRepository streamRepository;
    private final SrsService srsService;
    private final StreamService streamService;

    public SrsWebhookController(StreamRepository streamRepository, SrsService srsService, StreamService streamService) {
        this.streamRepository = streamRepository;
        this.srsService = srsService;
        this.streamService = streamService;
    }

    @PostMapping(value = {"/on-publish", "/on_publish"}, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Integer> onPublishJson(@RequestBody(required = false) SrsCallbackRequest request) {
        return processOnPublish(request);
    }

    @PostMapping(value = {"/on-publish", "/on_publish"}, consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Integer> onPublishForm(@RequestParam Map<String, String> params) {
        return processOnPublish(buildRequestFromParams(params));
    }

    @PostMapping(value = {"/on-unpublish", "/on_unpublish"}, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Integer> onUnpublishJson(@RequestBody(required = false) SrsCallbackRequest request) {
        return processOnUnpublish(request);
    }

    @PostMapping(value = {"/on-unpublish", "/on_unpublish"}, consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Integer> onUnpublishForm(@RequestParam Map<String, String> params) {
        return processOnUnpublish(buildRequestFromParams(params));
    }

    @PostMapping(value = {"/on-play", "/on_play"}, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Integer> onPlayJson(@RequestBody(required = false) SrsCallbackRequest request) {
        return processOnPlay(request);
    }

    @PostMapping(value = {"/on-play", "/on_play"}, consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Integer> onPlayForm(@RequestParam Map<String, String> params) {
        return processOnPlay(buildRequestFromParams(params));
    }

    @PostMapping(value = {"/on-stop", "/on_stop"}, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Integer> onStopJson(@RequestBody(required = false) SrsCallbackRequest request) {
        return processOnStop(request);
    }

    @PostMapping(value = {"/on-stop", "/on_stop"}, consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Integer> onStopForm(@RequestParam Map<String, String> params) {
        return processOnStop(buildRequestFromParams(params));
    }

    private SrsCallbackRequest buildRequestFromParams(Map<String, String> params) {
        SrsCallbackRequest req = new SrsCallbackRequest();
        req.setAction(params.get("action"));
        req.setStream(params.get("stream"));
        req.setParam(params.get("param"));
        req.setApp(params.get("app"));
        req.setVhost(params.get("vhost"));
        req.setClientId(params.get("client_id"));
        return req;
    }

    private ResponseEntity<Integer> processOnPublish(SrsCallbackRequest request) {
        if (request == null) {
            logger.warn("SRS on_publish received null request");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(1);
        }

        if (request.getApp() != null && !request.getApp().isBlank()) {
            String app = request.getApp().trim();
            if (!"live".equalsIgnoreCase(app)) {
                logger.warn("SRS on_publish rejected: invalid app '{}'", app);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(1);
            }
        }

        String streamKey = extractStreamKey(request);
        if (streamKey == null || streamKey.isBlank()) {
            logger.warn("SRS on_publish received request with empty stream key");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(1);
        }

        boolean published = streamService.publishStream(request.getApp(), streamKey);
        if (!published) {
            logger.warn("SRS on_publish rejected: stream key not found in database: {}", streamKey);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(1);
        }

        logger.info("SRS on_publish accepted for stream key {}", streamKey);
        return ResponseEntity.ok(0);
    }

    private ResponseEntity<Integer> processOnUnpublish(SrsCallbackRequest request) {
        if (request == null) {
            logger.warn("SRS on_unpublish received null request");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(1);
        }

        if (request.getApp() != null && !request.getApp().isBlank()) {
            String app = request.getApp().trim();
            if (!"live".equalsIgnoreCase(app)) {
                logger.warn("SRS on_unpublish rejected: invalid app '{}'", app);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(1);
            }
        }

        String streamKey = extractStreamKey(request);
        if (streamKey == null || streamKey.isBlank()) {
            logger.warn("SRS on_unpublish received request with empty stream key");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(1);
        }

        boolean unpublished = streamService.unpublishStream(request.getApp(), streamKey);
        if (!unpublished) {
            logger.warn("SRS on_unpublish rejected: stream key not found in database: {}", streamKey);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(1);
        }

        logger.info("SRS on_unpublish accepted for stream key {}", streamKey);
        return ResponseEntity.ok(0);
    }

    private ResponseEntity<Integer> processOnPlay(SrsCallbackRequest request) {
        if (request == null) {
            return ResponseEntity.ok(0);
        }

        String streamKey = extractStreamKey(request);
        String clientId = request.getClientId();
        if (streamKey != null && !streamKey.isBlank() && clientId != null && !clientId.isBlank()) {
            srsService.registerPlaySession(streamKey, clientId);
        }

        return ResponseEntity.ok(0);
    }

    private ResponseEntity<Integer> processOnStop(SrsCallbackRequest request) {
        if (request == null) {
            return ResponseEntity.ok(0);
        }

        String streamKey = extractStreamKey(request);
        String clientId = request.getClientId();
        if (streamKey != null && !streamKey.isBlank() && clientId != null && !clientId.isBlank()) {
            srsService.unregisterPlaySession(streamKey, clientId);
        }

        return ResponseEntity.ok(0);
    }

    private String extractStreamKey(SrsCallbackRequest request) {
        String streamKey = request.getStream();

        if ((streamKey == null || streamKey.isBlank()) && request.getParam() != null) {
            streamKey = extractStreamKeyFromParam(request.getParam());
        }

        if (streamKey != null && streamKey.contains("?")) {
            streamKey = streamKey.substring(0, streamKey.indexOf("?"));
        }

        if (streamKey != null) {
            streamKey = streamKey.trim();
            if (streamKey.startsWith("live/")) {
                streamKey = streamKey.substring("live/".length());
            } else if (streamKey.startsWith("/")) {
                streamKey = streamKey.substring(1);
            }
        }

        return streamKey != null ? streamKey.trim() : null;
    }

    private String extractStreamKeyFromParam(String param) {
        if (param == null || param.isBlank()) {
            return null;
        }

        String p = param.startsWith("?") ? param.substring(1) : param;
        for (String pair : p.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && ("streamKey".equalsIgnoreCase(kv[0]) || "key".equalsIgnoreCase(kv[0]) || "stream".equalsIgnoreCase(kv[0]))) {
                return kv[1];
            }
        }

        return null;
    }
}
