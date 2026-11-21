package com.example;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public class Clones_on_phisics implements ModInitializer {
    public static final String MOD_ID = "clones_on_phisics";

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playC2S().register(ResetFallPayload.TYPE, ResetFallPayload.STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(ResetFallPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (player != null) {
                    player.fallDistance = 0.0f;
                }
            });
        });

        ServerTickEvents.END_WORLD_TICK.register(CloneLoopManager::serverTick);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("spawnclone")
                    .executes(context -> {
                        if (context.getSource().getEntity() instanceof ServerPlayer player) {
                            CloneLoopManager.spawnClone(player);
                            player.sendSystemMessage(Component.literal("§aКлон создан!"));
                        }
                        return 1;
                    }));

            dispatcher.register(Commands.literal("cloop")
                    .then(Commands.argument("action", StringArgumentType.word())
                            .executes(context -> {
                                String action = StringArgumentType.getString(context, "action");
                                ServerPlayer player = context.getSource().getPlayer();

                                switch (action.toLowerCase()) {
                                    case "rec" -> {
                                        CloneLoopManager.startRecording();
                                        if (player != null) {
                                            player.sendSystemMessage(Component.literal("§c● Запись маршрута началась!"));
                                        }
                                    }
                                    case "play" -> {
                                        CloneLoopManager.startPlaying();
                                        if (player != null) {
                                            player.sendSystemMessage(Component.literal("§a▶ Клоны запущены!"));
                                        }
                                    }
                                    case "pause" -> {
                                        CloneLoopManager.pausePlaying();
                                        if (player != null) {
                                            player.sendSystemMessage(Component.literal("§e❚❚ Пауза!"));
                                        }
                                    }
                                    case "stop" -> {
                                        CloneLoopManager.stopAll();
                                        if (player != null) {
                                            player.sendSystemMessage(Component.literal("§7■ Стоп!"));
                                        }
                                    }
                                    case "clear" -> {
                                        if (player != null && player.level() instanceof ServerLevel serverLevel) {
                                            CloneLoopManager.clearClones(serverLevel);
                                            player.sendSystemMessage(Component.literal("§7✖ Все клоны удалены!"));
                                        }
                                    }
                                    default -> {
                                        if (player != null) {
                                            player.sendSystemMessage(Component.literal("§cНеизвестное действие: " + action));
                                        }
                                    }
                                }
                                return 1;
                            })));
        });
    }
}
