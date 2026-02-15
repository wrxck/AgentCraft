package com.agentcraft.npc;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class AgentEquipment {

    private final ItemStack pickaxe;
    private final ItemStack sword;
    private final ItemStack helmet;
    private final ItemStack chestplate;
    private final ItemStack leggings;
    private final ItemStack boots;

    public AgentEquipment() {
        this.pickaxe = buildPickaxe();
        this.sword = buildSword();
        this.helmet = buildHelmet();
        this.chestplate = buildChestplate();
        this.leggings = buildLeggings();
        this.boots = buildBoots();
    }

    private ItemStack buildPickaxe() {
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.addEnchant(Enchantment.EFFICIENCY, 5, true);
        meta.addEnchant(Enchantment.UNBREAKING, 3, true);
        meta.addEnchant(Enchantment.FORTUNE, 3, true);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildSword() {
        ItemStack item = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.addEnchant(Enchantment.SHARPNESS, 5, true);
        meta.addEnchant(Enchantment.FIRE_ASPECT, 2, true);
        meta.addEnchant(Enchantment.LOOTING, 3, true);
        meta.addEnchant(Enchantment.SWEEPING_EDGE, 3, true);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildHelmet() {
        ItemStack item = new ItemStack(Material.DIAMOND_HELMET);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.addEnchant(Enchantment.PROTECTION, 4, true);
        meta.addEnchant(Enchantment.UNBREAKING, 3, true);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildChestplate() {
        ItemStack item = new ItemStack(Material.DIAMOND_CHESTPLATE);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.addEnchant(Enchantment.PROTECTION, 4, true);
        meta.addEnchant(Enchantment.UNBREAKING, 3, true);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildLeggings() {
        ItemStack item = new ItemStack(Material.DIAMOND_LEGGINGS);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.addEnchant(Enchantment.PROTECTION, 4, true);
        meta.addEnchant(Enchantment.UNBREAKING, 3, true);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildBoots() {
        ItemStack item = new ItemStack(Material.DIAMOND_BOOTS);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.addEnchant(Enchantment.PROTECTION, 4, true);
        meta.addEnchant(Enchantment.UNBREAKING, 3, true);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack getPickaxe() { return pickaxe.clone(); }
    public ItemStack getSword() { return sword.clone(); }
    public ItemStack getHelmet() { return helmet.clone(); }
    public ItemStack getChestplate() { return chestplate.clone(); }
    public ItemStack getLeggings() { return leggings.clone(); }
    public ItemStack getBoots() { return boots.clone(); }

    /** Default main hand item (sword when not mining). */
    public ItemStack getMainHand() { return sword.clone(); }
}
