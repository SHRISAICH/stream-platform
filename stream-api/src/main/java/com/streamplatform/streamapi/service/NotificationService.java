package com.streamplatform.streamapi.service;

import java.util.List;

import com.streamplatform.streamapi.dto.NotificationResponse;
import com.streamplatform.streamapi.dto.UnreadCountResponse;
import com.streamplatform.streamapi.entity.Notification;
import com.streamplatform.streamapi.entity.Stream;
import com.streamplatform.streamapi.entity.User;
import com.streamplatform.streamapi.entity.Video;

public interface NotificationService {

    List<NotificationResponse> getMyNotifications();

    UnreadCountResponse getUnreadCount();

    NotificationResponse markAsRead(Long id);

    int markAllAsRead();

    void deleteNotification(Long id);

    Notification createNotification(User recipient, String type, String title, String message, Long relatedStreamId);

    void notifyStreamLive(Stream stream);

    void notifyStreamEnded(Stream stream);

    void notifyStreamCreated(Stream stream);

    void notifyVideoReady(Video video);
}
