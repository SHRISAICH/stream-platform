package com.streamplatform.streamapi.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdminStatsResponse {

    private long totalUsers;
    private long activeUsers;
    private long totalStreams;
    private long liveStreams;
    private long scheduledStreams;
    private long endedStreams;
    private long totalVideos;
    private long totalVideoViews;
    private long totalStorageBytes;
    private int maxConcurrentViewers;
    private long totalStreamDurationSeconds;

    public AdminStatsResponse() {
    }

    public long getTotalUsers() {
        return totalUsers;
    }

    public void setTotalUsers(long totalUsers) {
        this.totalUsers = totalUsers;
    }

    public long getActiveUsers() {
        return activeUsers;
    }

    public void setActiveUsers(long activeUsers) {
        this.activeUsers = activeUsers;
    }

    public long getTotalStreams() {
        return totalStreams;
    }

    public void setTotalStreams(long totalStreams) {
        this.totalStreams = totalStreams;
    }

    public long getLiveStreams() {
        return liveStreams;
    }

    public void setLiveStreams(long liveStreams) {
        this.liveStreams = liveStreams;
    }

    public long getScheduledStreams() {
        return scheduledStreams;
    }

    public void setScheduledStreams(long scheduledStreams) {
        this.scheduledStreams = scheduledStreams;
    }

    public long getEndedStreams() {
        return endedStreams;
    }

    public void setEndedStreams(long endedStreams) {
        this.endedStreams = endedStreams;
    }

    public long getTotalVideos() {
        return totalVideos;
    }

    public void setTotalVideos(long totalVideos) {
        this.totalVideos = totalVideos;
    }

    public long getTotalVideoViews() {
        return totalVideoViews;
    }

    public void setTotalVideoViews(long totalVideoViews) {
        this.totalVideoViews = totalVideoViews;
    }

    public long getTotalStorageBytes() {
        return totalStorageBytes;
    }

    public void setTotalStorageBytes(long totalStorageBytes) {
        this.totalStorageBytes = totalStorageBytes;
    }

    public int getMaxConcurrentViewers() {
        return maxConcurrentViewers;
    }

    public void setMaxConcurrentViewers(int maxConcurrentViewers) {
        this.maxConcurrentViewers = maxConcurrentViewers;
    }

    public long getTotalStreamDurationSeconds() {
        return totalStreamDurationSeconds;
    }

    public void setTotalStreamDurationSeconds(long totalStreamDurationSeconds) {
        this.totalStreamDurationSeconds = totalStreamDurationSeconds;
    }
}
