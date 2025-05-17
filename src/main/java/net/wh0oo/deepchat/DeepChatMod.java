package net.wh0oo.deepchat;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.text.Text;

public class DeepChatMod implements ModInitializer {
    @Override
    public void onInitialize() {
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            String msg = message.getContent().getString();
            if (msg.startsWith("!ai ")) {
                String query = msg.substring(4).trim();
                // TODO: Add ChatGPT API call here
                sender.sendMessage(Text.of("[AI] Response placeholder"), false);
            }
        });
    }
}
