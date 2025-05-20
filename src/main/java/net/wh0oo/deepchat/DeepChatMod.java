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
    
    // Execution
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
            // Normalize input
            problem = problem.replaceAll("[,\\s]", "").replace("×", "*").replace("÷", "/");
            
            // Parse components
            String[] parts = problem.split("[()+\\-*/^]");
            if (parts.length < 6) return "Format: (num1*num2)+(num3/num4)-(num5^num6*num7)";
            
            // Handle exponents and decimals
            double num1 = Double.parseDouble(parts[0]);
            double num2 = Double.parseDouble(parts[1]);
            double num3 = Double.parseDouble(parts[2]);
            double num4 = Double.parseDouble(parts[3]);
            double num5 = Double.parseDouble(parts[4]);
            double num6 = Double.parseDouble(parts[5]);
            
            // Calculate
            double mult1 = num1 * num2;
            double div = num3 / num4;
            double exp = Math.pow(num5, num6);
            
            // Handle optional decimal multiplier (e.g., 3.14)
            double decimalMultiplier = parts.length > 6 ? Double.parseDouble(parts[6]) : 1.0;
            double finalTerm = exp * decimalMultiplier;
            
            double result = mult1 + div - finalTerm;
            
            return String.format(
                "%.0f × %.0f = %,.0f\n" +
                "%.0f ÷ %.0f = %,.2f\n" +
                "%.0f^%.0f × %.2f = %,.2f\n" +
                "Result: %,.0f + %,.2f - %,.2f = **%,.2f**",
                num1, num2, mult1,
                num3, num4, div,
                num5, num6, decimalMultiplier, finalTerm,
                mult1, div, finalTerm, result
            );
        } catch (Exception e) {
            return "Invalid math problem: " + e.getMessage();
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
