package com.example;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.ArmorStandEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
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
            CommandDispatcher<ServerCommandSource> dispatcher,
            CommandRegistryAccess registryAccess,
            CommandManager.RegistrationEnvironment environment
    ) {
        dispatcher.register(
            CommandManager.literal("spawnclone")
                .executes(context -> {
                    ServerCommandSource source = context.getSource();
                    if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
                        source.sendFeedback(() -> Text.literal("Только игрок!"), false);
                        return 0;
                    }

                    var world = player.getServerWorld();
                    var clone = new ArmorStandEntity(EntityType.ARMOR_STAND, world);

                    Vec3d look = player.getRotationVec(1.0f);
                    Vec3d pos = player.getPos().add(look.x * 1.5, 0.0, look.z * 1.5);

                    clone.refreshPositionAndAngles(pos.x, pos.y, pos.z, player.getYaw(), player.getPitch());
                    clone.setVelocity(look.x * 0.35, 0.15, look.z * 0.35);
                    clone.setCustomName(Text.literal("Клон " + player.getName().getString()));
                    clone.setCustomNameVisible(true);

                    world.spawnEntity(clone);
                    ACTIVE_CLONES.put(clone.getUuid(), 100);

                    world.spawnParticles(ParticleTypes.CLOUD, pos.x, pos.y + 1.0, pos.z, 15, 0.3, 0.5, 0.3, 0.02);
                    source.sendFeedback(() -> Text.literal("Клон создан!"), false);
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
                for (var world : server.getWorlds()) {
                    var entity = world.getEntity(id);
                    if (entity != null) {
                        world.spawnParticles(ParticleTypes.POOF, entity.getX(), entity.getY() + 1.0, entity.getZ(), 10, 0.25, 0.4, 0.25, 0.02);
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
