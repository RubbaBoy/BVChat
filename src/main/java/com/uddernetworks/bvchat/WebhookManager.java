package com.uddernetworks.bvchat;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Emote;
import net.dv8tion.jda.api.entities.MessageEmbed;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    public WebhookManager(BVChat bvChat) {
        this.bvChat = bvChat;
        this.jda = bvChat.getJda();
        this.channel = jda.getTextChannelById(bvChat.getConfigManager().getConfig().getLong("channel"));
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

    public void sendFromNNBatch(String prompt, String input) {
        if (prompt != null) sendPromptNotice(prompt);
        Arrays.stream(input.split("\n")).forEach(line -> {
            var userMessage = line.split(" > ", 2);
            if (userMessage.length != 2) return;
            var message = userMessage[1].trim();

            double length = message.split("\\s+").length;
            Thread.sleep((long) Math.min(random(0, 2000) + length * 100, 4000));

            try {
                sendMessage(userMessage[0].trim(), message);
            } catch (IOException | InterruptedException e) {
                LOGGER.error("Error sending message from {}", userMessage[0].trim());
            }
        });
        if (prompt != null) sendPromptEndNotice(prompt);
    }

    private double random(int origin, int bound) {
        return ThreadLocalRandom.current().nextDouble(origin, bound);
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
                                "  \"content\": \"" + processDiscordEmotes(message) + "\",\n" +
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
            var waitMessage = channel.sendMessage("Waiting " + wait + "ms").complete();
            Thread.sleep(wait);
            waitMessage.delete().queue();
            sendMessage(user, message);
        }
    }

    private String processDiscordEmotes(String message) {
        final String[] newMessage = {message};
        emotes.forEach(e -> newMessage[0] = newMessage[0].replace(":" + e.getName() + ":", e.getAsMention()));
        return newMessage[0];
    }
}
