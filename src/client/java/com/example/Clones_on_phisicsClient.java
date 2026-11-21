package com.example;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public class Clones_on_phisicsClient implements ClientModInitializer {
    private static final int MAX_AIR_JUMPS = 2; // 1 обычный + 2 в воздухе = тройной прыжок
    private static KeyMapping spawnCloneKey;

    private int airJumpsUsed = 0;
    private boolean jumpWasDown = false;

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

            handleMultiJump(client);
        });
    }

    private void handleMultiJump(net.minecraft.client.Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null) {
            airJumpsUsed = 0;
            jumpWasDown = false;
            return;
        }

        boolean jumpDown = client.options.keyJump.isDown();
        boolean jumpJustPressed = jumpDown && !jumpWasDown;
        jumpWasDown = jumpDown;

        if (player.onGround() || player.isInWater() || player.onClimbable()) {
            airJumpsUsed = 0;
            return;
        }

        if (jumpJustPressed && airJumpsUsed < MAX_AIR_JUMPS) {
            airJumpsUsed++;

            Vec3 vel = player.getDeltaMovement();
            double jumpVelocity = Math.max(vel.y, 0.46);
            player.setDeltaMovement(vel.x, jumpVelocity, vel.z);
            player.fallDistance = 0.0f;

            if (client.level != null) {
                client.level.addParticle(
                        ParticleTypes.CLOUD,
                        player.getX(),
                        player.getY() + 0.05,
                        player.getZ(),
                        0.0,
                        -0.02,
                        0.0
                );
            }
        }
    }
}
