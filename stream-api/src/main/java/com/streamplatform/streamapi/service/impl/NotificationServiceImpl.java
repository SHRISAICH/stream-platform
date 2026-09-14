package com.streamplatform.streamapi.service.impl;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.streamplatform.streamapi.dto.NotificationResponse;
import com.streamplatform.streamapi.dto.UnreadCountResponse;
import com.streamplatform.streamapi.entity.Notification;
import com.streamplatform.streamapi.entity.Stream;
import com.streamplatform.streamapi.entity.User;
import com.streamplatform.streamapi.entity.Video;
import com.streamplatform.streamapi.exception.ApiException;
import com.streamplatform.streamapi.repository.NotificationRepository;
import com.streamplatform.streamapi.repository.UserRepository;
import com.streamplatform.streamapi.security.CustomUserDetails;
import com.streamplatform.streamapi.service.NotificationService;

@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationServiceImpl.class);

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public NotificationServiceImpl(
            NotificationRepository notificationRepository,
            UserRepository userRepository,
            SimpMessagingTemplate messagingTemplate) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
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

    @Override
    @Transactional(readOnly = true)
    public List<NotificationResponse> getMyNotifications() {
        User user = getCurrentUser();
        List<Notification> notifications = notificationRepository.findByUserOrderByCreatedAtDesc(user);
        return notifications.stream()
                .map(NotificationResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public UnreadCountResponse getUnreadCount() {
        User user = getCurrentUser();
        long count = notificationRepository.countUnreadByUser(user);
        return new UnreadCountResponse(count);
    }

    @Override
    @Transactional
    public NotificationResponse markAsRead(Long id) {
        User user = getCurrentUser();

        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ApiException("Notification not found.", HttpStatus.NOT_FOUND));

        if (!notification.getRecipient().getId().equals(user.getId())) {
            throw new ApiException("Access denied. You can only update your own notifications.", HttpStatus.FORBIDDEN);
        }

        if (!notification.isRead()) {
            notification.setRead(true);
            notification = notificationRepository.save(notification);
        }

        return NotificationResponse.fromEntity(notification);
    }

    @Override
    @Transactional
    public int markAllAsRead() {
        User user = getCurrentUser();
        return notificationRepository.markAllAsReadByUser(user);
    }

    @Override
    @Transactional
    public void deleteNotification(Long id) {
        User user = getCurrentUser();

        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ApiException("Notification not found.", HttpStatus.NOT_FOUND));

        if (!notification.getRecipient().getId().equals(user.getId())) {
            throw new ApiException("Access denied. You can only delete your own notifications.", HttpStatus.FORBIDDEN);
        }

        notificationRepository.delete(notification);
        logger.info("Deleted notification id={} for user={}", id, user.getUsername());
    }

    @Override
    @Transactional
    public Notification createNotification(User recipient, String type, String title, String message, Long relatedStreamId) {
        Notification notification = new Notification(recipient, type, title, message, relatedStreamId);
        Notification saved = notificationRepository.save(notification);

        NotificationResponse response = NotificationResponse.fromEntity(saved);

        try {
            messagingTemplate.convertAndSend("/topic/users/" + recipient.getId() + "/notifications", response);
            logger.debug("Broadcasted notification {} to user {}", saved.getId(), recipient.getId());
        } catch (Exception e) {
            logger.warn("Failed to broadcast notification via WebSocket to user {}: {}", recipient.getId(), e.getMessage());
        }

        return saved;
    }

    @Override
    @Transactional
    public void notifyStreamLive(Stream stream) {
        if (stream == null || stream.getUser() == null) {
            return;
        }

        User owner = stream.getUser();
        if (owner.getId() != null) {
            owner = userRepository.findById(owner.getId()).orElse(owner);
        }

        // 1. Notify stream owner
        createNotification(
                owner,
                "STREAM_LIVE",
                "Stream is Live",
                "Your stream \"" + stream.getTitle() + "\" is now broadcasting live.",
                stream.getId()
        );

        // 2. Notify other registered users if stream is public
        if (stream.isPublic()) {
            List<User> allUsers = userRepository.findAll();
            for (User u : allUsers) {
                if (u.isEnabled() && !u.getId().equals(owner.getId())) {
                    createNotification(
                            u,
                            "STREAM_LIVE",
                            "Live Stream Started",
                            owner.getUsername() + " is now live: \"" + stream.getTitle() + "\"",
                            stream.getId()
                    );
                }
            }
        }
    }

    @Override
    @Transactional
    public void notifyStreamEnded(Stream stream) {
        if (stream == null || stream.getUser() == null) {
            return;
        }

        User owner = stream.getUser();
        if (owner.getId() != null) {
            owner = userRepository.findById(owner.getId()).orElse(owner);
        }

        createNotification(
                owner,
                "STREAM_ENDED",
                "Stream Ended",
                "Your stream \"" + stream.getTitle() + "\" has ended.",
                stream.getId()
        );
    }

    @Override
    @Transactional
    public void notifyStreamCreated(Stream stream) {
        if (stream == null || stream.getUser() == null) {
            return;
        }

        User owner = stream.getUser();
        if (owner.getId() != null) {
            owner = userRepository.findById(owner.getId()).orElse(owner);
        }

        createNotification(
                owner,
                "STREAM_CREATED",
                "Stream Created",
                "Your stream \"" + stream.getTitle() + "\" has been created.",
                stream.getId()
        );
    }

    @Override
    @Transactional
    public void notifyVideoReady(Video video) {
        if (video == null || video.getUser() == null) {
            return;
        }

        User owner = video.getUser();
        if (owner.getId() != null) {
            owner = userRepository.findById(owner.getId()).orElse(owner);
        }

        createNotification(
                owner,
                "VIDEO_READY",
                "Video Uploaded",
                "Your video \"" + video.getTitle() + "\" has been processed and is ready to watch.",
                null
        );
    }
}
