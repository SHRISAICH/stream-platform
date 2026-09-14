package com.streamplatform.streamapi.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.streamplatform.streamapi.dto.ChatEvent;
import com.streamplatform.streamapi.dto.ChatMessageResponse;
import com.streamplatform.streamapi.dto.CreateChatMessageRequest;
import com.streamplatform.streamapi.entity.ChatMessage;
import com.streamplatform.streamapi.entity.Stream;
import com.streamplatform.streamapi.entity.User;
import com.streamplatform.streamapi.exception.ApiException;
import com.streamplatform.streamapi.repository.ChatMessageRepository;
import com.streamplatform.streamapi.repository.StreamRepository;
import com.streamplatform.streamapi.repository.UserRepository;
import com.streamplatform.streamapi.security.CustomUserDetails;
import com.streamplatform.streamapi.service.ChatService;

@Service
public class ChatServiceImpl implements ChatService {

    private static final Logger logger = LoggerFactory.getLogger(ChatServiceImpl.class);

    private final ChatMessageRepository chatMessageRepository;
    private final StreamRepository streamRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatServiceImpl(
            ChatMessageRepository chatMessageRepository,
            StreamRepository streamRepository,
            UserRepository userRepository,
            SimpMessagingTemplate messagingTemplate) {
        this.chatMessageRepository = chatMessageRepository;
        this.streamRepository = streamRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails)) {
            throw new ApiException("User is not authenticated.", HttpStatus.UNAUTHORIZED);
        }

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        return userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new ApiException("User not found.", HttpStatus.NOT_FOUND));
    }

    private Optional<User> getOptionalCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails)) {
            return Optional.empty();
        }

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        return userRepository.findByUsername(userDetails.getUsername());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getRecentMessages(Long streamId) {
        Stream stream = streamRepository.findById(streamId)
                .orElseThrow(() -> new ApiException("Stream not found.", HttpStatus.NOT_FOUND));

        if (!stream.isPublic()) {
            Optional<User> currentUser = getOptionalCurrentUser();
            if (currentUser.isEmpty() || !stream.getUser().getId().equals(currentUser.get().getId())) {
                throw new ApiException("Access denied.", HttpStatus.FORBIDDEN);
            }
        }

        List<ChatMessage> recent = chatMessageRepository.findRecentMessagesByStream(
                stream, PageRequest.of(0, 50));

        // Create a mutable copy to reverse to chronological order (oldest first)
        List<ChatMessage> chronological = new ArrayList<>(recent);
        Collections.reverse(chronological);

        return chronological.stream()
                .map(ChatMessageResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional
    public ChatMessageResponse sendMessage(Long streamId, CreateChatMessageRequest request) {
        User user = getCurrentUser();

        Stream stream = streamRepository.findById(streamId)
                .orElseThrow(() -> new ApiException("Stream not found.", HttpStatus.NOT_FOUND));

        if (!stream.isPublic() && !stream.getUser().getId().equals(user.getId())) {
            throw new ApiException("Access denied.", HttpStatus.FORBIDDEN);
        }

        String content = request.getContent() != null ? request.getContent().trim() : "";
        if (content.isEmpty()) {
            throw new ApiException("Message content cannot be blank.", HttpStatus.BAD_REQUEST);
        }
        if (content.length() > 500) {
            throw new ApiException("Message content exceeds maximum limit of 500 characters.", HttpStatus.BAD_REQUEST);
        }

        ChatMessage message = new ChatMessage(stream, user, content);
        ChatMessage savedMessage = chatMessageRepository.save(message);

        ChatMessageResponse response = ChatMessageResponse.fromEntity(savedMessage);

        try {
            ChatEvent event = ChatEvent.messageEvent(streamId, response);
            messagingTemplate.convertAndSend("/topic/streams/" + streamId + "/chat", event);
            logger.debug("Broadcasted chat message {} to stream {}", savedMessage.getId(), streamId);
        } catch (Exception e) {
            logger.warn("Failed to broadcast chat message via WebSocket: {}", e.getMessage());
        }

        return response;
    }

    @Override
    @Transactional
    public void deleteMessage(Long messageId) {
        User user = getCurrentUser();

        ChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new ApiException("Message not found.", HttpStatus.NOT_FOUND));

        boolean isAuthor = message.getUser().getId().equals(user.getId());
        boolean isStreamOwner = message.getStream().getUser().getId().equals(user.getId());
        boolean isAdmin = user.getRole() != null && user.getRole().toUpperCase().contains("ADMIN");

        if (!isAuthor && !isStreamOwner && !isAdmin) {
            throw new ApiException("Access denied. Only the author, stream owner, or admin can delete this message.", HttpStatus.FORBIDDEN);
        }

        Long streamId = message.getStream().getId();
        chatMessageRepository.delete(message);
        logger.info("Deleted chat message id={} by user={}", messageId, user.getUsername());

        try {
            ChatEvent event = ChatEvent.deleteEvent(streamId, messageId);
            messagingTemplate.convertAndSend("/topic/streams/" + streamId + "/chat", event);
            logger.debug("Broadcasted delete event for message {} to stream {}", messageId, streamId);
        } catch (Exception e) {
            logger.warn("Failed to broadcast chat delete event via WebSocket: {}", e.getMessage());
        }
    }
}
