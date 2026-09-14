package com.streamplatform.streamapi.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.streamplatform.streamapi.entity.ChatMessage;
import com.streamplatform.streamapi.entity.Stream;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    @Query("SELECT m FROM ChatMessage m JOIN FETCH m.user WHERE m.stream = :stream ORDER BY m.createdAt DESC")
    List<ChatMessage> findRecentMessagesByStream(@Param("stream") Stream stream, Pageable pageable);

    void deleteByStream(Stream stream);
}
