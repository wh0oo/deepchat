package net.wh0oo.deepchat;

import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import okhttp3.*;
import com.google.gson.*;
import java.nio.file.*;
import java.io.IOException;
import java.util.concurrent.*;
import java.util.*;

public class DeepChatMod implements ModInitializer {
    // Config paths
    private static final String CONFIG_DIR = "config/deepchat/";
    private static final String API_KEY_PATH = CONFIG_DIR + "api_key.txt";
    private static final String MODEL_PATH = CONFIG_DIR + "model.txt";
    
    // API settings
    private static final String[] VALID_MODELS = {"deepseek-chat", "deepseek-reasoner"};
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    
    // Execution
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Map<UUID, Long> lastQueryTimes = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 3000;
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build();

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
            String normalized = problem.replaceAll("[,\\s]", "")
                                     .replace("×", "*")
                                     .replace("÷", "/")
                                     .replace("^", "**");

            Expression expr = new ExpressionBuilder(normalized).build();
            double result = expr.evaluate();
            
            return formatExpression(problem) + " = " + formatNumber(result);
        } catch (Exception e) {
            return "Math error: " + e.getMessage().replace("**", "^");
        }
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
                String apiKey = Files.readString(Paths.get(API_KEY_PATH)).trim();
                String model = Files.readString(Paths.get(MODEL_PATH)).trim();
                
                String json = "{\"model\":\"" + model + "\",\"messages\":[{\"role\":\"user\",\"content\":\"" + 
                            query.replace("\"", "\\\"") + "\"}]}";

                Request request = new Request.Builder()
                    .url("https://api.deepseek.com/v1/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .post(RequestBody.create(json, JSON))
                    .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    JsonObject jsonResponse = JsonParser.parseString(response.body().string()).getAsJsonObject();
                    String aiResponse = jsonResponse.getAsJsonArray("choices")
                        .get(0).getAsJsonObject()
                        .getAsJsonObject("message")
                        .get("content").getAsString();
                    
                    executeServerSay(source.getServer(), "[AI] " + aiResponse);
                }
            } catch (Exception e) {
                source.sendError(Text.literal("Error: " + e.getMessage().replaceAll("(?i)api key", "[REDACTED]")));
            }
        });
    }

    private String formatExpression(String expr) {
        return expr.replace("**", "^")
                  .replace("*", "×")
                  .replace("/", "÷");
    }

    private String formatNumber(double num) {
        if (num == (long) num) {
            return String.format("%,d", (long) num);
        }
        return String.format("%,.2f", num);
    }

    private void executeServerSay(MinecraftServer server, String message) {
        if (server == null || !server.isRunning()) return;
        server.getCommandManager().executeWithPrefix(
            server.getCommandSource().withLevel(4),
            "say " + message
        );
    }

    private void setupConfigFiles() {
        try {
            Files.createDirectories(Paths.get(CONFIG_DIR));
            if (!Files.exists(Paths.get(API_KEY_PATH))) {
                Files.write(Paths.get(API_KEY_PATH), "your-api-key-here".getBytes());
            }
            if (!Files.exists(Paths.get(MODEL_PATH))) {
                Files.write(Paths.get(MODEL_PATH), "deepseek-chat".getBytes());
            }
        } catch (IOException e) {
            System.err.println("Config error: " + e.getMessage());
        }
    }

    public void onDisable() {
        executor.shutdown();
    }
}
