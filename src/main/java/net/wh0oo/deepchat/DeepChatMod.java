package net.wh0oo.deepchat;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import okhttp3.*;
import com.google.gson.*;
import java.nio.file.*;
import java.io.IOException;

public class DeepChatMod implements ModInitializer {
    private static final String CONFIG_PATH = "config/deepchat/api_key.txt";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final OkHttpClient httpClient = new OkHttpClient();

    @Override
    public void onInitialize() {
        try {
            Files.createDirectories(Paths.get("config/deepchat"));
            if (!Files.exists(Paths.get(CONFIG_PATH))) {
                Files.write(Paths.get(CONFIG_PATH), "paste-your-deepseek-key-here".getBytes());
            }
        } catch (IOException e) {
            System.err.println("Failed to create config file: " + e.getMessage());
        }

        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            String msg = message.getContent().getString();
            if (msg.startsWith("!ai ")) {
                String query = msg.substring(4).trim();
                new Thread(() -> {
                    try {
                        String apiKey = getApiKey();
                        String response = callDeepSeek(apiKey, query);
                        // Fixed command execution
                        sender.getServer().getCommandManager().execute(
                            sender.getServer().getCommandSource().withSilent(), 
                            "say [AI] " + response
                        );
                    } catch (Exception e) {
                        sender.getServer().getCommandManager().execute(
                            sender.getServer().getCommandSource().withSilent(),
                            "say [AI] Error: Check server logs"
                        );
                        System.err.println("DeepSeek Error: " + e.getMessage());
                    }
                }).start();
            }
        });
    }

    private String getApiKey() throws IOException {
        return Files.readString(Paths.get(CONFIG_PATH)).trim();
    }

    private String callDeepSeek(String apiKey, String query) throws Exception {
        String json = "{\"model\":\"deepseek-chat\",\"messages\":[{\"role\":\"user\",\"content\":\"" + 
                     query.replace("\"", "\\\"") + "\"}],\"max_tokens\":100}";

        Request request = new Request.Builder()
            .url("https://api.deepseek.com/v1/chat/completions")
            .header("Authorization", "Bearer " + apiKey)
            .post(RequestBody.create(json, JSON))
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            JsonObject jsonResponse = JsonParser.parseString(response.body().string()).getAsJsonObject();
            return jsonResponse.getAsJsonArray("choices")
                .get(0).getAsJsonObject()
                .getAsJsonObject("message")
                .get("content").getAsString();
        }
    }
}
