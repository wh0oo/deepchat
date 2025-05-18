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
import java.util.Arrays; // Added missing import

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
                // Fixed: Use server's command source instead of player entity
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

    // ... [rest of setupConfigFiles() remains unchanged] ...

    private String validateModel(String model) {
        // Fixed: Added Arrays import
        if (!Arrays.asList(VALID_MODELS).contains(model.toLowerCase())) {
            System.err.println("Invalid model, using deepseek-chat");
            return "deepseek-chat";
        }
        return model;
    }

    // ... [rest of methods remain unchanged] ...

    // Fixed: Removed @Override annotation
    public void onDisable() {
        executor.shutdown();
    }
}
