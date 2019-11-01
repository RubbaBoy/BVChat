package com.uddernetworks.bvchat;

import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.internal.JDAImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.security.auth.login.LoginException;
import java.io.File;

public class BVChat extends ListenerAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(BVChat.class);

    private ConfigManager configManager;
    private JDAImpl jda;

    private WebhookManager webhookManager;
    private PromptedTextGenerator promptedTextGenerator;
    private boolean processing = false;

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
        promptedTextGenerator = new PromptedTextGenerator(this, new File("E:\\gpt-2"));
    }

    @Override
    public void onMessageReceived(@Nonnull MessageReceivedEvent event) {
        var message = event.getMessage();
        if (message.isMentioned(jda.getSelfUser())) {
            if (processing) {
                message.getTextChannel().sendMessage("I'm already talking motherfucker").queue();
                return;
            }

            processing = true;
            var contents = message.getContentStripped().substring("@BVChat ".length());
            message.getTextChannel().sendMessage("Got it").queue();
            promptedTextGenerator.generateFor(contents).thenRun(() -> processing = false);
        }
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
