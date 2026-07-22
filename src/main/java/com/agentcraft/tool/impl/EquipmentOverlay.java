package com.agentcraft.tool.impl;

import com.agentcraft.npc.AgentEquipment;
import com.comphenix.protocol.wrappers.EnumWrappers;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;
import org.bukkit.inventory.ItemStack;

/**
 * An {@link AgentEquipment} view that layers items the agent equipped at
 * runtime (via the equip tool) over its base equipment set. Installing this on
 * the {@link com.agentcraft.npc.FakePlayer} via {@code setEquipment} means
 * respawns and late-joining players see the equipped items too, instead of the
 * equip being a per-viewer visual that silently voided the item.
 */
final class EquipmentOverlay extends AgentEquipment {

    private final AgentEquipment base;
    private final Map<EnumWrappers.ItemSlot, ItemStack> overrides =
            new EnumMap<>(EnumWrappers.ItemSlot.class);

    EquipmentOverlay(AgentEquipment base) {
        this.base = base;
    }

    /**
     * Sets the item for a slot.
     *
     * @return the item previously equipped in that slot via the overlay, or
     *         {@code null} if none (base equipment is not returned — it never
     *         came from the agent's inventory)
     */
    ItemStack put(EnumWrappers.ItemSlot slot, ItemStack item) {
        return overrides.put(slot, item);
    }

    private ItemStack slotOr(EnumWrappers.ItemSlot slot, Supplier<ItemStack> fallback) {
        ItemStack item = overrides.get(slot);
        return item != null ? item.clone() : fallback.get();
    }

    @Override
    public ItemStack getMainHand() {
        return slotOr(EnumWrappers.ItemSlot.MAINHAND,
                base != null ? base::getMainHand : super::getMainHand);
    }

    @Override
    public ItemStack getHelmet() {
        return slotOr(EnumWrappers.ItemSlot.HEAD,
                base != null ? base::getHelmet : super::getHelmet);
    }

    @Override
    public ItemStack getChestplate() {
        return slotOr(EnumWrappers.ItemSlot.CHEST,
                base != null ? base::getChestplate : super::getChestplate);
    }

    @Override
    public ItemStack getLeggings() {
        return slotOr(EnumWrappers.ItemSlot.LEGS,
                base != null ? base::getLeggings : super::getLeggings);
    }

    @Override
    public ItemStack getBoots() {
        return slotOr(EnumWrappers.ItemSlot.FEET,
                base != null ? base::getBoots : super::getBoots);
    }

    @Override
    public ItemStack getPickaxe() {
        return base != null ? base.getPickaxe() : super.getPickaxe();
    }

    @Override
    public ItemStack getSword() {
        return base != null ? base.getSword() : super.getSword();
    }
}
