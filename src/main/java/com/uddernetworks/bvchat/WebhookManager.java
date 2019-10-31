package com.uddernetworks.bvchat;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Icon;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.requests.restaction.WebhookAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class WebhookManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebhookManager.class);

    private Map<String, User> userAvatars = new HashMap<>();

    private JDA jda;
    private BVChat bvChat;

    private TextChannel channel;
    private List<Webhook> webhooks;

    public WebhookManager(BVChat bvChat) {
        this.bvChat = bvChat;
        this.jda = bvChat.getJda();
        this.channel = jda.getTextChannelById(bvChat.getConfigManager().getConfig().getLong("channel"));
        this.webhooks = channel.retrieveWebhooks().complete();
    }

    public void sendFromNNBatch(String input) {
        Arrays.stream(input.split("\n")).forEach(line -> {
            var userMessage = line.split(">", 2);
//            if (userMessage.length != 2) return;
            try {
                sendMessage(userMessage[0].trim(), userMessage[1].trim());
            } catch (IOException | InterruptedException e) {
                LOGGER.error("Error sending message from {}", userMessage[0].trim());
            }

            Thread.sleep(500);
        });
    }

    public void sendMessage(String user, String message) throws IOException, InterruptedException {
        var nameWithoutNumber = user.substring(0, user.lastIndexOf('#'));

        var webhook = webhooks.get(0);

        HttpClient client = HttpClient.newHttpClient();

        var customAvatar = "";
        var username = nameWithoutNumber;
        var userDude = userAvatars.computeIfAbsent(user, $ -> jda.getUserByTag(user));
        if (userDude != null) {
            customAvatar = ",\n\"avatar_url\": \"" + userDude.getAvatarUrl() + "\"";
            username = userDude.getName();
        }

        var request = HttpRequest.newBuilder()
                .uri(URI.create(webhook.getUrl()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\n" +
                                "  \"content\": \"" + message + "\",\n" +
                                "  \"username\": \"" + username + "\"" +
                                    customAvatar +
                                "}"
                ))
                .build();

        var result = client.send(request, HttpResponse.BodyHandlers.ofString());
        var body = result.body();
        if (body.contains("You are being rate limited.")) {
            int wait = Integer.parseInt(body.split("\"retry_after\": ")[1].replace("}", "").trim()) + 100;
            LOGGER.info("Waiting {}ms", wait);
            Thread.sleep(wait);
            sendMessage(user, message);
        }
    }
}
