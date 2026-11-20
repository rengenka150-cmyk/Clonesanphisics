package com.example;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public class Clones_on_phisicsClient implements ClientModInitializer {
    private static KeyMapping spawnCloneKey;

    @Override
    public void onInitializeClient() {
        spawnCloneKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.clones_on_phisics.spawn_clone",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                KeyMapping.Category.GAMEPLAY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (spawnCloneKey.consumeClick()) {
                if (client.player != null && client.player.connection != null) {
                    client.player.connection.sendCommand("spawnclone");
                }
            }
        });
    }
}
