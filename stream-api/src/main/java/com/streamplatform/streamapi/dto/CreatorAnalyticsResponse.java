package com.streamplatform.streamapi.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreatorAnalyticsResponse {

    private long totalStreams;
    private long liveStreams;
    private long scheduledStreams;
    private long completedStreams;
    private int peakConcurrentViewers;
    private long totalDurationSeconds;
    private long totalVideos;
    private long totalVideoViews;
    private long totalStorageBytes;
    private List<StreamResponse> recentStreams;
    private Map<String, Long> categoryDistribution;

    public CreatorAnalyticsResponse() {
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

    public long getCompletedStreams() {
        return completedStreams;
    }

    public void setCompletedStreams(long completedStreams) {
        this.completedStreams = completedStreams;
    }

    public int getPeakConcurrentViewers() {
        return peakConcurrentViewers;
    }

    public void setPeakConcurrentViewers(int peakConcurrentViewers) {
        this.peakConcurrentViewers = peakConcurrentViewers;
    }

    public long getTotalDurationSeconds() {
        return totalDurationSeconds;
    }

    public void setTotalDurationSeconds(long totalDurationSeconds) {
        this.totalDurationSeconds = totalDurationSeconds;
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

    public List<StreamResponse> getRecentStreams() {
        return recentStreams;
    }

    public void setRecentStreams(List<StreamResponse> recentStreams) {
        this.recentStreams = recentStreams;
    }

    public Map<String, Long> getCategoryDistribution() {
        return categoryDistribution;
    }

    public void setCategoryDistribution(Map<String, Long> categoryDistribution) {
        this.categoryDistribution = categoryDistribution;
    }
}
