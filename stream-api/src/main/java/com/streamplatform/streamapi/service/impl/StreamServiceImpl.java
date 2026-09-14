package com.streamplatform.streamapi.service.impl;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.streamplatform.streamapi.dto.CreateStreamRequest;
import com.streamplatform.streamapi.dto.ScheduleStreamRequest;
import com.streamplatform.streamapi.dto.StreamResponse;
import com.streamplatform.streamapi.entity.Stream;
import com.streamplatform.streamapi.entity.User;
import com.streamplatform.streamapi.exception.ApiException;
import com.streamplatform.streamapi.repository.StreamRepository;
import com.streamplatform.streamapi.repository.UserRepository;
import com.streamplatform.streamapi.security.CustomUserDetails;
import com.streamplatform.streamapi.service.NotificationService;
import com.streamplatform.streamapi.service.SrsService;
import com.streamplatform.streamapi.service.StreamService;

@Service
public class StreamServiceImpl implements StreamService {

    private static final Logger logger = LoggerFactory.getLogger(StreamServiceImpl.class);

    private final StreamRepository streamRepository;
    private final UserRepository userRepository;
    private final SrsService srsService;
    private final NotificationService notificationService;

    @Value("${srs.playback.url-prefix:http://localhost:8080/live}")
    private String playbackUrlPrefix;

    public StreamServiceImpl(
            StreamRepository streamRepository,
            UserRepository userRepository,
            SrsService srsService,
            NotificationService notificationService) {

        this.streamRepository = streamRepository;
        this.userRepository = userRepository;
        this.srsService = srsService;
        this.notificationService = notificationService;
    }

    private User getCurrentUser() {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null ||
                !(authentication.getPrincipal() instanceof CustomUserDetails)) {
            throw new ApiException("Unauthorized", HttpStatus.UNAUTHORIZED);
        }

        CustomUserDetails userDetails =
                (CustomUserDetails) authentication.getPrincipal();

