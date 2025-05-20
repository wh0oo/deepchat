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
import java.util.regex.*;

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
    private static final int MAX_CHUNKS = 3; // Hard cap on message count
    
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build();

    @Override
    public void onInitialize() {
        setupConfigFiles();
        
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            String msg = message.getContent().getString();
            if (msg.startsWith("!ai ")) {
                ServerCommandSource source = sender.getServer().getCommandSource();
                UUID playerId = sender.getUuid();
                String query = msg.substring(4).trim();
                
                // Parse [max=X] parameter (default: null = no limit)
                Integer maxChars = null;
                Matcher matcher = Pattern.compile("\\[max=(\\d+)\\]").matcher(query);
                if (matcher.find()) {
                    maxChars = Integer.parseInt(matcher.group(1));
                    query = query.replace(matcher.group(0), "").trim();
                }

                if (source == null || source.getServer() == null) {
                    System.err.println("[ERROR] Invalid command source");
                    return;
                }
                
                if (System.currentTimeMillis() - lastQueryTimes.getOrDefault(playerId, 0L) < COOLDOWN_MS) {
                    source.sendError(Text.literal("Please wait 3 seconds between queries!"));
                    return;
                }
                lastQueryTimes.put(playerId, System.currentTimeMillis());
                
                executor.submit(() -> processQueryAsync(source, query, maxChars));
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

    private void processQueryAsync(ServerCommandSource source, String query, Integer maxChars) {
        try {
            System.out.println("[DeepChat] Processing: " + query);
            String response = processQueryWithRetry(query, maxChars);
            
            if (response == null || response.trim().isEmpty()) {
                throw new IOException("Empty API response");
            }
            
            executeServerSay(source.getServer(), "[AI] " + cleanMessage(response), maxChars);
                
        } catch (Exception e) {
            System.err.println("[ERROR] " + e.getMessage());
            source.sendError(Text.literal("AI Error: " + 
                e.getMessage().replaceAll("(?i)api key", "[REDACTED]")));
        }
    }

    private String cleanMessage(String message) {
        return message
            .replace("**", "")
            .replace("*", "")
            .replace("`", "")
            .replace("#", "")
            .replace("\n", " ")
            .replace("\"", "'");
    }

    private String processQueryWithRetry(String query, Integer maxChars) throws Exception {
        String apiKey = Files.readString(Paths.get(API_KEY_PATH)).trim();
        String model = validateModel(Files.readString(Paths.get(MODEL_PATH)).trim());
        String jsonPayload = buildRequestJson(model, query, maxChars);
        
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

    private String buildRequestJson(String model, String query, Integer maxChars) {
        JsonObject request = new JsonObject();
        request.addProperty("model", model);
        
        // Set token limit if maxChars specified (approximate 4 chars per token)
        if (maxChars != null) {
            request.addProperty("max_tokens", maxChars / 4);
        }
        
        JsonArray messages = new JsonArray();
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", query);
        messages.add(message);
        request.add("messages", messages);
        
        return request.toString();
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

    private void executeServerSay(MinecraftServer server, String message, Integer maxChars) {
        try {
            if (server == null || !server.isRunning()) return;
            
            // Apply length limit if specified
            if (maxChars != null) {
                message = message.substring(0, Math.min(message.length(), maxChars));
            }
            
            // Split into max 3 chunks
            List<String> chunks = new ArrayList<>();
            int remaining = message.length();
            int chunkSize = (int) Math.ceil(message.length() / (double) MAX_CHUNKS);
            
            for (int i = 0; i < MAX_CHUNKS && remaining > 0; i++) {
                int end = Math.min((i + 1) * chunkSize, message.length());
                chunks.add(message.substring(i * chunkSize, end));
                remaining -= (end - (i * chunkSize));
            }
            
            // Send with sequence markers
            for (int i = 0; i < chunks.size(); i++) {
                String chunk = String.format("[%d/%d] %s", i + 1, chunks.size(), chunks.get(i));
                server.getCommandManager().executeWithPrefix(
                    server.getCommandSource().withLevel(4),
                    "say " + chunk
                );
            }
        } catch (Exception e) {
            System.err.println("[Broadcast] Failed: " + e.getMessage());
        }
    }

    public void onDisable() {
        executor.shutdown();
    }
}
