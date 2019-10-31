package com.uddernetworks.bvchat;

import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.events.ReadyEvent;
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

    public static void main(String[] args) throws LoginException {
        new BVChat().main();
        Thread.sleep(10000);
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
//        webhookManager.sendMessage("RubbaBoy#2832", "Yoooo wassup");
        try {
            webhookManager.sendFromNNBatch(Files.readString(Paths.get("E:\\BVChat\\input.txt")));
        } catch (IOException e) {
            LOGGER.error("Error reading input.txt", e);
        }
    }

    public JDAImpl getJda() {
        return jda;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }
}
