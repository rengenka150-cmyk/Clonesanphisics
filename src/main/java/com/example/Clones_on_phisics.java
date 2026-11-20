package com.example;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.ArmorStandEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Clones_on_phisics implements ModInitializer {
    public static final String MOD_ID = "clones_on_phisics";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final Map<UUID, Integer> ACTIVE_CLONES = new ConcurrentHashMap<>();

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
                        source.sendFeedback(() -> Text.literal("Команду может вызвать только игрок!"), false);
                        return 0;
                    }

                    ServerWorld world = player.getServerWorld();
                    ArmorStandEntity clone = new ArmorStandEntity(EntityType.ARMOR_STAND, world);

                    Vec3d look = player.getRotationVec(1.0f);
                    Vec3d spawnPos = player.getPos().add(look.x * 1.5, 0.0, look.z * 1.5);

                    clone.refreshPositionAndAngles(
                        spawnPos.x,
                        spawnPos.y,
                        spawnPos.z,
                        player.getYaw(),
                        player.getPitch()
                    );
                    clone.setVelocity(look.x * 0.35, 0.15, look.z * 0.35);
                    clone.setCustomName(Text.literal("Клон " + player.getName().getString()));
                    clone.setCustomNameVisible(true);

                    world.spawnEntity(clone);
                    ACTIVE_CLONES.put(clone.getUuid(), 100);

                    world.spawnParticles(
                        ParticleTypes.CLOUD,
                        spawnPos.x,
                        spawnPos.y + 1.0,
                        spawnPos.z,
                        15,
                        0.3,
                        0.5,
                        0.3,
                        0.02
                    );

                    source.sendFeedback(() -> Text.literal("Физический клон создан!"), false);
                    return 1;
                })
        );
    }

    private void onServerTick(MinecraftServer server) {
        Iterator> iterator = ACTIVE_CLONES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Integer> entry = iterator.next();
            int ticksLeft = entry.getValue() - 1;

            if (ticksLeft <= 0) {
                for (ServerWorld world : server.getWorlds()) {
                    Entity entity = world.getEntity(entry.getKey());
                    if (entity != null) {
                        world.spawnParticles(
                            ParticleTypes.POOF,
                            entity.getX(),
                            entity.getY() + 1.0,
                            entity.getZ(),
                            10,
                            0.25,
                            0.4,
                            0.25,
                            0.02
                        );
                        entity.discard();
                    }
                }
                iterator.remove();
            } else {
                entry.setValue(ticksLeft);
            }
        }
    }
}
