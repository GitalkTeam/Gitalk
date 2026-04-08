package com.gitalk.chat.domain;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Message {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final String sender;
    private final String content;
    private final String roomId;
    private final LocalDateTime sentAt;

    public Message(String sender, String content, String roomId) {
        this.sender = sender;
        this.content = content;
        this.roomId = roomId;
        this.sentAt = LocalDateTime.now();
    }

    public String getSender()         { return sender; }
    public String getContent()        { return content; }
    public String getRoomId()         { return roomId; }
    public LocalDateTime getSentAt()  { return sentAt; }
    public String getFormattedTime()  { return sentAt.format(FORMATTER); }
}
