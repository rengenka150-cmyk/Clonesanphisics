package com.example;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Clones_on_phisics implements ModInitializer {
    public static final String MOD_ID = "clones_on_phisics";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final Map<UUID, Integer> ACTIVE_CLONES = new HashMap<>();

    @Override
    public void onInitialize() {
        LOGGER.info("Clones on Physics loaded!");
        CommandRegistrationCallback.EVENT.register(this::registerCommands);
        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);
    }

    private void registerCommands(
            CommandDispatcher<CommandSourceStack> dispatcher,
            CommandBuildContext registryAccess,
            Commands.CommandSelection environment
    ) {
        dispatcher.register(
            Commands.literal("spawnclone")
                .executes(context -> {
                    CommandSourceStack source = context.getSource();
                    if (!(source.getEntity() instanceof ServerPlayer player)) {
                        source.sendFailure(Component.literal("Только игрок!"));
                        return 0;
                    }

                    ServerLevel level = player.level();
                    ArmorStand clone = new ArmorStand(EntityType.ARMOR_STAND, level);

                    Vec3 look = player.getLookAngle();
                    Vec3 pos = player.position().add(look.x * 1.5, 0.0, look.z * 1.5);

                    clone.snapTo(pos.x, pos.y, pos.z, player.getYRot(), player.getXRot());
                    clone.setDeltaMovement(look.x * 0.35, 0.15, look.z * 0.35);
                    clone.setCustomName(Component.literal("Клон " + player.getName().getString()));
                    clone.setCustomNameVisible(true);

                    level.addFreshEntity(clone);
                    ACTIVE_CLONES.put(clone.getUUID(), 100);

                    level.sendParticles(ParticleTypes.CLOUD, pos.x, pos.y + 1.0, pos.z, 15, 0.3, 0.5, 0.3, 0.02);
                    source.sendSuccess(() -> Component.literal("Клон создан!"), false);
                    return 1;
                })
        );
    }

    private void onServerTick(MinecraftServer server) {
        if (ACTIVE_CLONES.isEmpty()) return;

        List<UUID> ids = new ArrayList<>(ACTIVE_CLONES.keySet());
        for (UUID id : ids) {
            int ticks = ACTIVE_CLONES.getOrDefault(id, 0) - 1;
            if (ticks <= 0) {
                for (ServerLevel level : server.getAllLevels()) {
                    Entity entity = level.getEntity(id);
                    if (entity != null) {
                        level.sendParticles(ParticleTypes.POOF, entity.getX(), entity.getY() + 1.0, entity.getZ(), 10, 0.25, 0.4, 0.25, 0.02);
                        entity.discard();
                    }
                }
                ACTIVE_CLONES.remove(id);
            } else {
                ACTIVE_CLONES.put(id, ticks);
            }
        }
    }
}
