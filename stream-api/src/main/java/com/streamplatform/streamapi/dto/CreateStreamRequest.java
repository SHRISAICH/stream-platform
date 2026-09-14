package com.streamplatform.streamapi.dto;

import jakarta.validation.constraints.NotBlank;

public class CreateStreamRequest {

    @NotBlank
    private String title;

    private String description;

    @NotBlank
    private String category;

    private boolean isPublic = true;

    public CreateStreamRequest() {
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
        return isPublic;
    }

    public void setPublic(boolean aPublic) {
        isPublic = aPublic;
    }
}