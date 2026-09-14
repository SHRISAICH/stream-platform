package com.streamplatform.streamapi.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.streamplatform.streamapi.entity.User;
import com.streamplatform.streamapi.entity.Video;

@Repository
public interface VideoRepository extends JpaRepository<Video, Long> {

    @Query("SELECT v FROM Video v JOIN FETCH v.user WHERE v.user = :user ORDER BY v.createdAt DESC")
    List<Video> findByUserOrderByCreatedAtDesc(@Param("user") User user);

    @Query("SELECT v FROM Video v JOIN FETCH v.user WHERE v.isPublic = true ORDER BY v.createdAt DESC")
    List<Video> findByIsPublicTrueOrderByCreatedAtDesc();

    @Query("SELECT v FROM Video v JOIN FETCH v.user WHERE v.category = :category AND v.isPublic = true ORDER BY v.createdAt DESC")
    List<Video> findByCategoryAndIsPublicTrueOrderByCreatedAtDesc(@Param("category") String category);

    Optional<Video> findByObjectKey(String objectKey);

    @Query("SELECT v FROM Video v JOIN FETCH v.user WHERE v.isPublic = true AND " +
           "(LOWER(v.title) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(v.description) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(v.category) LIKE LOWER(CONCAT('%', :query, '%'))) " +
           "ORDER BY v.createdAt DESC")
    List<Video> searchPublicVideos(@Param("query") String query);

    long countByUser(User user);

    @Query("SELECT COALESCE(SUM(v.views), 0) FROM Video v WHERE v.user = :user")
    Long sumViewsByUser(@Param("user") User user);

    @Query("SELECT COALESCE(SUM(v.fileSize), 0) FROM Video v WHERE v.user = :user")
    Long sumFileSizeByUser(@Param("user") User user);

    @Query("SELECT COALESCE(SUM(v.views), 0) FROM Video v")
    Long sumTotalViews();

    @Query("SELECT COALESCE(SUM(v.fileSize), 0) FROM Video v")
    Long sumTotalFileSize();

    @Query("SELECT v FROM Video v JOIN FETCH v.user ORDER BY v.createdAt DESC")
    List<Video> findAllByOrderByCreatedAtDesc();
}
