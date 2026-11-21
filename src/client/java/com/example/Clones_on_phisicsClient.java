package com.example;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public class Clones_on_phisicsClient implements ClientModInitializer {
    private static final int MAX_AIR_JUMPS = 2; // 1 обычный + 2 в воздухе = тройной прыжок
    private static final int MIN_AIR_TICKS_BEFORE_AIR_JUMP = 3;
    private static final int SLIDE_DURATION_TICKS = 14;
    private static final int SLIDE_COOLDOWN_TICKS = 20;

    private static final int MAX_WALL_CLIMB_TICKS = 42;
    private static final double WALL_CLIMB_SPEED = 0.21;
    private static final double WALL_SLIDE_MAX_FALL_SPEED = -0.08;

    private static final double ROPE_MAX_DISTANCE = 26.0;
    private static final int HOOK_MAX_HOLD_TICKS = 45;
    private static final int HOOK_END_HOLD_TICKS = 6; // Короткое удержание дистанции у цели (~0.3 сек)
    private static final double HOOK_TOP_OFFSET_Y = 1.6;
    private static final int SWING_MAX_HOLD_TICKS = 160;
    private static final int ROPE_COOLDOWN_TICKS = 8;

    private static KeyMapping spawnCloneKey;
    private static KeyMapping slideKey;
    private static KeyMapping hookPullKey;
    private static KeyMapping webSwingKey;

    private int airJumpsUsed = 0;
    private int ticksInAir = 0;
    private boolean jumpWasDown = false;

    private int slideTicks = 0;
    private int slideCooldown = 0;
    private Vec3 slideDirection = Vec3.ZERO;

    private int wallClimbTicks = 0;

    private boolean hookActive = false;
    private Vec3 hookAnchor = null;
    private int hookHoldTicks = 0;
    private int hookEndHoldRemaining = 0;
    private double hookHoldLength = 0.0;

    private boolean swingActive = false;
    private Vec3 swingAnchor = null;
    private double swingRopeLength = 0.0;
    private int swingHoldTicks = 0;

    private int ropeCooldown = 0;

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

        hookPullKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.clones_on_phisics.hook_pull",
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(),
                KeyMapping.Category.MOVEMENT
        ));

        webSwingKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.clones_on_phisics.web_swing",
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

            handleRopes(client);
            handleMultiJumpAndWallMechanics(client);
            handleSlide(client);
        });
    }

    private void resetServerFallDistance(LocalPlayer player) {
        player.fallDistance = 0.0f;
        if (ClientPlayNetworking.canSend(ResetFallPayload.TYPE)) {
            ClientPlayNetworking.send(new ResetFallPayload());
        }
    }

    private void resetHookState() {
        hookActive = false;
        hookAnchor = null;
        hookHoldTicks = 0;
        hookEndHoldRemaining = 0;
        hookHoldLength = 0.0;
    }

    private void resetSwingState() {
        swingActive = false;
        swingAnchor = null;
        swingRopeLength = 0.0;
        swingHoldTicks = 0;
    }

    private Vec3 raycastBlock(Minecraft client, LocalPlayer player, double maxDistance) {
        if (client.level == null) {
            return null;
        }
        Vec3 eyePos = player.getEyePosition(1.0f);
        Vec3 lookVec = player.getLookAngle();
        Vec3 endPos = eyePos.add(lookVec.scale(maxDistance));

        BlockHitResult hit = client.level.clip(new ClipContext(
                eyePos,
                endPos,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        ));

        return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : null;
    }

    private void drawRopeParticles(Minecraft client, Vec3 from, Vec3 to) {
        if (client.level == null) {
            return;
        }
        double distance = from.distanceTo(to);
        int steps = Math.min((int) (distance * 2.5), 45);
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / (steps + 1);
            Vec3 p = from.lerp(to, t);
            client.level.addParticle(ParticleTypes.CRIT, p.x, p.y, p.z, 0.0, 0.0, 0.0);
        }
    }

    private void handleRopes(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null) {
            resetHookState();
            resetSwingState();
            ropeCooldown = 0;
            return;
        }

        if (ropeCooldown > 0) {
            ropeCooldown--;
        }

        boolean hookDown = hookPullKey.isDown();
        boolean swingDown = webSwingKey.isDown();

        // 1. ХУК-ПУШКА: тянет к точке сверху блока, затем коротко держит дистанцию и отпускает
        if (!hookActive && !swingActive && hookDown && ropeCooldown == 0) {
            Vec3 hitPos = raycastBlock(client, player, ROPE_MAX_DISTANCE);
            if (hitPos != null) {
                hookAnchor = hitPos.add(0.0, HOOK_TOP_OFFSET_Y, 0.0);
                hookActive = true;
                hookHoldTicks = 0;
                hookEndHoldRemaining = 0;
                hookHoldLength = 0.0;
            }
        }

        if (hookActive) {
            boolean expiredBeforeEndHold = hookEndHoldRemaining == 0 && hookHoldTicks >= HOOK_MAX_HOLD_TICKS;
            boolean expiredEndHold = hookEndHoldRemaining > 0 && hookEndHoldRemaining <= 1;

            if (!hookDown || hookAnchor == null || expiredBeforeEndHold || expiredEndHold) {
                resetHookState();
                ropeCooldown = ROPE_COOLDOWN_TICKS;
            } else {
                hookHoldTicks++;
                Vec3 playerCenter = player.position().add(0.0, player.getBbHeight() * 0.6, 0.0);
                Vec3 toAnchor = hookAnchor.subtract(playerCenter);
                double distance = toAnchor.length();

                if (hookEndHoldRemaining == 0 && distance > 1.65) {
                    Vec3 dir = toAnchor.normalize();
                    Vec3 nextVel = player.getDeltaMovement().add(dir.scale(0.165));
                    if (nextVel.length() > 1.2) {
                        nextVel = nextVel.normalize().scale(1.2);
                    }
                    double liftBoost = distance < 4.5 ? 0.075 : 0.04;
                    player.setDeltaMovement(nextVel.x, nextVel.y + liftBoost, nextVel.z);
                } else {
                    if (hookEndHoldRemaining == 0) {
                        hookEndHoldRemaining = HOOK_END_HOLD_TICKS;
                        hookHoldLength = Math.max(distance, 1.1);
                    } else {
                        hookEndHoldRemaining--;
                    }

                    Vec3 vel = player.getDeltaMovement().scale(0.65);
                    if (distance > hookHoldLength && distance > 0.001) {
                        Vec3 fromAnchor = playerCenter.subtract(hookAnchor);
                        Vec3 radialDir = fromAnchor.normalize();
                        double radialSpeed = vel.dot(radialDir);
                        if (radialSpeed > 0.0) {
                            vel = vel.subtract(radialDir.scale(radialSpeed));
                        }
                        double stretch = distance - hookHoldLength;
                        vel = vel.subtract(radialDir.scale(Math.min(stretch * 0.35, 0.3)));
                    }
                    player.setDeltaMovement(vel);
                }

                resetServerFallDistance(player);
                airJumpsUsed = 0;
                wallClimbTicks = 0;
                drawRopeParticles(client, playerCenter, hookAnchor);
            }
        }

        // 2. ПАУТИНА-МАЯТНИК: дольше держит и сохраняет длину верёвки
        if (!swingActive && !hookActive && swingDown && ropeCooldown == 0) {
            Vec3 hitPos = raycastBlock(client, player, ROPE_MAX_DISTANCE);
            if (hitPos != null) {
                Vec3 playerCenter = player.position().add(0.0, player.getBbHeight() * 0.6, 0.0);
                double initialDist = playerCenter.distanceTo(hitPos);
                if (initialDist > 2.0) {
                    swingAnchor = hitPos;
                    swingRopeLength = initialDist;
                    swingActive = true;
                    swingHoldTicks = 0;
                }
            }
        }

        if (swingActive) {
            if (!swingDown || swingHoldTicks >= SWING_MAX_HOLD_TICKS || swingAnchor == null) {
                resetSwingState();
                ropeCooldown = ROPE_COOLDOWN_TICKS;
            } else {
                swingHoldTicks++;
                Vec3 playerCenter = player.position().add(0.0, player.getBbHeight() * 0.6, 0.0);
                Vec3 fromAnchor = playerCenter.subtract(swingAnchor);
                double currentDist = fromAnchor.length();

                Vec3 vel = player.getDeltaMovement();

                Vec3 look = player.getLookAngle();
                Vec3 flatLook = new Vec3(look.x, 0.0, look.z);
                if (flatLook.lengthSqr() > 1.0E-4) {
                    vel = vel.add(flatLook.normalize().scale(0.025));
                }

                if (currentDist > swingRopeLength && currentDist > 0.001) {
                    Vec3 radialDir = fromAnchor.normalize();
                    double radialSpeed = vel.dot(radialDir);
                    if (radialSpeed > 0.0) {
                        vel = vel.subtract(radialDir.scale(radialSpeed));
                    }
                    double stretch = currentDist - swingRopeLength;
                    vel = vel.subtract(radialDir.scale(Math.min(stretch * 0.35, 0.4)));
                }

                player.setDeltaMovement(vel);
                resetServerFallDistance(player);
                airJumpsUsed = 0;
                wallClimbTicks = 0;
                drawRopeParticles(client, playerCenter, swingAnchor);
            }
        }
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

        if (player.horizontalCollision && shiftDown && vel.y < 0.0) {
            double clampedY = Math.max(vel.y, WALL_SLIDE_MAX_FALL_SPEED);
            player.setDeltaMovement(vel.x, clampedY, vel.z);
            player.fallDistance = 0.0f;

            if (ticksInAir % 4 == 0) {
                resetServerFallDistance(player);
            }

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

        if (player.horizontalCollision && jumpDown && wallClimbTicks < MAX_WALL_CLIMB_TICKS) {
            wallClimbTicks++;
            Vec3 look = player.getLookAngle();
            Vec3 flatLook = new Vec3(look.x, 0.0, look.z);
            double pushX = vel.x;
            double pushZ = vel.z;
            if (flatLook.lengthSqr() > 1.0E-4) {
                Vec3 dir = flatLook.normalize();
                pushX += dir.x * 0.04;
                pushZ += dir.z * 0.04;
            }
            player.setDeltaMovement(pushX, WALL_CLIMB_SPEED, pushZ);
            player.fallDistance = 0.0f;
            if (wallClimbTicks % 4 == 0) {
                resetServerFallDistance(player);
            }
            return;
        }

        if (jumpJustPressed && ticksInAir >= MIN_AIR_TICKS_BEFORE_AIR_JUMP && airJumpsUsed < MAX_AIR_JUMPS) {
            airJumpsUsed++;

            double jumpVelocity = Math.max(vel.y, 0.46);
            player.setDeltaMovement(vel.x, jumpVelocity, vel.z);
            resetServerFallDistance(player);

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
