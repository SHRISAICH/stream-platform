package com.streamplatform.streamapi.dto;

public class UnreadCountResponse {

    private long count;
    private long unreadCount;

    public UnreadCountResponse() {
    }

    public UnreadCountResponse(long count) {
        this.count = count;
        this.unreadCount = count;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
        this.unreadCount = count;
    }

    public long getUnreadCount() {
        return unreadCount;
    }

    public void setUnreadCount(long unreadCount) {
        this.unreadCount = unreadCount;
        this.count = unreadCount;
    }
}
