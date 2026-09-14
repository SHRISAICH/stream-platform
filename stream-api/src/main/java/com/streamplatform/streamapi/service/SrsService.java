package com.streamplatform.streamapi.service;

import java.util.Map;

public interface SrsService {

    boolean isStreamLive(String streamKey);

    int getViewerCount(String streamKey);

    Map<String, Integer> getViewerCounts();

    void registerPlaySession(String streamKey, String clientId);

    void unregisterPlaySession(String streamKey, String clientId);

    void clearPlaySessions(String streamKey);
}

