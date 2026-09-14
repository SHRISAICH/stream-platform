package com.streamplatform.streamapi.service;

import java.util.List;

import com.streamplatform.streamapi.dto.AdminStatsResponse;
import com.streamplatform.streamapi.dto.StreamResponse;
import com.streamplatform.streamapi.dto.UserResponse;
import com.streamplatform.streamapi.dto.VideoResponse;

public interface AdminService {

    AdminStatsResponse getPlatformStats();

    List<UserResponse> getAllUsers();

    UserResponse updateUserStatus(Long userId, boolean enabled);

    UserResponse updateUserRole(Long userId, String role);

    List<StreamResponse> getAllStreams();

    void deleteStreamAsAdmin(Long streamId);

    List<VideoResponse> getAllVideos();

    void deleteVideoAsAdmin(Long videoId);
}
