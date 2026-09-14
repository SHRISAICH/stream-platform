package com.streamplatform.streamapi.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.streamplatform.streamapi.dto.ChatMessageResponse;
import com.streamplatform.streamapi.dto.CreateChatMessageRequest;
import com.streamplatform.streamapi.service.ChatService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/streams/{streamId}/chat")
    public ResponseEntity<List<ChatMessageResponse>> getRecentMessages(
            @PathVariable Long streamId) {
        return ResponseEntity.ok(chatService.getRecentMessages(streamId));
    }

    @PostMapping("/streams/{streamId}/chat")
    public ResponseEntity<ChatMessageResponse> sendMessage(
            @PathVariable Long streamId,
            @Valid @RequestBody CreateChatMessageRequest request) {
        ChatMessageResponse response = chatService.sendMessage(streamId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping({"/chat/{messageId}", "/streams/{streamId}/chat/{messageId}"})
    public ResponseEntity<Void> deleteMessage(
            @PathVariable(value = "messageId") Long messageId) {
        chatService.deleteMessage(messageId);
        return ResponseEntity.noContent().build();
    }
}
