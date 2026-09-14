package com.streamplatform.streamapi.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.streamplatform.streamapi.entity.Stream;
import com.streamplatform.streamapi.entity.User;

@Repository
public interface StreamRepository extends JpaRepository<Stream, Long> {

    @Query("SELECT s FROM Stream s JOIN FETCH s.user WHERE s.user = :user ORDER BY s.createdAt DESC")
    List<Stream> findByUser(@Param("user") User user);

    @Query("SELECT s FROM Stream s JOIN FETCH s.user WHERE s.isPublic = true AND s.status != 'CANCELLED' ORDER BY s.createdAt DESC")
    List<Stream> findPublicStreams();

    @Query("SELECT s FROM Stream s JOIN FETCH s.user WHERE s.isPublic = true AND s.status = 'SCHEDULED' ORDER BY s.scheduledStartTime ASC")
    List<Stream> findPublicScheduledStreams();

    List<Stream> findByUserAndStatus(User user, String status);

    long countByUser(User user);

    long countByUserAndStatus(User user, String status);

    @Query("SELECT COALESCE(MAX(s.peakViewers), 0) FROM Stream s WHERE s.user = :user")
    Integer maxPeakViewersByUser(@org.springframework.data.repository.query.Param("user") User user);

    @Query("SELECT COALESCE(SUM(s.durationSeconds), 0) FROM Stream s WHERE s.user = :user")
    Long sumDurationByUser(@org.springframework.data.repository.query.Param("user") User user);

    long countByStatus(String status);

    @Query("SELECT COALESCE(MAX(s.peakViewers), 0) FROM Stream s")
    Integer maxPlatformPeakViewers();

    @Query("SELECT COALESCE(SUM(s.durationSeconds), 0) FROM Stream s")
    Long sumPlatformDuration();

    @Query("SELECT s FROM Stream s JOIN FETCH s.user ORDER BY s.createdAt DESC")
    List<Stream> findAllByOrderByCreatedAtDesc();

    boolean existsByStreamKey(String streamKey);

    Optional<Stream> findByStreamKey(String streamKey);
}