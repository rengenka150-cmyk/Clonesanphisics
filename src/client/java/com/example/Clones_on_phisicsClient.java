package com.example;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public class Clones_on_phisicsClient implements ClientModInitializer {
    private static final int MAX_AIR_JUMPS = 2; // 1 обычный + 2 в воздухе = тройной прыжок
    private static final int MIN_AIR_TICKS_BEFORE_AIR_JUMP = 3;
    private static final int SLIDE_DURATION_TICKS = 14;
    private static final int SLIDE_COOLDOWN_TICKS = 20;
    private static final int MAX_WALL_CLIMB_TICKS = 30; // ~1.5 секунды карабкания вверх
    private static final double WALL_SLIDE_MAX_FALL_SPEED = -0.08; // Скорость медленного спуска на Shift

    private static KeyMapping spawnCloneKey;
    private static KeyMapping slideKey;

    private int airJumpsUsed = 0;
    private int ticksInAir = 0;
    private boolean jumpWasDown = false;

    private int slideTicks = 0;
    private int slideCooldown = 0;
    private Vec3 slideDirection = Vec3.ZERO;

    private int wallClimbTicks = 0;

    @Override
    public void onInitializeClient() {
        spawnCloneKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.clones_on_phisics.spawn_clone",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                KeyMapping.Category.GAMEPLAY
        ));

        slideKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.clones_on_phisics.slide",
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(),
                KeyMapping.Category.MOVEMENT
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (spawnCloneKey.consumeClick()) {
                if (client.player != null && client.player.connection != null) {
                    client.player.connection.sendCommand("spawnclone");
                }
            }

            handleMultiJumpAndWallMechanics(client);
            handleSlide(client);
        });
    }

    private void handleMultiJumpAndWallMechanics(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null) {
            airJumpsUsed = 0;
            ticksInAir = 0;
            wallClimbTicks = 0;
            jumpWasDown = false;
            return;
        }

        boolean jumpDown = client.options.keyJump.isDown();
        boolean shiftDown = client.options.keyShift.isDown();
        boolean jumpJustPressed = jumpDown && !jumpWasDown;
        jumpWasDown = jumpDown;

        if (player.onGround() || player.isInWater() || player.onClimbable()) {
            airJumpsUsed = 0;
            ticksInAir = 0;
            wallClimbTicks = 0;
            return;
        }

        ticksInAir++;
        Vec3 vel = player.getDeltaMovement();

        // 1. Медленный спуск по стене при зажатом Shift
        if (player.horizontalCollision && shiftDown && vel.y < 0.0) {
            double clampedY = Math.max(vel.y, WALL_SLIDE_MAX_FALL_SPEED);
            player.setDeltaMovement(vel.x, clampedY, vel.z);
            player.fallDistance = 0.0f;

            if (client.level != null && ticksInAir % 3 == 0) {
                client.level.addParticle(
                        ParticleTypes.CLOUD,
                        player.getX(),
                        player.getY() + 0.5,
                        player.getZ(),
                        0.0,
                        0.01,
                        0.0
                );
            }
            return;
        }

        // 2. Лазание по стене вверх: упёрся в стену в воздухе и держишь прыжок
        if (player.horizontalCollision && jumpDown && wallClimbTicks < MAX_WALL_CLIMB_TICKS) {
            wallClimbTicks++;
            player.setDeltaMovement(vel.x, 0.22, vel.z);
            player.fallDistance = 0.0f;
            return;
        }

        // 3. Воздушные прыжки
        if (jumpJustPressed && ticksInAir >= MIN_AIR_TICKS_BEFORE_AIR_JUMP && airJumpsUsed < MAX_AIR_JUMPS) {
            airJumpsUsed++;

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

    private void handleSlide(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null) {
            slideTicks = 0;
            slideCooldown = 0;
            return;
        }

        if (slideCooldown > 0) {
            slideCooldown--;
        }

        while (slideKey.consumeClick()) {
            if (slideTicks == 0 && slideCooldown == 0 && player.onGround() && !player.isInWater()) {
                Vec3 look = player.getLookAngle();
                Vec3 flatLook = new Vec3(look.x, 0.0, look.z);
                if (flatLook.lengthSqr() > 1.0E-4) {
                    slideDirection = flatLook.normalize();
                    slideTicks = SLIDE_DURATION_TICKS;
                    slideCooldown = SLIDE_COOLDOWN_TICKS;
                }
            }
        }

        if (slideTicks > 0) {
            slideTicks--;
            double speed = 0.65 + (0.25 * ((double) slideTicks / SLIDE_DURATION_TICKS));
            Vec3 currentVel = player.getDeltaMovement();
            player.setDeltaMovement(
                    slideDirection.x * speed,
                    currentVel.y,
                    slideDirection.z * speed
            );
            player.setPose(Pose.CROUCHING);

            if (client.level != null) {
                client.level.addParticle(
                        ParticleTypes.CLOUD,
                        player.getX(),
                        player.getY() + 0.1,
                        player.getZ(),
                        -slideDirection.x * 0.05,
                        0.02,
                        -slideDirection.z * 0.05
                );
            }
        }
    }
}
