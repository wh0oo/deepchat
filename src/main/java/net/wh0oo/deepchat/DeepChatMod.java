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
import java.util.Arrays;

public class DeepChatMod implements ModInitializer {
    // Config paths
    private static final String CONFIG_DIR = "config/deepchat/";
    private static final String API_KEY_PATH = CONFIG_DIR + "api_key.txt";
    private static final String MODEL_PATH = CONFIG_DIR + "model.txt";
    
    // API settings
    private static final String[] VALID_MODELS = {"deepseek-chat", "deepseek-reasoner"};
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    
    // Execution pool
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build();

    @Override
    public void onInitialize() {
        setupConfigFiles();
        
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            String msg = message.getContent().getString();
            if (msg.startsWith("!ai ")) {
                ServerCommandSource source = sender.getServer().getCommandSource();
                String query = msg.substring(4).trim();
                executor.submit(() -> processQueryAsync(source, query));
            }
        });
    }

    private void processQueryAsync(ServerCommandSource source, String query) {
        try {
            String response = processQueryWithRetry(query);
            executeServerSay(source.getServer(), "[AI] " + response);
        } catch (Exception e) {
            executeServerSay(source.getServer(), "[AI] Error: " + e.getMessage());
            System.err.println("API Error: " + e.getMessage());
        }
    }

    // ADDED MISSING METHOD
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

    // ADDED MISSING METHOD
    private String processQueryWithRetry(String query) throws Exception {
        String apiKey = Files.readString(Paths.get(API_KEY_PATH)).trim();
        String model = validateModel(Files.readString(Paths.get(MODEL_PATH)).trim());

        Request request = new Request.Builder()
            .url("https://api.deepseek.com/v1/chat/completions")
            .header("Authorization", "Bearer " + apiKey)
            .post(RequestBody.create(buildRequestJson(model, query), JSON))
            .build();

        for (int attempt = 1; attempt <= 3; attempt++) {
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful()) return parseResponse(response);
                if (attempt == 3) throw new IOException("API Error: " + response.code());
            }
            Thread.sleep(1000 * attempt);
        }
        throw new IOException("Max retries exceeded");
    }

    // ADDED MISSING METHOD
    private String validateModel(String model) {
        if (!Arrays.asList(VALID_MODELS).contains(model.toLowerCase())) {
            System.err.println("Invalid model, using deepseek-chat");
            return "deepseek-chat";
        }
        return model;
    }

    // ADDED MISSING METHOD
    private String buildRequestJson(String model, String query) {
        return "{\"model\":\"" + model + "\",\"messages\":[{\"role\":\"user\",\"content\":\"" + 
              query.replace("\"", "\\\"") + "\"}],\"max_tokens\":100}";
    }

    // ADDED MISSING METHOD
    private String parseResponse(Response response) throws IOException {
        JsonObject json = JsonParser.parseString(response.body().string()).getAsJsonObject();
        return json.getAsJsonArray("choices")
            .get(0).getAsJsonObject()
            .getAsJsonObject("message")
            .get("content").getAsString();
    }

    // ADDED MISSING METHOD
    private void executeServerSay(MinecraftServer server, String message) {
        server.getCommandManager().executeWithPrefix(
            server.getCommandSource().withSilent(),
            "say " + message
        );
    }

    public void onDisable() {
        executor.shutdown();
    }
}
