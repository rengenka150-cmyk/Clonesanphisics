package com.example;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

public class CloneLoopManager {
    public record RecordedFrame(double x, double y, double z, float yRot, float xRot) {}

    private static final List<RecordedFrame> FRAMES = new ArrayList<>();
    private static final List<CloneNpcEntity> SPAWNED_CLONES = new ArrayList<>();

    private static boolean recording = false;
    private static boolean playing = false;

    public static boolean isRecording() {
        return recording;
    }

    public static boolean isPlaying() {
        return playing;
    }

    public static List<RecordedFrame> getFrames() {
        return FRAMES;
    }

    public static void startRecording() {
        FRAMES.clear();
        recording = true;
        playing = false;
    }

    public static void startPlaying() {
        recording = false;
        playing = true;
        for (CloneNpcEntity clone : SPAWNED_CLONES) {
            if (clone.isAlive()) {
                clone.resetPlayback();
            }
        }
    }

    public static void pausePlaying() {
        playing = false;
    }

    public static void stopAll() {
        recording = false;
        playing = false;
        for (CloneNpcEntity clone : SPAWNED_CLONES) {
            if (clone.isAlive()) {
                clone.resetPlayback();
            }
        }
    }

    public static void clearClones(ServerLevel level) {
        recording = false;
        playing = false;
        for (CloneNpcEntity clone : SPAWNED_CLONES) {
            if (clone.isAlive()) {
                clone.discard();
            }
        }
        SPAWNED_CLONES.clear();

        for (CloneNpcEntity entity : level.getEntitiesOfClass(
                CloneNpcEntity.class,
                new AABB(-30000, -128, -30000, 30000, 512, 30000)
        )) {
            entity.discard();
        }
    }

    public static void recordPlayerTick(ServerPlayer player) {
        if (!recording || player == null) {
            return;
        }
        FRAMES.add(new RecordedFrame(
                player.getX(),
                player.getY(),
                player.getZ(),
                player.getYRot(),
                player.getXRot()
        ));
    }

    public static CloneNpcEntity spawnClone(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return null;
        }
        CloneNpcEntity clone = new CloneNpcEntity(EntityType.VILLAGER, level);
        clone.setPos(player.getX(), player.getY(), player.getZ());
        clone.setYRot(player.getYRot());
        clone.setXRot(player.getXRot());

        level.addFreshEntity(clone);
        SPAWNED_CLONES.add(clone);
        return clone;
    }

    public static void serverTick(ServerLevel level) {
        SPAWNED_CLONES.removeIf(c -> !c.isAlive());
        for (ServerPlayer player : level.players()) {
            recordPlayerTick(player);
        }
    }
}