        return userRepository.findById(userDetails.getId())
                .orElseThrow(() ->
                        new ApiException("User not found", HttpStatus.NOT_FOUND));
    }

    private String generateStreamKey() {

        byte[] randomBytes = new byte[16];
        new SecureRandom().nextBytes(randomBytes);

        return HexFormat.of().formatHex(randomBytes);
    }

    private void populateCommonFields(StreamResponse response, Stream stream) {
        response.setId(stream.getId());
        response.setTitle(stream.getTitle());
        response.setDescription(stream.getDescription());
        response.setCategory(stream.getCategory());
        response.setPublic(stream.isPublic());
        response.setCreatedAt(stream.getCreatedAt());
        response.setScheduledStartTime(stream.getScheduledStartTime());
        response.setScheduledEndTime(stream.getScheduledEndTime());
        response.setStartedAt(stream.getStartedAt());
        response.setEndedAt(stream.getEndedAt());
        response.setDurationSeconds(stream.getDurationSeconds());
        response.setPeakViewers(stream.getPeakViewers());

        if (stream.getUser() != null) {
            response.setUserId(stream.getUser().getId());
            response.setCreatorUsername(stream.getUser().getUsername());
            response.setCreatorFullName(stream.getUser().getFullName());
        }

        boolean live = "LIVE".equalsIgnoreCase(stream.getStatus()) || srsService.isStreamLive(stream.getStreamKey());

        if (live) {
            response.setStatus("LIVE");
            int viewers = srsService.getViewerCount(stream.getStreamKey());
            response.setViewerCount(viewers);
            if (stream.getPeakViewers() == null || viewers > stream.getPeakViewers()) {
                stream.setPeakViewers(viewers);
            }
        } else if ("SCHEDULED".equalsIgnoreCase(stream.getStatus())) {
            response.setStatus("SCHEDULED");
            response.setViewerCount(0);
        } else if ("ENDED".equalsIgnoreCase(stream.getStatus())) {
            response.setStatus("ENDED");
            response.setViewerCount(0);
        } else if ("CANCELLED".equalsIgnoreCase(stream.getStatus())) {
            response.setStatus("CANCELLED");
            response.setViewerCount(0);
        } else {
            response.setStatus("OFFLINE");
            response.setViewerCount(0);
        }

        response.setPlaybackUrl(playbackUrlPrefix.replaceAll("/+$", "") + "/" + stream.getStreamKey() + ".m3u8");
    }

    private StreamResponse mapToResponse(Stream stream) {
        StreamResponse response = new StreamResponse();
        populateCommonFields(response, stream);
        response.setStreamKey(stream.getStreamKey());
        return response;
    }

    private StreamResponse mapToPublicResponse(Stream stream) {
        StreamResponse response = new StreamResponse();
        populateCommonFields(response, stream);
        response.setStreamKey(null);
        return response;
    }



    @Override
    public StreamResponse createStream(CreateStreamRequest request) {

        User user = getCurrentUser();

        Stream stream = new Stream();

        stream.setTitle(request.getTitle());
        stream.setDescription(request.getDescription());
        stream.setCategory(request.getCategory());
        stream.setPublic(request.isPublic());

        stream.setStatus("OFFLINE");

        String streamKey;

        do {
            streamKey = generateStreamKey();
        } while (streamRepository.existsByStreamKey(streamKey));

        stream.setStreamKey(streamKey);
        stream.setUser(user);

        Stream savedStream = streamRepository.save(stream);

        try {
            notificationService.notifyStreamCreated(savedStream);
        } catch (Exception e) {
            // Non-blocking notification dispatch
        }

        return mapToResponse(savedStream);
    }

    @Override
    public List<StreamResponse> getMyStreams() {

        User user = getCurrentUser();

        return streamRepository.findByUser(user)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    public List<StreamResponse> getPublicStreams() {

        return streamRepository.findPublicStreams()
                .stream()
                .map(this::mapToPublicResponse)
                .toList();
    }

    @Override
    public StreamResponse getStreamById(Long id) {

        User user = getCurrentUser();

        Stream stream = streamRepository.findById(id)
                .orElseThrow(() -> new ApiException("Stream not found.", HttpStatus.NOT_FOUND));

        if (!stream.getUser().getId().equals(user.getId())) {
            throw new ApiException("Access denied.", HttpStatus.FORBIDDEN);
        }

        return mapToResponse(stream);
    }

    @Override
    public StreamResponse updateStream(
            Long id,
            CreateStreamRequest request) {

        User user = getCurrentUser();

        Stream stream = streamRepository.findById(id)
                .orElseThrow(() -> new ApiException("Stream not found.", HttpStatus.NOT_FOUND));

        if (!stream.getUser().getId().equals(user.getId())) {
            throw new ApiException("Access denied.", HttpStatus.FORBIDDEN);
        }

        stream.setTitle(request.getTitle());
        stream.setDescription(request.getDescription());
        stream.setCategory(request.getCategory());
        stream.setPublic(request.isPublic());

        Stream updatedStream = streamRepository.save(stream);

        return mapToResponse(updatedStream);
    }

    @Override
    public void deleteStream(Long id) {

        User user = getCurrentUser();

        Stream stream = streamRepository.findById(id)
                .orElseThrow(() -> new ApiException("Stream not found.", HttpStatus.NOT_FOUND));

        if (!stream.getUser().getId().equals(user.getId())) {
            throw new ApiException("Access denied.", HttpStatus.FORBIDDEN);
        }

        streamRepository.delete(stream);
    }

    @Override
    @Transactional
    public StreamResponse scheduleStream(ScheduleStreamRequest request) {
        User user = getCurrentUser();

        if (request.getScheduledStartTime() == null) {
            throw new ApiException("Scheduled start time is required.", HttpStatus.BAD_REQUEST);
        }
        if (request.getScheduledStartTime().isBefore(LocalDateTime.now().minusMinutes(5))) {
            throw new ApiException("Scheduled start time must be in the future.", HttpStatus.BAD_REQUEST);
        }
        if (request.getScheduledEndTime() != null && request.getScheduledEndTime().isBefore(request.getScheduledStartTime())) {
            throw new ApiException("Scheduled end time must be after scheduled start time.", HttpStatus.BAD_REQUEST);
        }

        Stream stream = new Stream();
        stream.setTitle(request.getTitle());
        stream.setDescription(request.getDescription());
        stream.setCategory(request.getCategory());
        stream.setPublic(request.isPublic());
        stream.setStatus("SCHEDULED");
        stream.setScheduledStartTime(request.getScheduledStartTime());
        stream.setScheduledEndTime(request.getScheduledEndTime());

        String streamKey;
        do {
            streamKey = generateStreamKey();
        } while (streamRepository.existsByStreamKey(streamKey));

        stream.setStreamKey(streamKey);
        stream.setUser(user);

        Stream savedStream = streamRepository.save(stream);

        try {
            notificationService.notifyStreamCreated(savedStream);
        } catch (Exception e) {
            logger.warn("Failed to dispatch stream scheduled notification: {}", e.getMessage());
        }

        return mapToResponse(savedStream);
    }

    @Override
    public List<StreamResponse> getPublicScheduledStreams() {
        return streamRepository.findPublicScheduledStreams()
                .stream()
                .map(this::mapToPublicResponse)
                .toList();
    }

    @Override
    @Transactional
    public StreamResponse cancelScheduledStream(Long id) {
        User user = getCurrentUser();

        Stream stream = streamRepository.findById(id)
                .orElseThrow(() -> new ApiException("Stream not found.", HttpStatus.NOT_FOUND));

        if (!stream.getUser().getId().equals(user.getId())) {
            throw new ApiException("Access denied.", HttpStatus.FORBIDDEN);
        }

        if ("LIVE".equalsIgnoreCase(stream.getStatus()) || srsService.isStreamLive(stream.getStreamKey())) {
            throw new ApiException("Cannot cancel an active live stream.", HttpStatus.BAD_REQUEST);
        }

        stream.setStatus("CANCELLED");
        Stream updated = streamRepository.save(stream);
        return mapToResponse(updated);
    }

    @Override
    public boolean validateStreamKey(String streamKey) {
        if (streamKey == null || streamKey.isBlank()) {
            return false;
        }
        return streamRepository.existsByStreamKey(streamKey.trim());
    }

    @Override
    @Transactional
    public boolean publishStream(String app, String streamKey) {
        if (app != null && !app.isBlank() && !"live".equalsIgnoreCase(app.trim())) {
            return false;
        }
        if (streamKey == null || streamKey.isBlank()) {
            return false;
        }

        String cleanedKey = streamKey.trim();
        if (cleanedKey.startsWith("live/")) {
            cleanedKey = cleanedKey.substring("live/".length());
        } else if (cleanedKey.startsWith("/")) {
            cleanedKey = cleanedKey.substring(1);
        }

        Optional<Stream> streamOpt = streamRepository.findByStreamKey(cleanedKey);
        if (streamOpt.isEmpty()) {
            return false;
        }

        Stream stream = streamOpt.get();
        stream.setStatus("LIVE");
        if (stream.getStartedAt() == null) {
            stream.setStartedAt(LocalDateTime.now());
        }
        streamRepository.save(stream);

        try {
            notificationService.notifyStreamLive(stream);
        } catch (Exception e) {
            logger.warn("Failed to dispatch stream live notification: {}", e.getMessage());
        }

        return true;
    }

    @Override
    @Transactional
    public boolean unpublishStream(String app, String streamKey) {
        if (app != null && !app.isBlank() && !"live".equalsIgnoreCase(app.trim())) {
            return false;
        }
        if (streamKey == null || streamKey.isBlank()) {
            return false;
        }

        String cleanedKey = streamKey.trim();
        if (cleanedKey.startsWith("live/")) {
            cleanedKey = cleanedKey.substring("live/".length());
        } else if (cleanedKey.startsWith("/")) {
            cleanedKey = cleanedKey.substring(1);
        }

        Optional<Stream> streamOpt = streamRepository.findByStreamKey(cleanedKey);
        if (streamOpt.isEmpty()) {
            return false;
        }

        Stream stream = streamOpt.get();
        LocalDateTime now = LocalDateTime.now();
        stream.setEndedAt(now);
        if (stream.getStartedAt() != null) {
            stream.setDurationSeconds(java.time.Duration.between(stream.getStartedAt(), now).getSeconds());
        }
        int currentViewers = srsService.getViewerCount(cleanedKey);
        if (stream.getPeakViewers() == null || currentViewers > stream.getPeakViewers()) {
            stream.setPeakViewers(currentViewers);
        }
        if (stream.getScheduledStartTime() != null) {
            stream.setStatus("ENDED");
        } else {
            stream.setStatus("OFFLINE");
        }
        streamRepository.save(stream);
        srsService.clearPlaySessions(cleanedKey);

        try {
            notificationService.notifyStreamEnded(stream);
        } catch (Exception e) {
            logger.warn("Failed to dispatch stream ended notification: {}", e.getMessage());
        }

        return true;
    }
}