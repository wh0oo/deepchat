package net.wh0oo.deepchat;

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
import java.util.concurrent.atomic.AtomicLong;

public class DeepChatMod implements ModInitializer {
    // Config paths
    private static final String CONFIG_DIR = "config/deepchat/";
    private static final String API_KEY_PATH = CONFIG_DIR + "api_key.txt";
    private static final String MODEL_PATH = CONFIG_DIR + "model.txt";
    
    // API settings
    private static final String[] VALID_MODELS = {"deepseek-chat", "deepseek-reasoner"};
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    
    // Execution
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor();
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final Map<UUID, Long> lastQueryTimes = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 3000;
    
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build();

    @Override
    public void onInitialize() {
        setupConfigFiles();
        startMonitoring();
        
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            String msg = message.getContent().getString();
            if (msg.startsWith("!ai ")) {
                ServerCommandSource source = sender.getServer().getCommandSource();
                UUID playerId = sender.getUuid();
                String query = msg.substring(4).trim();
                
                if (source == null || source.getServer() == null) {
                    System.err.println("[ERROR] Invalid command source");
                    return;
                }
                
                if (System.currentTimeMillis() - lastQueryTimes.getOrDefault(playerId, 0L) < COOLDOWN_MS) {
                    source.sendError(Text.literal("Please wait 3 seconds between queries!"));
                    return;
                }
                lastQueryTimes.put(playerId, System.currentTimeMillis());
                
                executor.submit(() -> processQueryAsync(source, query));
            }
        });
    }

    private void setupConfigFiles() {
        try {
            Files.createDirectories(Paths.get(CONFIG_DIR));
            if (!Files.exists(Paths.get(API_KEY_PATH))) {
                Files.write(Paths.get(API_KEY_PATH), "paste-your-key-here".getBytes());
            }
            if (!Files.exists(Paths.get(MODEL_PATH))) {
                Files.write(Paths.get(MODEL_PATH), "deepseek-chat".getBytes());
            }
        } catch (IOException e) {
            System.err.println("Config Error: " + e.getMessage());
        }
    }

    private void startMonitoring() {
        monitor.scheduleAtFixedRate(() -> {
            System.out.printf("[DeepChat] Stats - Active: %d, Queued: %d, Total: %d%n",
                ((ThreadPoolExecutor)executor).getActiveCount(),
                ((ThreadPoolExecutor)executor).getQueue().size(),
                totalRequests.get());
        }, 0, 5, TimeUnit.SECONDS);
    }

    private void processQueryAsync(ServerCommandSource source, String query) {
        totalRequests.incrementAndGet();
        long startTime = System.currentTimeMillis();
        
        try {
            System.out.println("[DeepChat] Processing: " + query);
            String response = processQueryWithRetry(query);
            long duration = System.currentTimeMillis() - startTime;
            
            if (response == null || response.trim().isEmpty()) {
                throw new IOException("Empty API response");
            }
            
            executeServerSay(source.getServer(), 
                String.format("[AI] [%dms] %s", duration, response));
                
        } catch (Exception e) {
            System.err.println("[ERROR] " + e.getMessage());
            source.sendError(Text.literal("AI Error: " + 
                e.getMessage().replaceAll("(?i)api key", "[REDACTED]")));
        }
    }

    private String processQueryWithRetry(String query) throws Exception {
        String apiKey = Files.readString(Paths.get(API_KEY_PATH)).trim();
        String model = validateModel(Files.readString(Paths.get(MODEL_PATH)).trim());
        String jsonPayload = buildRequestJson(model, query);
        
        Request request = new Request.Builder()
            .url("https://api.deepseek.com/v1/chat/completions")
            .header("Authorization", "Bearer " + apiKey)
            .post(RequestBody.create(jsonPayload, JSON))
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String rawResponse = response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + ": " + rawResponse);
            }
            return parseResponse(rawResponse);
        }
    }

    private String validateModel(String model) {
        return Arrays.asList(VALID_MODELS).contains(model.toLowerCase()) ? model : "deepseek-chat";
    }

    private String buildRequestJson(String model, String query) {
        return "{\"model\":\"" + model + "\",\"messages\":[{\"role\":\"user\",\"content\":\"" + 
              query.replace("\"", "\\\"") + "\"}],\"max_tokens\":100}";
    }

    private String parseResponse(String rawResponse) throws IOException {
        JsonObject json = JsonParser.parseString(rawResponse).getAsJsonObject();
        if (json.has("error")) {
            throw new IOException(json.get("error").toString());
        }
        return json.getAsJsonArray("choices")
            .get(0).getAsJsonObject()
            .getAsJsonObject("message")
            .get("content").getAsString();
    }

    private void executeServerSay(MinecraftServer server, String message) {
        try {
            server.getCommandManager().executeWithPrefix(
                server.getCommandSource().withLevel(4).withSilent(),
                "say " + message
            );
            System.out.println("[Broadcast] Success: " + message);
        } catch (Exception e) {
            System.err.println("[Broadcast] Failed: " + e.getMessage());
        }
    }

    public void onDisable() {
        executor.shutdown();
        monitor.shutdown();
    }
}
