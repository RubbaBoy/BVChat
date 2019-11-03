package com.uddernetworks.bvchat;

import com.uddernetworks.bvchat.utils.Commandline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class GPT2TextGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(GPT2TextGenerator.class);

    private final BVChat bvChat;
    private File gpt2Directory;

    public GPT2TextGenerator(BVChat bvChat, File gpt2Directory) {
        this.bvChat = bvChat;
        this.gpt2Directory = gpt2Directory;
    }

    public CompletableFuture<Void> generateFor(String input) {
        return CompletableFuture.runAsync(() -> {
            boolean readingLines = false;
            var inputLines = new ArrayList<String>();
            for (String line : Commandline.runCommand(List.of("python", "src/interactive_conditional_samples.py", "--temperature", "0.8", "--top_k", "40", "--model_name", "ready", "--prompt", input), gpt2Directory)
                    .split("\\r?\\n")) {
                if (line.startsWith("====================")) {
                    readingLines = true;
                } else if (readingLines) {
                    inputLines.add(line);
                }
            }

            bvChat.stopTyping();
            bvChat.getWebhookManager().sendFromNNBatch(input, String.join("\n", inputLines)).join();
        });
    }

    public CompletableFuture<Void> generateUnprompted() {
        return CompletableFuture.runAsync(() -> {
            boolean readingLines = false;
            var inputLines = new ArrayList<String>();
            for (String line : Commandline.runCommand(List.of("python", "src/generate_unconditional_samples.py", "--temperature", "0.8", "--top_k", "40", "--model_name", "ready", "--nsamples", "1"), gpt2Directory)
                    .split("\\r?\\n")) {
                if (line.startsWith("====================")) {
                    readingLines = true;
                } else if (readingLines) {
                    inputLines.add(line);
                }
            }

            bvChat.stopTyping();
            bvChat.getWebhookManager().sendFromNNBatch("==== Unprompted ====", String.join("\n", inputLines)).join();
        });
    }
}
