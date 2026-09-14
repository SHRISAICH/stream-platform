package com.streamplatform.streamapi.service;

import java.util.List;

import com.streamplatform.streamapi.dto.CreateStreamRequest;
import com.streamplatform.streamapi.dto.StreamResponse;

public interface StreamService {

    StreamResponse createStream(CreateStreamRequest request);

    List<StreamResponse> getMyStreams();

    List<StreamResponse> getPublicStreams();

    StreamResponse getStreamById(Long id);

    StreamResponse updateStream(Long id, CreateStreamRequest request);

    void deleteStream(Long id);

    StreamResponse scheduleStream(com.streamplatform.streamapi.dto.ScheduleStreamRequest request);

    List<StreamResponse> getPublicScheduledStreams();

    StreamResponse cancelScheduledStream(Long id);

    boolean validateStreamKey(String streamKey);

    boolean publishStream(String app, String streamKey);

    boolean unpublishStream(String app, String streamKey);
}