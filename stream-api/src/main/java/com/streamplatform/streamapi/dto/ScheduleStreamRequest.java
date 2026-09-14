package com.streamplatform.streamapi.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class ScheduleStreamRequest {

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    @NotBlank(message = "Category is required")
    private String category;

    @com.fasterxml.jackson.annotation.JsonProperty("isPublic")
    private Boolean isPublic = true;

    @NotNull(message = "Scheduled start time is required")
    private LocalDateTime scheduledStartTime;

    private LocalDateTime scheduledEndTime;

    public ScheduleStreamRequest() {
    }

    public ScheduleStreamRequest(String title, String description, String category, Boolean isPublic, LocalDateTime scheduledStartTime, LocalDateTime scheduledEndTime) {
        this.title = title;
        this.description = description;
        this.category = category;
        this.isPublic = isPublic != null ? isPublic : true;
        this.scheduledStartTime = scheduledStartTime;
        this.scheduledEndTime = scheduledEndTime;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public boolean isPublic() {
        return isPublic != null && isPublic;
    }

    public void setPublic(Boolean aPublic) {
        if (aPublic != null) {
            isPublic = aPublic;
        }
    }

    @com.fasterxml.jackson.annotation.JsonSetter("public")
    public void setPublicAlias(Boolean aPublic) {
        if (aPublic != null) {
            isPublic = aPublic;
        }
    }

    public LocalDateTime getScheduledStartTime() {
        return scheduledStartTime;
    }

    public void setScheduledStartTime(LocalDateTime scheduledStartTime) {
        this.scheduledStartTime = scheduledStartTime;
    }

    public LocalDateTime getScheduledEndTime() {
        return scheduledEndTime;
    }

    public void setScheduledEndTime(LocalDateTime scheduledEndTime) {
        this.scheduledEndTime = scheduledEndTime;
    }
}
