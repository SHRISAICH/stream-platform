package com.streamplatform.streamapi.dto;

public class ChatEvent {

    public static final String TYPE_MESSAGE = "MESSAGE";
    public static final String TYPE_DELETE = "DELETE";

    private String type;
    private Long streamId;
    private Long messageId;
    private ChatMessageResponse message;

    public ChatEvent() {
    }

    public static ChatEvent messageEvent(Long streamId, ChatMessageResponse message) {
        ChatEvent event = new ChatEvent();
        event.setType(TYPE_MESSAGE);
        event.setStreamId(streamId);
        event.setMessageId(message.getId());
        event.setMessage(message);
        return event;
    }

    public static ChatEvent deleteEvent(Long streamId, Long messageId) {
        ChatEvent event = new ChatEvent();
        event.setType(TYPE_DELETE);
        event.setStreamId(streamId);
        event.setMessageId(messageId);
        return event;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Long getStreamId() {
        return streamId;
    }

    public void setStreamId(Long streamId) {
        this.streamId = streamId;
    }

    public Long getMessageId() {
        return messageId;
    }

    public void setMessageId(Long messageId) {
        this.messageId = messageId;
    }

    public ChatMessageResponse getMessage() {
        return message;
    }

    public void setMessage(ChatMessageResponse message) {
        this.message = message;
    }
}
