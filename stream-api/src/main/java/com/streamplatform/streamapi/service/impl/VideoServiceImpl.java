package com.streamplatform.streamapi.service.impl;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.streamplatform.streamapi.dto.UpdateVideoRequest;
import com.streamplatform.streamapi.dto.VideoResponse;
import com.streamplatform.streamapi.entity.User;
import com.streamplatform.streamapi.entity.Video;
import com.streamplatform.streamapi.exception.ApiException;
import com.streamplatform.streamapi.repository.UserRepository;
import com.streamplatform.streamapi.repository.VideoRepository;
import com.streamplatform.streamapi.security.CustomUserDetails;
import com.streamplatform.streamapi.service.MinioStorageService;
import com.streamplatform.streamapi.service.NotificationService;
import com.streamplatform.streamapi.service.VideoService;

@Service
public class VideoServiceImpl implements VideoService {

    private static final Logger logger = LoggerFactory.getLogger(VideoServiceImpl.class);

    private static final long MAX_FILE_SIZE = 500L * 1024L * 1024L; // 500MB
    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList("mp4", "webm", "mkv", "mov", "avi");

    private final VideoRepository videoRepository;
    private final UserRepository userRepository;
    private final MinioStorageService minioStorageService;
    private final NotificationService notificationService;

    public VideoServiceImpl(
            VideoRepository videoRepository,
            UserRepository userRepository,
            MinioStorageService minioStorageService,
            NotificationService notificationService) {
        this.videoRepository = videoRepository;
        this.userRepository = userRepository;
        this.minioStorageService = minioStorageService;
        this.notificationService = notificationService;
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails)) {
            throw new ApiException("User is not authenticated.", HttpStatus.UNAUTHORIZED);
        }

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        return userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new ApiException("User not found.", HttpStatus.NOT_FOUND));
    }

    private Optional<User> getOptionalCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails)) {
            return Optional.empty();
        }

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        return userRepository.findByUsername(userDetails.getUsername());
    }

    private VideoResponse mapToResponse(Video video) {
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
    @Transactional
    public VideoResponse uploadVideo(
            MultipartFile file,
            String title,
            String description,
            String category,
            boolean isPublic) {

        User user = getCurrentUser();

        if (file == null || file.isEmpty()) {
            throw new ApiException("Video file is required.", HttpStatus.BAD_REQUEST);
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ApiException("Video file size exceeds maximum limit of 500MB.", HttpStatus.BAD_REQUEST);
        }

        String originalFilename = file.getOriginalFilename() != null
                ? StringUtils.cleanPath(file.getOriginalFilename())
                : "video.mp4";

        String extension = "";
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < originalFilename.length() - 1) {
            extension = originalFilename.substring(dotIndex + 1).toLowerCase();
        }

        String contentType = file.getContentType();
        if (contentType == null || (!contentType.startsWith("video/") && !ALLOWED_EXTENSIONS.contains(extension))) {
            throw new ApiException("Invalid file type. Only video files are allowed.", HttpStatus.BAD_REQUEST);
        }

        if (contentType == null || !contentType.startsWith("video/")) {
            contentType = "video/" + (extension.isEmpty() ? "mp4" : extension);
        }

        if (!StringUtils.hasText(title)) {
            title = originalFilename;
        }

        if (!StringUtils.hasText(category)) {
            category = "General";
        }

        // Clean object key naming scheme: videos/{userId}/{uuid}/{originalFilename}
        String uniqueId = UUID.randomUUID().toString();
        String safeName = originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
        String objectKey = String.format("videos/%d/%s/%s", user.getId(), uniqueId, safeName);

        try (InputStream inputStream = file.getInputStream()) {
            minioStorageService.uploadFile(objectKey, inputStream, file.getSize(), contentType);
        } catch (Exception e) {
            logger.error("Error reading upload file stream: {}", e.getMessage());
            throw new ApiException("Failed to process video upload: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }

        Video video = new Video();
        video.setUser(user);
        video.setTitle(title.trim());
        video.setDescription(description != null ? description.trim() : null);
        video.setCategory(category.trim());
        video.setObjectKey(objectKey);
        video.setOriginalFilename(originalFilename);
        video.setContentType(contentType);
        video.setFileSize(file.getSize());
        video.setPublic(isPublic);
        video.setStatus("READY");

        Video savedVideo = videoRepository.save(video);
        logger.info("Video uploaded and registered successfully: id={}, user={}", savedVideo.getId(), user.getUsername());

        try {
            notificationService.notifyVideoReady(savedVideo);
        } catch (Exception e) {
            // Non-blocking notification dispatch
        }

        return mapToResponse(savedVideo);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VideoResponse> getMyVideos() {
        User user = getCurrentUser();
        return videoRepository.findByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<VideoResponse> getPublicVideos(String category, String search) {
        List<Video> videos;

        if (StringUtils.hasText(search)) {
            videos = videoRepository.searchPublicVideos(search.trim());
        } else if (StringUtils.hasText(category) && !"All".equalsIgnoreCase(category.trim())) {
            videos = videoRepository.findByCategoryAndIsPublicTrueOrderByCreatedAtDesc(category.trim());
        } else {
            videos = videoRepository.findByIsPublicTrueOrderByCreatedAtDesc();
        }

        return videos.stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public VideoResponse getVideoById(Long id) {
        Video video = videoRepository.findById(id)
                .orElseThrow(() -> new ApiException("Video not found.", HttpStatus.NOT_FOUND));

        if (!video.isPublic()) {
            Optional<User> currentUser = getOptionalCurrentUser();
            if (currentUser.isEmpty() || !video.getUser().getId().equals(currentUser.get().getId())) {
                throw new ApiException("Access denied.", HttpStatus.FORBIDDEN);
            }
        }

        return mapToResponse(video);
    }

    @Override
    @Transactional
    public VideoResponse updateVideo(Long id, UpdateVideoRequest request) {
        User user = getCurrentUser();

        Video video = videoRepository.findById(id)
                .orElseThrow(() -> new ApiException("Video not found.", HttpStatus.NOT_FOUND));

        if (!video.getUser().getId().equals(user.getId())) {
            throw new ApiException("Access denied.", HttpStatus.FORBIDDEN);
        }

        video.setTitle(request.getTitle().trim());
        video.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);
        video.setCategory(request.getCategory().trim());
        video.setPublic(request.isPublic());

        Video updated = videoRepository.save(video);
        logger.info("Video metadata updated: id={}, user={}", id, user.getUsername());
        return mapToResponse(updated);
    }

    @Override
    @Transactional
    public void deleteVideo(Long id) {
        User user = getCurrentUser();

        Video video = videoRepository.findById(id)
                .orElseThrow(() -> new ApiException("Video not found.", HttpStatus.NOT_FOUND));

        if (!video.getUser().getId().equals(user.getId())) {
            throw new ApiException("Access denied.", HttpStatus.FORBIDDEN);
        }

        // Delete from MinIO
        minioStorageService.deleteObject(video.getObjectKey());

        // Delete from database
        videoRepository.delete(video);
        logger.info("Video deleted from storage and database: id={}, user={}", id, user.getUsername());
    }

    @Override
    public ResponseEntity<StreamingResponseBody> streamVideo(Long id, String rangeHeader) {
        Video video = videoRepository.findById(id)
                .orElseThrow(() -> new ApiException("Video not found.", HttpStatus.NOT_FOUND));

        if (!video.isPublic()) {
            Optional<User> currentUser = getOptionalCurrentUser();
            if (currentUser.isEmpty() || !video.getUser().getId().equals(currentUser.get().getId())) {
                throw new ApiException("Access denied.", HttpStatus.FORBIDDEN);
            }
        }

        long fileSize = video.getFileSize();
        String contentType = video.getContentType() != null ? video.getContentType() : "video/mp4";

        long start = 0;
        long end = fileSize - 1;

        boolean isPartial = false;

        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            String[] ranges = rangeHeader.substring("bytes=".length()).split("-");
            try {
                if (ranges.length > 0 && !ranges[0].isEmpty()) {
                    start = Long.parseLong(ranges[0]);
                }
                if (ranges.length > 1 && !ranges[1].isEmpty()) {
                    end = Long.parseLong(ranges[1]);
                }
                if (end >= fileSize) {
                    end = fileSize - 1;
                }
                if (start <= end) {
                    isPartial = true;
                }
            } catch (NumberFormatException ignored) {
                start = 0;
                end = fileSize - 1;
                isPartial = false;
            }
        }

        long contentLength = end - start + 1;
        long finalStart = start;
        long finalLength = contentLength;
        final boolean finalIsPartial = isPartial;

        if (start == 0) {
            try {
                video.setViews(video.getViews() + 1);
                videoRepository.save(video);
            } catch (Exception e) {
                logger.warn("Failed to increment video views: {}", e.getMessage());
            }
        }

        StreamingResponseBody responseBody = outputStream -> {
            try (InputStream in = finalIsPartial
                    ? minioStorageService.getObjectStream(video.getObjectKey(), finalStart, finalLength)
                    : minioStorageService.getObjectStream(video.getObjectKey())) {
                byte[] buffer = new byte[16384];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                outputStream.flush();
            }
        };

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_TYPE, contentType);
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");

        if (isPartial) {
            headers.set(HttpHeaders.CONTENT_RANGE, String.format("bytes %d-%d/%d", start, end, fileSize));
            headers.setContentLength(contentLength);
            return new ResponseEntity<>(responseBody, headers, HttpStatus.PARTIAL_CONTENT);
        } else {
            headers.setContentLength(fileSize);
            return new ResponseEntity<>(responseBody, headers, HttpStatus.OK);
        }
    }
}
