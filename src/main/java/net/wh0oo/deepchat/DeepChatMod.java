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
    
    // Execution pool with monitoring
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor();
    private final AtomicLong totalRequests = new AtomicLong(0);
    
    // Rate limiting
    private final Map<UUID, Long> lastQueryTimes = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 3000;
    
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(chain -> {
            Request request = chain.request();
            long startTime = System.nanoTime();
            Response response = chain.proceed(request);
            long durationMs = (System.nanoTime() - startTime) / 1_000_000;
            System.out.printf("[Network] %s %s (%d ms)%n",
                request.method(), request.url(), durationMs);
            return response;
        })
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
                
                // Rate limit check
                if (System.currentTimeMillis() - lastQueryTimes.getOrDefault(playerId, 0L) < COOLDOWN_MS) {
                    source.sendError(Text.literal("Please wait 3 seconds between queries!"));
                    return;
                }
                lastQueryTimes.put(playerId, System.currentTimeMillis());
                
                executor.submit(() -> processQueryAsync(source, query));
            }
        });
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
            System.out.println("[DeepChat] Processing query: " + query);
            String response = processQueryWithRetry(query);
            long duration = System.currentTimeMillis() - startTime;
            
            executeServerSay(source.getServer(), 
                String.format("[AI] [%dms] %s", duration, response));
                
        } catch (Exception e) {
            System.err.println("[DeepChat] Error processing query: " + e.getMessage());
            e.printStackTrace();
            executeServerSay(source.getServer(), 
                "[AI Error] " + e.getMessage().replaceAll("(?i)api key", "[REDACTED]"));
        }
    }

    private String processQueryWithRetry(String query) throws Exception {
        String apiKey = Files.readString(Paths.get(API_KEY_PATH)).trim();
        String model = validateModel(Files.readString(Paths.get(MODEL_PATH)).trim());

        // Network check
        try {
            Request testRequest = new Request.Builder()
                .url("https://api.deepseek.com/health")
                .build();
            Response testResponse = httpClient.newCall(testRequest).execute();
            System.out.println("[Network] Test response: " + testResponse.code());
        } catch (Exception e) {
            throw new IOException("Network unreachable: " + e.getMessage());
        }

        String jsonPayload = buildRequestJson(model, query);
        System.out.println("[DeepChat] Sending JSON: " + jsonPayload);
        
        Request request = new Request.Builder()
            .url("https://api.deepseek.com/v1/chat/completions")
            .header("Authorization", "Bearer " + apiKey)
            .post(RequestBody.create(jsonPayload, JSON))
            .build();

        for (int attempt = 1; attempt <= 3; attempt++) {
            try (Response response = httpClient.newCall(request).execute()) {
                String rawResponse = response.body().string();
                System.out.println("[DeepChat] Raw API response: " + rawResponse);
                
                if (!response.isSuccessful()) {
                    throw new IOException("HTTP " + response.code() + ": " + rawResponse);
                }
                return parseResponse(rawResponse);
            } catch (IOException e) {
                if (attempt == 3) throw e;
                Thread.sleep(1000 * attempt);
            }
        }
        throw new IOException("Max retries exceeded");
    }

    private String parseResponse(String rawResponse) throws IOException {
        try {
            JsonObject json = JsonParser.parseString(rawResponse).getAsJsonObject();
            if (json.has("error")) {
                throw new IOException(json.get("error").toString());
            }
            return json.getAsJsonArray("choices")
                .get(0).getAsJsonObject()
                .getAsJsonObject("message")
                .get("content").getAsString();
        } catch (Exception e) {
            System.err.println("Failed to parse: " + rawResponse);
            throw new IOException("Invalid API response format", e);
        }
    }

    // ... (keep existing methods: setupConfigFiles, validateModel, buildRequestJson, executeServerSay)

    @Override
    public void onDisable() {
        executor.shutdown();
        monitor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
