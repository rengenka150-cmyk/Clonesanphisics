package com.example;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ResetFallPayload() implements CustomPacketPayload {
    public static final Type<ResetFallPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(Clones_on_phisics.MOD_ID, "reset_fall")
    );

    public static final StreamCodec<FriendlyByteBuf, ResetFallPayload> STREAM_CODEC =
            StreamCodec.unit(new ResetFallPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
