package com.streamplatform.streamapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateChatMessageRequest {

    @NotBlank(message = "Message content is required.")
    @Size(min = 1, max = 500, message = "Message content must be between 1 and 500 characters.")
    private String content;

    public CreateChatMessageRequest() {
    }

    public CreateChatMessageRequest(String content) {
        this.content = content;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
