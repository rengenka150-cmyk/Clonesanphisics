package com.example;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
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

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("spawnclone")
                    .executes(context -> {
                        // Сюда на следующем шаге подключим спавн нашего физического NPC-клона
                        return 1;
                    }));

            dispatcher.register(Commands.literal("clone")
                    .then(Commands.argument("action", StringArgumentType.word())
                            .executes(context -> {
                                String action = StringArgumentType.getString(context, "action");
                                // Обработка rec / play / pause / stop / clear подключится к менеджеру клонов
                                return 1;
                            })));
        });
    }
}
