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
import java.util.concurrent.TimeUnit;

public class DeepChatMod implements ModInitializer {
    // Config paths
    private static final String CONFIG_DIR = "config/deepchat/";
    private static final String API_KEY_PATH = CONFIG_DIR + "api_key.txt";
    private static final String MODEL_PATH = CONFIG_DIR + "model.txt";
    
    // HTTP client
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build();

    @Override
    public void onInitialize() {
        setupConfigFiles();
        
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            String msg = message.getContent().getString();
            if (msg.startsWith("!ai ")) {
                String query = msg.substring(4).trim();
                new Thread(() -> {
                    try {
                        String response = processQuery(query);
                        executeServerSay(sender.getServer(), "[AI] " + response);
                    } catch (Exception e) {
                        executeServerSay(sender.getServer(), "[AI] Error: " + e.getMessage());
                        System.err.println("API Error: " + e.getMessage());
                    }
                }).start();
            }
        });
    }

    private void setupConfigFiles() {
        try {
            Files.createDirectories(Paths.get(CONFIG_DIR));
            
            // Create API key file if missing
            if (!Files.exists(Paths.get(API_KEY_PATH))) {
                Files.write(Paths.get(API_KEY_PATH), "paste-your-key-here".getBytes());
            }
            
            // Create model config file with default "medium"
            if (!Files.exists(Paths.get(MODEL_PATH))) {
                Files.write(Paths.get(MODEL_PATH), "medium".getBytes());
            }
        } catch (IOException e) {
            System.err.println("Config Error: " + e.getMessage());
        }
    }

    private String processQuery(String query) throws Exception {
        String apiKey = Files.readString(Paths.get(API_KEY_PATH)).trim();
        String model = Files.readString(Paths.get(MODEL_PATH)).trim().toLowerCase();
        
        // Map simple names to actual DeepSeek models
        String apiModel = switch (model) {
            case "light" -> "deepseek-chat-1.3-light";
            case "full" -> "deepseek-coder-1.3-instruct";
            default -> "deepseek-chat-1.3"; // medium/default
        };

        String json = "{\"model\":\"" + apiModel + "\",\"messages\":[{\"role\":\"user\",\"content\":\"" + 
                     query.replace("\"", "\\\"") + "\"}],\"max_tokens\":100}";

        Request request = new Request.Builder()
            .url("https://api.deepseek.com/v1/chat/completions")
            .header("Authorization", "Bearer " + apiKey)
            .post(RequestBody.create(json, JSON))
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            return parseResponse(response);
        }
    }

    private String parseResponse(Response response) throws IOException {
        if (!response.isSuccessful()) {
            throw new IOException("API Error: " + response.code() + " - " + response.body().string());
        }
        JsonObject jsonResponse = JsonParser.parseString(response.body().string()).getAsJsonObject();
        return jsonResponse.getAsJsonArray("choices")
            .get(0).getAsJsonObject()
            .getAsJsonObject("message")
            .get("content").getAsString();
    }

    private void executeServerSay(MinecraftServer server, String message) {
        server.getCommandManager().executeWithPrefix(
            server.getCommandSource().withSilent(),
            "say " + message
        );
    }
}
