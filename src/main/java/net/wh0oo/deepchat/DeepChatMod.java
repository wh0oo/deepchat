package net.wh0oo.deepchat;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import java.util.concurrent.*;
import java.util.*;
import java.io.IOException;
import java.nio.file.*;

public class DeepChatMod implements ModInitializer {
    // Config paths
    private static final String CONFIG_DIR = "config/deepchat/";
    private static final String API_KEY_PATH = CONFIG_DIR + "api_key.txt";
    
    // Thread pool
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Map<UUID, Long> lastQueryTimes = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 3000;

    @Override
    public void onInitialize() {
        setupConfigFiles();
        
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            String msg = message.getContent().getString();
            ServerCommandSource source = sender.getServer().getCommandSource();
            
            if (msg.startsWith("!ai solve ")) {
                String solution = solveMathProblem(msg.substring("!ai solve ".length()));
                executeServerSay(source.getServer(), "[Math] " + solution);
            } 
            else if (msg.startsWith("!ai ")) {
                handleAIQuery(source, sender.getUuid(), msg.substring("!ai ".length()));
            }
        });
    }

    private void handleAIQuery(ServerCommandSource source, UUID playerId, String query) {
        if (source == null || source.getServer() == null) return;
        
        if (System.currentTimeMillis() - lastQueryTimes.getOrDefault(playerId, 0L) < COOLDOWN_MS) {
            source.sendError(Text.literal("Wait 3 seconds between queries!"));
            return;
        }
        lastQueryTimes.put(playerId, System.currentTimeMillis());
        
        executor.submit(() -> {
            try {
                String response = "Response from your AI implementation"; // Replace with actual API call
                executeServerSay(source.getServer(), "[AI] " + response);
            } catch (Exception e) {
                source.sendError(Text.literal("Error: " + e.getMessage()));
            }
        });
    }

    private String solveMathProblem(String problem) {
        try {
            // Remove all commas and parentheses, then split by operators
            String[] parts = problem.replaceAll("[,\\(\\)]", "").split("[×x\\+\\-÷/]");
            if (parts.length != 6) return "Format: num1 × num2) + (num3 × num4) - (num5 ÷ num6";
            
            long[] nums = new long[6];
            for (int i = 0; i < 6; i++) nums[i] = Long.parseLong(parts[i].trim());
            
            long mult1 = nums[0] * nums[1];
            long mult2 = nums[2] * nums[3];
            long div = nums[4] / nums[5];
            long result = mult1 + mult2 - div;
            
            return String.format(
                "%,d × %,d = %,d\n" +
                "%,d × %,d = %,d\n" +
                "%,d ÷ %,d = %,d\n" +
                "Result: %,d + %,d - %,d = **%,d**",
                nums[0], nums[1], mult1,
                nums[2], nums[3], mult2,
                nums[4], nums[5], div,
                mult1, mult2, div, result
            );
        } catch (Exception e) {
            return "Invalid math problem";
        }
    }

    private void executeServerSay(MinecraftServer server, String message) {
        if (server == null || !server.isRunning()) return;
        server.getCommandManager().executeWithPrefix(
            server.getCommandSource().withLevel(4),
            "say " + message.replace("**", "") // Remove markdown for Minecraft
        );
    }

    private void setupConfigFiles() {
        try {
            Files.createDirectories(Paths.get(CONFIG_DIR));
            if (!Files.exists(Paths.get(API_KEY_PATH))) {
                Files.write(Paths.get(API_KEY_PATH), "paste-your-api-key-here".getBytes());
            }
        } catch (IOException e) {
            System.err.println("Config error: " + e.getMessage());
        }
    }

    public void onDisable() {
        executor.shutdown();
    }
}
