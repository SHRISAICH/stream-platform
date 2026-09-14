package com.streamplatform.streamapi.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.streamplatform.streamapi.dto.CreatorAnalyticsResponse;
import com.streamplatform.streamapi.dto.StreamResponse;
import com.streamplatform.streamapi.entity.Stream;
import com.streamplatform.streamapi.entity.User;
import com.streamplatform.streamapi.entity.Video;
import com.streamplatform.streamapi.exception.ApiException;
import com.streamplatform.streamapi.repository.StreamRepository;
import com.streamplatform.streamapi.repository.UserRepository;
import com.streamplatform.streamapi.repository.VideoRepository;
import com.streamplatform.streamapi.security.CustomUserDetails;
import com.streamplatform.streamapi.service.AnalyticsService;
import com.streamplatform.streamapi.service.StreamService;

@Service
public class AnalyticsServiceImpl implements AnalyticsService {

    private final UserRepository userRepository;
    private final StreamRepository streamRepository;
    private final VideoRepository videoRepository;
    private final StreamService streamService;

    public AnalyticsServiceImpl(
            UserRepository userRepository,
            StreamRepository streamRepository,
            VideoRepository videoRepository,
            StreamService streamService) {
        this.userRepository = userRepository;
        this.streamRepository = streamRepository;
        this.videoRepository = videoRepository;
        this.streamService = streamService;
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails)) {
            throw new ApiException("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        return userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new ApiException("User not found", HttpStatus.NOT_FOUND));
    }

    @Override
    @Transactional(readOnly = true)
    public CreatorAnalyticsResponse getCreatorAnalytics() {
        User user = getCurrentUser();

        CreatorAnalyticsResponse response = new CreatorAnalyticsResponse();

        long totalStreams = streamRepository.countByUser(user);
        long liveStreams = streamRepository.countByUserAndStatus(user, "LIVE");
        long scheduledStreams = streamRepository.countByUserAndStatus(user, "SCHEDULED");
        long completedStreams = streamRepository.countByUserAndStatus(user, "ENDED");

        Integer peakViewers = streamRepository.maxPeakViewersByUser(user);
        Long totalDuration = streamRepository.sumDurationByUser(user);

        long totalVideos = videoRepository.countByUser(user);
        Long totalVideoViews = videoRepository.sumViewsByUser(user);
        Long totalStorage = videoRepository.sumFileSizeByUser(user);

        response.setTotalStreams(totalStreams);
        response.setLiveStreams(liveStreams);
        response.setScheduledStreams(scheduledStreams);
        response.setCompletedStreams(completedStreams);
        response.setPeakConcurrentViewers(peakViewers != null ? peakViewers : 0);
        response.setTotalDurationSeconds(totalDuration != null ? totalDuration : 0L);
        response.setTotalVideos(totalVideos);
        response.setTotalVideoViews(totalVideoViews != null ? totalVideoViews : 0L);
        response.setTotalStorageBytes(totalStorage != null ? totalStorage : 0L);

        List<StreamResponse> myStreams = streamService.getMyStreams();
        response.setRecentStreams(myStreams.stream().limit(10).toList());

        Map<String, Long> categoryCount = new HashMap<>();
        List<Stream> streams = streamRepository.findByUser(user);
        for (Stream s : streams) {
            if (s.getCategory() != null && !s.getCategory().isBlank()) {
                categoryCount.put(s.getCategory(), categoryCount.getOrDefault(s.getCategory(), 0L) + 1);
            }
        }
        List<Video> videos = videoRepository.findByUserOrderByCreatedAtDesc(user);
        for (Video v : videos) {
            if (v.getCategory() != null && !v.getCategory().isBlank()) {
                categoryCount.put(v.getCategory(), categoryCount.getOrDefault(v.getCategory(), 0L) + 1);
            }
        }
        response.setCategoryDistribution(categoryCount);

        return response;
    }
}
