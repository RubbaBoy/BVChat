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
import java.nio.file.Paths;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class BVChat extends ListenerAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(BVChat.class);

    private ConfigManager configManager;
    private JDAImpl jda;

    private WebhookManager webhookManager;
    private GPT2TextGenerator GPT2TextGenerator;
    private boolean processing = false;
    private long ownerId;
    private boolean active = true;
    private TextChannel textChannel;

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

        new ScheduledThreadPoolExecutor(1).scheduleAtFixedRate(() -> {
            if (typing) {
                textChannel.sendTyping().complete();
            }
        }, 1, 5, TimeUnit.SECONDS);
    }

    @Override
    public void onReady(@Nonnull ReadyEvent event) {
        jda = (JDAImpl) event.getJDA();

        java.lang.Thread.setDefaultUncaughtExceptionHandler((thread, exception) -> LOGGER.error("Error on thread {}", thread.getName(), exception));

        textChannel = jda.getTextChannelById(configManager.getConfig().getLong("channel"));
        webhookManager = new WebhookManager(this, textChannel);
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

            processing = true;
            startTyping();

            if (contents.startsWith("file ")) {
                var file = contents.substring("file ".length());
                if (!file.endsWith(".txt")) {
                    sendMessage(message, "File must be a text file!");
                    processing = false;
                    stopTyping();
                    return;
                }

                webhookManager.sendFromNNBatch("==== File " + file + " ====", Paths.get("E:\\BVChat\\" + file))
                        .thenRun(() -> processing = false)
                        .exceptionally(e -> {
                            LOGGER.error("Error while sending conversation while reading file " + file, e);
                            sendMessage(message, "Fuck, an error.");
                            return null;
                        });
                return;
            } else if (contents.equals("") || contents.equalsIgnoreCase("unprompted")) {
                sendMessage(message, "Aight, unprompted it is");
                GPT2TextGenerator.generateUnprompted().thenRun(() -> processing = false);
                return;
            }

            sendMessage(message, "Aight");
            GPT2TextGenerator.generateFor(contents).thenRun(() -> processing = false);
        }
    }

    private void sendMessage(Message respondingMessage, String message) {
        sendMessage(respondingMessage.getTextChannel(), message);
    }

    private void sendMessage(TextChannel textChannel, String message) {
        textChannel.sendMessage(message).queue();
    }

    private boolean typing;

    public void startTyping() {
        typing = true;
    }

    public void stopTyping() {
        typing = false;
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
