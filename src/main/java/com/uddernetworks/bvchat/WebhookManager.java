package com.uddernetworks.bvchat;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Emote;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.Webhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

public class WebhookManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebhookManager.class);
    private static final Color SHREK_GREEN = new Color(6, 131, 0);

    private Map<String, User> userAvatars = new HashMap<>();

    private JDA jda;
    private BVChat bvChat;

    private TextChannel channel;
    private List<Webhook> webhooks;
    private List<Emote> emotes;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public WebhookManager(BVChat bvChat, TextChannel textChannel) {
        this.bvChat = bvChat;
        this.jda = bvChat.getJda();
        this.channel = textChannel;
        this.webhooks = channel.retrieveWebhooks().complete();
        this.emotes = channel.getGuild().getEmotes();
    }

    public void sendPromptNotice(String prompt) {
        channel.sendMessage(new EmbedBuilder()
                .setTitle("The following messages are on the prompt:")
                .appendDescription(prompt)
                .setColor(SHREK_GREEN)
                .build())
                .complete();
    }

    public void sendPromptEndNotice(String prompt) {
        channel.sendMessage(new EmbedBuilder()
                .setTitle("The conversation has concluded for the prompt:")
                .appendDescription(prompt)
                .setColor(SHREK_GREEN)
                .build())
                .complete();
    }

    public CompletableFuture<Void> sendFromNNBatch(String prompt, Path path) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return Files.readString(path);
            } catch (IOException e) {
                LOGGER.error("Error reading file " + path, e);
                return "";
            } finally {
                bvChat.stopTyping();
            }
        }).thenAccept(input -> sendFromNNBatch(prompt, input).join());
    }

    public CompletableFuture<Void> sendFromNNBatch(String prompt, String input) {
        return CompletableFuture.runAsync(() -> {
            if (prompt != null) sendPromptNotice(prompt);
            Arrays.stream(input.split("\n")).forEach(line -> {
                var userMessage = line.split(" > ", 2);
                if (userMessage.length != 2) return;
                var message = userMessage[1].trim();

                try {
                    sendMessage(userMessage[0].trim(), message);
                } catch (Exception e) {
                    LOGGER.error("Bad input: {}", line);
                    LOGGER.error("Error sending message from " + userMessage[0].trim(), e);
                }

                double length = message.split("\\s+").length;
                if (length <= 3) {
                    Thread.sleep(random(1000, 2000));
                } else {
                    Thread.sleep((long) Math.min(random(500, 2500) + (length * 100), 5000));
                }
            });
            if (prompt != null) sendPromptEndNotice(prompt);
        });
    }

    private long random(int origin, int bound) {
        return ThreadLocalRandom.current().nextLong(origin, bound);
    }

    public void sendMessage(String user, String message) throws Exception {
        var nameWithoutNumber = user.substring(0, Math.max(0, user.lastIndexOf('#')));
        if (nameWithoutNumber.isBlank()) {
            user = "Unknown" + user;
            nameWithoutNumber = "Unknown";
        }

        var webhook = webhooks.get(0);

        var customAvatar = "";
        var username = nameWithoutNumber;
        String finalUser = user;
        var userDude = userAvatars.computeIfAbsent(user, $ -> jda.getUserByTag(finalUser));
        if (userDude != null) {
            customAvatar = ",\n\"avatar_url\": \"" + userDude.getAvatarUrl() + "\"";
            username = userDude.getName();
        }

        var request = HttpRequest.newBuilder()
                .uri(URI.create(webhook.getUrl()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\n" +
                                "  \"content\": \"" + processDiscordEmotes(message) + "\",\n" +
                                "  \"username\": \"" + username + "\"" +
                                    customAvatar +
                                "}"
                ))
                .build();

        synchronized (httpClient) {
            var result = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            var body = result.body();
            if (body.contains("You are being rate limited.")) {
                int wait = Integer.parseInt(body.split("\"retry_after\": ")[1].replace("}", "").trim()) + 100;
                LOGGER.info("Waiting {}ms", wait);
                var waitMessage = channel.sendMessage("Waiting " + wait + "ms").complete();
                Thread.sleep(wait);
                waitMessage.delete().queue();
                sendMessage(user, message);
            }
        }
    }

    private String processDiscordEmotes(String message) {
        final String[] newMessage = {message};
        emotes.forEach(e -> newMessage[0] = newMessage[0].replace(":" + e.getName() + ":", e.getAsMention()));
        return newMessage[0];
    }
}
