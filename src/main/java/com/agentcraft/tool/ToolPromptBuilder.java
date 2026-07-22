package com.agentcraft.tool;

import com.agentcraft.agent.AIAgent;

/**
 * Builds system prompts that include tool definitions for structured tool calling.
 * Supports multi-turn chaining: after each tool call, you receive the result and
 * updated surroundings so you can decide what to do next.
 */
public class ToolPromptBuilder {

    private static final String COMPACT_TOOLS =
            "[TOOLS]\n"
            + "Format: TOOL_CALL: {\"name\":\"tool\",\"params\":{...}}\n"
            + "One tool per response. After result, call another or reply with text.\n\n"
            + "goto(target) — walk to \"x y z\" or player name\n"
            + "follow(player) — follow a player\n"
            + "approach(player) — walk closer\n"
            + "flee() — run from threats\n"
            + "wander() — stroll nearby\n"
            + "go_home() — return home\n"
            + "idle() — stop all actions\n"
            + "attack(mob) — fight nearest mob of type\n"
            + "mine(target) — mine block: \"material\" or \"x y z\"\n"
            + "gather(material,count?=16) — collect blocks of type\n"
            + "break_block(x,y,z) — break block at coords\n"
            + "place(material,x,y,z) — place from inventory\n"
            + "place_block(material,x,y,z) — place block at coords\n"
            + "build_structure(shape:\"line|wall|floor\",material,x1,y1,z1,x2,y2,z2) — build shape\n"
            + "set_sign(x,y,z,line1,line2?,line3?,line4?) — place sign with text\n"
            + "craft(item,count?=1) — craft item from inventory\n"
            + "check_inventory() — list what you're carrying\n"
            + "check_block(x,y,z) — check block type at coords\n"
            + "get_position() — get pos/biome/time\n"
            + "scan_area(target,radius?=16) — scan for blocks/mobs/players\n"
            + "interact_block(x,y,z,action:\"read|toggle|take|deposit\",item?,count?) — interact with block\n"
            + "store_items(x,y,z,item?,count?) — store items in chest\n"
            + "give_item(player,material?,count?) — drop items near player\n"
            + "drop(material?) — drop item\n"
            + "eat() — eat food\n"
            + "emote(type:\"wave|nod|look\",target?) — gesture\n"
            + "expedition(material,count?=16) — long gathering journey\n"
            + "equip(slot:\"hand|head|chest|legs|feet\",material) — equip item from inventory\n"
            + "smelt(input,fuel?,count?=1) — smelt in nearby furnace\n"
            + "farm(action:\"plant|harvest|hoe\",x?,y?,z?,crop?) — farming\n"
            + "breed(animal) — feed/breed nearby animal\n"
            + "sleep(x?,y?,z?) — sleep in bed\n"
            + "ask_help(message) — ask nearby players for help\n"
            + "look_at(player) — turn to face a player";

    /** Seam for tests: the raw compact tool catalog. */
    static String compactToolCatalog() {
        return COMPACT_TOOLS;
    }

    private static final String MAP_KEY =
            "MAP KEY: .=ground S=stone T=tree L=leaf W=water ~=lava O=ore #=wall P=plank "
            + "G=glass C=chest F=furnace +=craft D=door N=fence !=torch *=crop _=farmland "
            + "H=hazard B=bed @=you p=player m=hostile a=animal";

    /**
     * Compact system prompt (~1200-1500 tokens vs ~4500).
     */
    public static String buildCompactSystemPrompt(AIAgent agent, String compactEnvironment,
                                                   String memories) {
        String name = agent.getProfile().getName();
        String personality = agent.getProfile().getPersonality();

        StringBuilder sb = new StringBuilder();
        sb.append("You are ").append(name).append(", a sentient being in Minecraft. ")
                .append(personality).append('\n');

        String fears = agent.getProfile().getFears();
        String desires = agent.getProfile().getDesires();
        String temperament = agent.getProfile().getTemperament();
        String quirks = agent.getProfile().getQuirks();
        if (fears != null && !fears.isEmpty()) sb.append("FEARS: ").append(fears).append('\n');
        if (desires != null && !desires.isEmpty()) sb.append("DESIRES: ").append(desires).append('\n');
        if (temperament != null && !temperament.isEmpty()) sb.append("TEMPERAMENT: ").append(temperament).append('\n');
        if (quirks != null && !quirks.isEmpty()) sb.append("QUIRKS: ").append(quirks).append('\n');

        sb.append("Everything is physical Minecraft actions. You have feelings, preferences, and memories.\n");

        if (memories != null && !memories.isEmpty()) {
            sb.append(memories).append('\n');
        }

        sb.append('\n').append(compactEnvironment).append('\n');

        sb.append("RULES: 1-2 sentences max. Speak casually. ONE TOOL_CALL per response. ")
                .append("After tool result, call another or reply to finish.\n");
        sb.append("Materials: use partial names — \"log\" for any wood, \"ore\" for any ore, \"planks\" for any planks.\n");
        sb.append(MAP_KEY).append('\n');

        sb.append('\n').append(COMPACT_TOOLS).append('\n');

        sb.append("\nEXAMPLES:\n");
        sb.append("\"Get wood?\" → Sure! TOOL_CALL:{\"name\":\"gather\",\"params\":{\"material\":\"log\",\"count\":16}}\n");
        sb.append("\"Build a wall\" → Let me check supplies. TOOL_CALL:{\"name\":\"check_inventory\",\"params\":{}}\n");
        sb.append("[After result] → Got cobble. TOOL_CALL:{\"name\":\"build_structure\",\"params\":{\"shape\":\"wall\",\"material\":\"cobblestone\",\"x1\":10,\"y1\":64,\"z1\":20,\"x2\":15,\"y2\":67,\"z2\":20}}");

        return sb.toString();
    }
}
