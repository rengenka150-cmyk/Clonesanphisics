package com.example;

import net.minecraft.resources.Identifier;

public final class Identifier {
    private Identifier() {}

    public static Identifier of(String path) {
        return Identifier.fromNamespaceAndPath(Clones_on_phisics.MOD_ID, path);
    }

    public static Identifier of(String namespace, String path) {
        return Identifier.fromNamespaceAndPath(namespace, path);
    }
}
