package net.wh0oo.deepchat;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.text.Text;
import okhttp3.*;
import com.google.gson.*;

public class DeepChatMod implements ModInitializer {
    private static final String **API_KEY = "sk-your-key-here";** // ← **REPLACE WITH YOUR API KEY**
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final OkHttpClient httpClient = new OkHttpClient();

    @Override
    public void onInitialize() {
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            String msg = message.getContent().getString();
            if (msg.startsWith("!ai ")) {
                String query = msg.substring(4).trim();
                new Thread(() -> {
                    try {
                        String response = callChatGPT(query);
                        sender.sendMessage(Text.of("[AI] " + response), false);
                    } catch (Exception e) {
                        sender.sendMessage(Text.of("[AI] Error: " + e.getMessage()), false);
                    }
                }).start();
            }
        });
    }

    private String callChatGPT(String query) throws Exception {
        // **CHEAPEST SETTINGS (gpt-3.5-turbo, short responses)**
        String json = "{\"model\":\"gpt-3.5-turbo\",\"messages\":[{\"role\":\"user\",\"content\":\"" + 
                     query.replace("\"", "\\\"") + "\"}],\"max_tokens\":100}"; // ← **LIMITS RESPONSE LENGTH**

        Request request = new Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .header("Authorization", "Bearer " + **API_KEY**) // ← **USE YOUR KEY**
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
