package com.gitalk.domain.chatbot.service;

import com.gitalk.common.api.GithubWebhookServer;
import com.gitalk.domain.chatbot.model.WebhookEvent;

import java.util.List;
import java.util.function.Consumer;

public class WebhookService {

    private final GithubWebhookServer webhookServer = new GithubWebhookServer();

    public boolean isRunning() {
        return webhookServer.isRunning();
    }

    public void start(Consumer<WebhookEvent> eventListener) throws Exception {
        webhookServer.setEventListener(eventListener);
        webhookServer.start();
    }

    public void stop() {
        webhookServer.stop();
    }

    public List<WebhookEvent> getEvents() {
        return webhookServer.getReceivedEvents();
    }
}
