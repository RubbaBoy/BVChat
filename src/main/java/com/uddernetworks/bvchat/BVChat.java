package com.uddernetworks.bvchat;

import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.internal.JDAImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.security.auth.login.LoginException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class BVChat extends ListenerAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(BVChat.class);

    private ConfigManager configManager;
    private JDAImpl jda;

    private WebhookManager webhookManager;
    private GPT2TextGenerator GPT2TextGenerator;
    private boolean processing = false;
    private long ownerId;
    private boolean active = true;

    public static void main(String[] args) throws LoginException {
        new BVChat().main();
    }

    private void main() throws LoginException {
        (configManager = new ConfigManager("src/main/resources/" + (new File("src/main/resources/secret.conf").exists() ? "secret.conf" : "config.conf"))).init();

        new JDABuilder()
                .setToken(configManager.getPrimaryToken())
                .setStatus(OnlineStatus.ONLINE)
                .setActivity(Activity.watching("kids"))
                .addEventListeners(this)
                .build();
    }

    @Override
    public void onReady(@Nonnull ReadyEvent event) {
        jda = (JDAImpl) event.getJDA();

        java.lang.Thread.setDefaultUncaughtExceptionHandler((thread, exception) -> LOGGER.error("Error on thread {}", thread.getName(), exception));

        webhookManager = new WebhookManager(this);
        GPT2TextGenerator = new GPT2TextGenerator(this, new File("E:\\gpt-2"));

        ownerId = configManager.getConfig().getLong("owner");
    }

    @Override
    public void onMessageReceived(@Nonnull MessageReceivedEvent event) {
        var message = event.getMessage();
        var author = event.getAuthor();
        var stripped = message.getContentStripped();
        if (message.isMentioned(jda.getSelfUser())) {
            var contents = stripped.length() != "@BVChat".length() ? stripped.substring("@BVChat ".length()) : "";

            if (contents.equalsIgnoreCase("stop")) {
                if (author.getIdLong() == ownerId) {
                    sendMessage(message, "aight");
                    active = false;
                } else {
                    sendMessage(message, "Only daddy rubba can do that ~uwu");
                }
                return;
            } else if (contents.equalsIgnoreCase("start")) {
                if (author.getIdLong() == ownerId) {
                    sendMessage(message, "aight");
                    active = true;
                } else {
                    sendMessage(message, "Only daddy rubba can do that ~uwu");
                }
                return;
            }

            if (!active && author.getIdLong() != ownerId) {
                sendMessage(message, "rubba says no, thot");
                return;
            }

            if (processing) {
                sendMessage(message, "I'm already talking motherfucker");
                return;
            }

            if (contents.startsWith("file ")) {
                var file = contents.substring("file ".length());
                if (!file.endsWith(".txt")) {
                    sendMessage(message, "File must be a text file!");
                    return;
                }

                processing = true;
                try {
                    webhookManager.sendFromNNBatch("==== File " + file + " ====", Files.readString(Paths.get("E:\\BVChat\\" + file)));
                } catch (IOException e) {
                    LOGGER.error("Error reading " + file, e);
                    sendMessage(message, "Fuck, an error.");
                }
                processing = false;

                return;
            } else if (contents.equals("") || contents.equalsIgnoreCase("unprompted")) {
                processing = true;
                sendMessage(message, "Got it");
                GPT2TextGenerator.generateUnprompted().thenRun(() -> processing = false);
                return;
            }

            processing = true;
            sendMessage(message, "Got it");
            GPT2TextGenerator.generateFor(contents).thenRun(() -> processing = false);
        }
    }

    private void sendMessage(Message respondingMessage, String message) {
        sendMessage(respondingMessage.getTextChannel(), message);
    }

    private void sendMessage(TextChannel textChannel, String message) {
        textChannel.sendMessage(message).queue();
    }

    public JDAImpl getJda() {
        return jda;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public WebhookManager getWebhookManager() {
        return webhookManager;
    }
}
