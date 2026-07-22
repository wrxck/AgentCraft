package com.agentcraft.tool.impl;

import com.agentcraft.agent.AIAgent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Shared chest-deposit logic used by {@link StoreItemsTool} and
 * {@link InteractBlockTool}. Iterates a snapshot copy of the agent inventory so
 * that {@code removeFromInventory}'s structural mutation of the live list can
 * never upset iteration (CME / silent partial deposits), and clones stacks so
 * enchantments and other item meta survive the move.
 */
final class ChestTransfer {

    private ChestTransfer() {
    }

    /**
     * Deposits matching items from the agent's inventory into a chest inventory.
     *
     * @param filter   material to deposit, or {@code null} for everything
     * @param maxCount maximum number of items to move
     * @return the number of items actually deposited
     */
    static int deposit(AIAgent agent, Inventory chestInv, Material filter, int maxCount) {
        int deposited = 0;
        List<ItemStack> snapshot = new ArrayList<>(agent.getBehaviorController().getInventory());
        for (ItemStack stack : snapshot) {
            if (filter != null && stack.getType() != filter) continue;
            int toStore = Math.min(stack.getAmount(), maxCount - deposited);
            if (toStore <= 0) break;

            ItemStack toAdd = stack.clone();
            toAdd.setAmount(toStore);
            Map<Integer, ItemStack> overflow = chestInv.addItem(toAdd);
            int notFit = overflow.values().stream().mapToInt(ItemStack::getAmount).sum();
            int fit = toStore - notFit;

            if (fit > 0) {
                agent.getBehaviorController().removeFromInventory(stack.getType(), fit);
                deposited += fit;
            }
            if (notFit > 0) break; // chest full
        }
        return deposited;
    }
}
