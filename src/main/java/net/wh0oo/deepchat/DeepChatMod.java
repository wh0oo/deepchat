// minecraft 26.1.2
package net.wh0oo.deepchat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DeepChatMod implements ModInitializer {
    // Config paths
    private static final String CONFIG_DIR = "config/deepchat/";
    private static final String API_KEY_PATH = CONFIG_DIR + "api_key.txt";
    private static final String MODEL_PATH = CONFIG_DIR + "model.txt";

    // API settings
    private static final String DEFAULT_MODEL = "deepseek-v4-flash";
    private static final String[] VALID_MODELS = {
        "deepseek-v4-flash",
        "deepseek-v4-pro"
    };
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    // Execution
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Map<UUID, Long> lastQueryTimes = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 3000;
    private static final int MAX_CHUNKS = 3;
    private static final int SINGLE_MESSAGE_THRESHOLD = 240;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build();

    @Override
    public void onInitialize() {
        setupConfigFiles();

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> executor.shutdown());

        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            String msg = message.decoratedContent().getString();
            if (!msg.startsWith("!ai ")) return;

            MinecraftServer server = sender.level() != null ? sender.level().getServer() : null;
            if (server == null) {
                System.err.println("[DeepChat] ERROR: Could not resolve MinecraftServer from sender world");
                return;
            }

            UUID playerId = sender.getUUID();
            String query = msg.substring(4).trim();

            // Parse [max=X]
            final Integer maxChars;
            final String finalQuery;
            Matcher matcher = Pattern.compile("\\[max=(\\d+)\\]").matcher(query);
            if (matcher.find()) {
                maxChars = Integer.parseInt(matcher.group(1));
                finalQuery = query.replace(matcher.group(0), "").trim();
            } else {
                maxChars = null;
                finalQuery = query;
            }

            if (finalQuery.isBlank()) {
                sender.sendSystemMessage(Component.literal("Usage: !ai <question>"));
                return;
            }

            long now = System.currentTimeMillis();
            if (now - lastQueryTimes.getOrDefault(playerId, 0L) < COOLDOWN_MS) {
                sender.sendSystemMessage(Component.literal("Please wait 3 seconds between queries!"));
                return;
            }
            lastQueryTimes.put(playerId, now);

            executor.submit(() -> processQueryAsync(server, playerId, finalQuery, maxChars));
        });
    }

    private void setupConfigFiles() {
        try {
            Files.createDirectories(Paths.get(CONFIG_DIR));

            if (!Files.exists(Paths.get(API_KEY_PATH))) {
                Files.writeString(Paths.get(API_KEY_PATH), "paste-your-key-here");
            }

            if (!Files.exists(Paths.get(MODEL_PATH))) {
                Files.writeString(Paths.get(MODEL_PATH), DEFAULT_MODEL);
            }
        } catch (IOException e) {
            System.err.println("[DeepChat] Config Error: " + e.getMessage());
        }
    }

    private void processQueryAsync(MinecraftServer server, UUID playerId, String query, Integer maxChars) {
        try {
            System.out.println("[DeepChat] Processing: " + query);
            String response = processQueryWithRetry(query, maxChars);

            if (response == null || response.trim().isEmpty()) {
                throw new IOException("Empty API response");
            }

            String cleaned = cleanMessage(response);

            server.execute(() -> executeServerSay(server, cleaned, maxChars));

        } catch (Exception e) {
            String safeMessage = "AI Error: " + e.getMessage().replaceAll("(?i)api key", "[REDACTED]");
            System.err.println("[DeepChat] ERROR: " + safeMessage);

            server.execute(() -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player != null) {
                    player.sendSystemMessage(Component.literal(safeMessage));
                }
            });
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
        String configuredModel = Files.readString(Paths.get(MODEL_PATH)).trim();
        String model = validateModel(configuredModel);
        String jsonPayload = buildRequestJson(model, query, maxChars);

        Request request = new Request.Builder()
            .url("https://api.deepseek.com/v1/chat/completions")
            .header("Authorization", "Bearer " + apiKey)
            .post(RequestBody.create(JSON, jsonPayload))
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String rawResponse = response.body() != null ? response.body().string() : "";

            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + ": " + rawResponse);
            }

            return parseResponse(rawResponse);
        }
    }

    private String validateModel(String model) {
        if (model == null || model.isBlank()) {
            return DEFAULT_MODEL;
        }

        String normalized = model.trim().toLowerCase(Locale.ROOT);

        for (String validModel : VALID_MODELS) {
            if (validModel.equals(normalized)) {
                return normalized;
            }
        }

        System.err.println(
            "[DeepChat] Invalid model in config/deepchat/model.txt: '" + model + "'. " +
            "Valid models are: deepseek-v4-flash, deepseek-v4-pro. " +
            "Using default: " + DEFAULT_MODEL
        );

        return DEFAULT_MODEL;
    }

    private String buildRequestJson(String model, String query, Integer maxChars) {
        JsonObject request = new JsonObject();
        request.addProperty("model", model);

        if (maxChars != null) {
            request.addProperty("max_tokens", Math.max(1, maxChars / 4));
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

            if (maxChars != null) {
                message = message.substring(0, Math.min(message.length(), maxChars));
            }

            if (message.length() <= SINGLE_MESSAGE_THRESHOLD) {
                server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack(),
                    "say [AI] " + message
                );
                return;
            }

            List<String> chunks = new ArrayList<>();
            int start = 0;
            int remainingLength = message.length();

            while (remainingLength > 0 && chunks.size() < MAX_CHUNKS - 1) {
                int chunkLength = Math.min(220, remainingLength);
                int splitAt = message.lastIndexOf(' ', start + chunkLength);
                if (splitAt <= start) splitAt = start + chunkLength;

                chunks.add(message.substring(start, splitAt).trim());
                remainingLength -= (splitAt - start);
                start = splitAt;
            }

            if (remainingLength > 0) {
                String lastChunk = message.substring(start);
                if (chunks.size() == MAX_CHUNKS - 1) {
                    lastChunk = "[...] " + lastChunk.substring(0, Math.min(100, lastChunk.length()));
                }
                chunks.add(lastChunk);
            }

            for (int i = 0; i < chunks.size(); i++) {
                server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack(),
                    String.format("say [AI %d/%d] %s", i + 1, chunks.size(), chunks.get(i))
                );
            }

        } catch (Exception e) {
            System.err.println("[DeepChat] Broadcast failed: " + e.getMessage());
        }
    }
}