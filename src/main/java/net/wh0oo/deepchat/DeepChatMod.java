package net.wh0oo.deepchat;

import javax.script.ScriptEngineManager;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
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

    private String solveMathProblem(String problem) {
        try {
            // Normalize input
            problem = problem.replaceAll("[,\\s]", "")
                           .replace("×", "*")
                           .replace("÷", "/");

            // Use JavaScript engine for math evaluation
            ScriptEngineManager mgr = new ScriptEngineManager();
            ScriptEngine engine = mgr.getEngineByName("JavaScript");
            Object result = engine.eval(problem);
            
            return "Result: " + formatNumber(Double.parseDouble(result.toString()));
        } catch (ScriptException e) {
            return "Math syntax error: " + e.getMessage();
        } catch (Exception e) {
            return "Calculation failed: " + e.getMessage();
        }
    }

    private String formatNumber(double num) {
        if (num == (long) num) {
            return String.format("%,d", (long) num);
        }
        return String.format("%,.2f", num);
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
                String response = "Response from your AI implementation"; // Replace with API call
                executeServerSay(source.getServer(), "[AI] " + response);
            } catch (Exception e) {
                source.sendError(Text.literal("Error: " + e.getMessage()));
            }
        });
    }

    private void executeServerSay(MinecraftServer server, String message) {
        if (server == null || !server.isRunning()) return;
        server.getCommandManager().executeWithPrefix(
            server.getCommandSource().withLevel(4),
            "say " + message.replace("**", "")
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
