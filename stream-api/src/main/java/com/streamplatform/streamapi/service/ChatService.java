package com.streamplatform.streamapi.service;

import java.util.List;

import com.streamplatform.streamapi.dto.ChatMessageResponse;
import com.streamplatform.streamapi.dto.CreateChatMessageRequest;

public interface ChatService {

    List<ChatMessageResponse> getRecentMessages(Long streamId);

    ChatMessageResponse sendMessage(Long streamId, CreateChatMessageRequest request);

    void deleteMessage(Long messageId);
}
