package com.agentcraft.testutil;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.behavior.BehaviorController;
import com.agentcraft.npc.FakePlayer;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

/**
 * Builds a Mockito-mocked {@link AIAgent} whose {@link BehaviorController} is
 * backed by a REAL {@code List<ItemStack>}, with mutation semantics wired to
 * mirror the real BehaviorController exactly:
 * <ul>
 *   <li>{@code getInventory()} returns {@code Collections.unmodifiableList}
 *       wrapping the LIVE list (as the real code does), so structural mutation
 *       during iteration reproduces ConcurrentModificationException.</li>
 *   <li>{@code removeFromInventory(Material)} decrements a stack or removes it
 *       structurally when the amount hits zero.</li>
 *   <li>{@code removeFromInventory(Material, int)} uses iterator-based removal
 *       of depleted stacks, returning the amount actually removed.</li>
 * </ul>
 */
public final class InventoryAgentMock {

    private InventoryAgentMock() {
    }

    public static AIAgent create(List<ItemStack> backing, World world, Location npcLocation) {
        AIAgent agent = mock(AIAgent.class);
        BehaviorController controller = mock(BehaviorController.class);
        FakePlayer npc = mock(FakePlayer.class);

        when(agent.getBehaviorController()).thenReturn(controller);
        when(agent.getNpc()).thenReturn(npc);
        when(npc.getLocation()).thenReturn(npcLocation);

        when(controller.getInventory()).thenAnswer(inv -> Collections.unmodifiableList(backing));

        when(controller.removeFromInventory(any(Material.class))).thenAnswer(inv -> {
            Material material = inv.getArgument(0);
            for (int i = 0; i < backing.size(); i++) {
                if (backing.get(i).getType() == material) {
                    ItemStack stack = backing.get(i);
                    if (stack.getAmount() > 1) {
                        stack.setAmount(stack.getAmount() - 1);
                    } else {
                        backing.remove(i);
                    }
                    return true;
                }
            }
            return false;
        });

        when(controller.removeFromInventory(any(Material.class), anyInt())).thenAnswer(inv -> {
            Material material = inv.getArgument(0);
            int count = inv.getArgument(1);
            int remaining = count;
            Iterator<ItemStack> it = backing.iterator();
            while (it.hasNext() && remaining > 0) {
                ItemStack stack = it.next();
                if (stack.getType() != material) continue;
                if (stack.getAmount() <= remaining) {
                    remaining -= stack.getAmount();
                    it.remove();
                } else {
                    stack.setAmount(stack.getAmount() - remaining);
                    remaining = 0;
                }
            }
            return count - remaining;
        });

        when(controller.hasInInventory(any(Material.class))).thenAnswer(inv -> {
            Material material = inv.getArgument(0);
            for (ItemStack stack : backing) {
                if (stack.getType() == material) return true;
            }
            return false;
        });

        when(controller.countInInventory(any(Material.class))).thenAnswer(inv -> {
            Material material = inv.getArgument(0);
            int total = 0;
            for (ItemStack stack : backing) {
                if (stack.getType() == material) total += stack.getAmount();
            }
            return total;
        });

        doAnswer(inv -> {
            backing.add(inv.getArgument(0));
            return null;
        }).when(controller).addToInventory(any(ItemStack.class));

        return agent;
    }
}
