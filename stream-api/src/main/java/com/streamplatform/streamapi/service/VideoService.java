package com.streamplatform.streamapi.service;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.streamplatform.streamapi.dto.UpdateVideoRequest;
import com.streamplatform.streamapi.dto.VideoResponse;

public interface VideoService {

    VideoResponse uploadVideo(MultipartFile file, String title, String description, String category, boolean isPublic);

    List<VideoResponse> getMyVideos();

    List<VideoResponse> getPublicVideos(String category, String search);

    VideoResponse getVideoById(Long id);

    VideoResponse updateVideo(Long id, UpdateVideoRequest request);

    void deleteVideo(Long id);

    ResponseEntity<StreamingResponseBody> streamVideo(Long id, String rangeHeader);
}
