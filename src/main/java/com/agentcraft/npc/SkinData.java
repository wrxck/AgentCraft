package com.agentcraft.npc;

public record SkinData(String value, String signature) {

    public static final SkinData EMPTY = new SkinData("", "");

    public boolean isEmpty() {
        return value.isEmpty() || signature.isEmpty();
    }
}
