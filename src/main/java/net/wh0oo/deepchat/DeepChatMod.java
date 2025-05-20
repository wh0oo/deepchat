package net.wh0oo.deepchat;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.text.Text;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.*;

public class DeepChatMod implements ModInitializer {
    private static final String API_URL = "https://api.example.com/ai"; // Replace with your AI endpoint
    private final OkHttpClient httpClient = new OkHttpClient();

    @Override
    public void onInitialize() {
        ServerMessageEvents.CHAT_MESSAGE.register((message, player, params) -> {
            String msg = message.getContent().getString();
            
            if (msg.startsWith("!ai ")) {
                String prompt = msg.substring(4);
                handleAIRequest(player, prompt);
            }
        });
    }

    private void handleAIRequest(PlayerEntity player, String prompt) {
        new Thread(() -> {
            try {
                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("prompt", prompt);

                Request request = new Request.Builder()
                    .url(API_URL)
                    .post(RequestBody.create(
                        requestBody.toString(),
                        MediaType.parse("application/json")
                    ))
                    .build();

                Response response = httpClient.newCall(request).execute();
                JsonObject jsonResponse = JsonParser.parseString(response.body().string()).getAsJsonObject();
                String aiResponse = jsonResponse.get("response").getAsString();

                player.sendMessage(Text.of("AI: " + aiResponse), false);
            } catch (Exception e) {
                player.sendMessage(Text.of("Error contacting AI service"), false);
                e.printStackTrace();
            }
        }).start();
    }
}
