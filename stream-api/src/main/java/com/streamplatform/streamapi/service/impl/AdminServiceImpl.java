package com.streamplatform.streamapi.service.impl;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.streamplatform.streamapi.dto.AdminStatsResponse;
import com.streamplatform.streamapi.dto.StreamResponse;
import com.streamplatform.streamapi.dto.UserResponse;
import com.streamplatform.streamapi.dto.VideoResponse;
import com.streamplatform.streamapi.entity.Stream;
import com.streamplatform.streamapi.entity.User;
import com.streamplatform.streamapi.entity.Video;
import com.streamplatform.streamapi.exception.ApiException;
import com.streamplatform.streamapi.repository.StreamRepository;
import com.streamplatform.streamapi.repository.UserRepository;
import com.streamplatform.streamapi.repository.VideoRepository;
import com.streamplatform.streamapi.security.CustomUserDetails;
import com.streamplatform.streamapi.service.AdminService;
import com.streamplatform.streamapi.service.MinioStorageService;
import com.streamplatform.streamapi.service.SrsService;

@Service
public class AdminServiceImpl implements AdminService {

    private final UserRepository userRepository;
    private final StreamRepository streamRepository;
    private final VideoRepository videoRepository;
    private final MinioStorageService minioStorageService;
    private final SrsService srsService;

    @Value("${srs.playback.url-prefix:http://localhost:8080/live}")
    private String playbackUrlPrefix;

    public AdminServiceImpl(
            UserRepository userRepository,
            StreamRepository streamRepository,
            VideoRepository videoRepository,
            MinioStorageService minioStorageService,
            SrsService srsService) {
        this.userRepository = userRepository;
        this.streamRepository = streamRepository;
        this.videoRepository = videoRepository;
        this.minioStorageService = minioStorageService;
        this.srsService = srsService;
    }

    private User getAdminUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails)) {
            throw new ApiException("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new ApiException("User not found", HttpStatus.NOT_FOUND));

        String role = user.getRole();
        if (role == null || (!role.equalsIgnoreCase("ADMIN") && !role.equalsIgnoreCase("ROLE_ADMIN"))) {
            throw new ApiException("Forbidden: Administrator access required", HttpStatus.FORBIDDEN);
        }
        return user;
    }

    private StreamResponse mapStreamToResponse(Stream stream) {
        StreamResponse response = new StreamResponse();
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
            response.setViewerCount(srsService.getViewerCount(stream.getStreamKey()));
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
        return response;
    }

    private VideoResponse mapVideoToResponse(Video video) {
        VideoResponse response = new VideoResponse();
        response.setId(video.getId());
        response.setTitle(video.getTitle());
        response.setDescription(video.getDescription());
        response.setCategory(video.getCategory());
        response.setOriginalFilename(video.getOriginalFilename());
        response.setContentType(video.getContentType());
        response.setFileSize(video.getFileSize());
        response.setThumbnailUrl(video.getThumbnailUrl());
        response.setStatus(video.getStatus());
        response.setPublic(video.isPublic());
        response.setCreatedAt(video.getCreatedAt());
        response.setUpdatedAt(video.getUpdatedAt());
        response.setViews(video.getViews());
        response.setPlaybackUrl("/api/videos/" + video.getId() + "/playback");

        if (video.getUser() != null) {
            response.setUserId(video.getUser().getId());
            response.setCreatorUsername(video.getUser().getUsername());
            response.setCreatorFullName(video.getUser().getFullName());
        }
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public AdminStatsResponse getPlatformStats() {
        getAdminUser();

        AdminStatsResponse stats = new AdminStatsResponse();
        stats.setTotalUsers(userRepository.count());
        stats.setActiveUsers(userRepository.countByEnabledTrue());
        stats.setTotalStreams(streamRepository.count());
        stats.setLiveStreams(streamRepository.countByStatus("LIVE"));
        stats.setScheduledStreams(streamRepository.countByStatus("SCHEDULED"));
        stats.setEndedStreams(streamRepository.countByStatus("ENDED"));
        stats.setTotalVideos(videoRepository.count());
        stats.setTotalVideoViews(videoRepository.sumTotalViews());
        stats.setTotalStorageBytes(videoRepository.sumTotalFileSize());

        Integer maxPeak = streamRepository.maxPlatformPeakViewers();
        stats.setMaxConcurrentViewers(maxPeak != null ? maxPeak : 0);

        Long totalDuration = streamRepository.sumPlatformDuration();
        stats.setTotalStreamDurationSeconds(totalDuration != null ? totalDuration : 0L);

        return stats;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        getAdminUser();
        return userRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(UserResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional
    public UserResponse updateUserStatus(Long userId, boolean enabled) {
        User admin = getAdminUser();
        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("User not found", HttpStatus.NOT_FOUND));

        if (targetUser.getId().equals(admin.getId()) && !enabled) {
            throw new ApiException("Cannot disable your own administrator account", HttpStatus.BAD_REQUEST);
        }

        targetUser.setEnabled(enabled);
        User saved = userRepository.save(targetUser);
        return UserResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public UserResponse updateUserRole(Long userId, String role) {
        User admin = getAdminUser();
        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("User not found", HttpStatus.NOT_FOUND));

        String cleanRole = role.trim().toUpperCase();
        if (cleanRole.startsWith("ROLE_")) {
            cleanRole = cleanRole.substring("ROLE_".length());
        }

        if (!cleanRole.equals("USER") && !cleanRole.equals("ADMIN")) {
            throw new ApiException("Invalid role. Permitted roles: USER, ADMIN", HttpStatus.BAD_REQUEST);
        }

        if (targetUser.getId().equals(admin.getId()) && cleanRole.equals("USER")) {
            throw new ApiException("Cannot demote your own administrator account", HttpStatus.BAD_REQUEST);
        }

        targetUser.setRole(cleanRole);
        User saved = userRepository.save(targetUser);
        return UserResponse.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StreamResponse> getAllStreams() {
        getAdminUser();
        return streamRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::mapStreamToResponse)
                .toList();
    }

    @Override
    @Transactional
    public void deleteStreamAsAdmin(Long streamId) {
        getAdminUser();
        Stream stream = streamRepository.findById(streamId)
                .orElseThrow(() -> new ApiException("Stream not found", HttpStatus.NOT_FOUND));

        try {
            srsService.clearPlaySessions(stream.getStreamKey());
        } catch (Exception ignored) {
        }

        streamRepository.delete(stream);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VideoResponse> getAllVideos() {
        getAdminUser();
        return videoRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::mapVideoToResponse)
                .toList();
    }

    @Override
    @Transactional
    public void deleteVideoAsAdmin(Long videoId) {
        getAdminUser();
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new ApiException("Video not found", HttpStatus.NOT_FOUND));

        try {
            minioStorageService.deleteObject(video.getObjectKey());
        } catch (Exception ignored) {
        }

        videoRepository.delete(video);
    }
}
