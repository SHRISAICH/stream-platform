package com.streamplatform.streamapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class UpdateUserStatusRequest {

    @JsonProperty("enabled")
    private Boolean enabled;

    public UpdateUserStatusRequest() {
    }

    public UpdateUserStatusRequest(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
