package com.streamplatform.streamapi.dto;

import java.time.LocalDateTime;

import com.streamplatform.streamapi.entity.ChatMessage;

public class ChatMessageResponse {

    private Long id;
    private Long streamId;
    private Long userId;
    private String username;
    private String userFullName;
    private String content;
    private LocalDateTime createdAt;

    public ChatMessageResponse() {
    }

    public static ChatMessageResponse fromEntity(ChatMessage message) {
        ChatMessageResponse response = new ChatMessageResponse();
        response.setId(message.getId());
        if (message.getStream() != null) {
            response.setStreamId(message.getStream().getId());
        }
        if (message.getUser() != null) {
            response.setUserId(message.getUser().getId());
            response.setUsername(message.getUser().getUsername());
            response.setUserFullName(message.getUser().getFullName());
        }
        response.setContent(message.getContent());
        response.setCreatedAt(message.getCreatedAt());
        return response;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getStreamId() {
        return streamId;
    }

    public void setStreamId(Long streamId) {
        this.streamId = streamId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getUserFullName() {
        return userFullName;
    }

    public void setUserFullName(String userFullName) {
        this.userFullName = userFullName;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
