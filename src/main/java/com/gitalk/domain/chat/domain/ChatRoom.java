package com.gitalk.chat.domain;

import java.util.HashSet;
import java.util.Set;

public class ChatRoom {

    private final String id;
    private final String name;
    private final Set<String> memberNicknames = new HashSet<>();

    public ChatRoom(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId()   { return id; }
    public String getName() { return name; }
}
