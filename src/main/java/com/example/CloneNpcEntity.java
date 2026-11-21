package com.example;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class CloneNpcEntity extends Villager {
    private int playbackIndex = 0;

    public CloneNpcEntity(EntityType<? extends Villager> entityType, Level level) {
        super(entityType, level);
        this.setCustomName(Component.literal("Клон"));
        this.setCustomNameVisible(true);
    }

    public void resetPlayback() {
        this.playbackIndex = 0;
    }

    @Override
    protected void registerGoals() {
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    @Override
    public boolean isPushable() {
        return true;
    }

    @Override
    protected void doPush(Entity entity) {
        super.doPush(entity);
        if (!this.level().isClientSide() && entity instanceof LivingEntity) {
            Vec3 away = this.position().subtract(entity.position());
            Vec3 flatAway = new Vec3(away.x, 0.0, away.z);
            if (flatAway.lengthSqr() > 1.0E-4) {
                Vec3 dir = flatAway.normalize();
                this.push(dir.x * 0.25, 0.05, dir.z * 0.25);
            }
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide() || !(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        List<Player> nearbyPlayers = serverLevel.getEntitiesOfClass(
                Player.class,
                this.getBoundingBox().inflate(0.25)
        );
        for (Player player : nearbyPlayers) {
            Vec3 diff = this.position().subtract(player.position());
            Vec3 flatDiff = new Vec3(diff.x, 0.0, diff.z);
            if (flatDiff.lengthSqr() > 1.0E-4) {
                Vec3 dir = flatDiff.normalize();
                this.push(dir.x * 0.14, 0.0, dir.z * 0.14);
                player.push(-dir.x * 0.08, 0.0, -dir.z * 0.08);
            }
        }

        if (CloneLoopManager.isPlaying()) {
            List<CloneLoopManager.RecordedFrame> frames = CloneLoopManager.getFrames();
            if (!frames.isEmpty()) {
                if (playbackIndex >= frames.size()) {
                    playbackIndex = 0;
                }
                CloneLoopManager.RecordedFrame frame = frames.get(playbackIndex++);
                double dx = frame.x() - this.getX();
                double dz = frame.z() - this.getZ();
                double distSq = dx * dx + dz * dz;

                if (distSq > 0.02) {
                    Vec3 currentVel = this.getDeltaMovement();
                    double pullX = Math.max(-0.35, Math.min(0.35, dx * 0.18));
                    double pullZ = Math.max(-0.35, Math.min(0.35, dz * 0.18));
                    this.setDeltaMovement(
                            currentVel.x * 0.65 + pullX * 0.35,
                            currentVel.y,
                            currentVel.z * 0.65 + pullZ * 0.35
                    );
                }

                this.setYRot(frame.yRot());
                this.setYHeadRot(frame.yRot());
                this.setXRot(frame.xRot());
            }
        }
    }
}
