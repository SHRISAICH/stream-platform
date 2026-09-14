package com.streamplatform.streamapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for SRS v5 HTTP callback payloads.
 *
 * SRS sends a POST with JSON body containing fields like:
 *   action, client_id, ip, vhost, app, stream, param, etc.
 *
 * We only bind the fields we need; the rest are ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SrsCallbackRequest {

    private String action;

    @JsonProperty("client_id")
    private String clientId;

    private String ip;

    private String vhost;

    private String app;

    /**
     * The stream name/key used in the RTMP publish URL.
     * e.g. if the streamer publishes to rtmp://host/live/abc123,
     * then stream = "abc123".
     */
    private String stream;

    private String param;

    public SrsCallbackRequest() {
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getVhost() {
        return vhost;
    }

    public void setVhost(String vhost) {
        this.vhost = vhost;
    }

    public String getApp() {
        return app;
    }

    public void setApp(String app) {
        this.app = app;
    }

    public String getStream() {
        return stream;
    }

    public void setStream(String stream) {
        this.stream = stream;
    }

    public String getParam() {
        return param;
    }

    public void setParam(String param) {
        this.param = param;
    }
}
