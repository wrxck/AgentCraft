package com.agentcraft.expedition;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Reports expedition progress to the mission-issuing player.
 * Sends actionbar updates and milestone chat messages.
 */
public class ExpeditionReporter {

    private static final int ACTIONBAR_INTERVAL = 20;

    private final String agentName;
    private final UUID ownerUuid;
    private int actionbarCooldown;

    public ExpeditionReporter(String agentName, UUID ownerUuid) {
        this.agentName = agentName;
        this.ownerUuid = ownerUuid;
    }

    public void tick(ExpeditionController controller) {
        if (--actionbarCooldown > 0) return;
        actionbarCooldown = ACTIONBAR_INTERVAL;

        Player owner = Bukkit.getPlayer(ownerUuid);
        if (owner == null || !owner.isOnline()) return;

        String stateLabel = formatState(controller.getState());
        int gathered = controller.getGathered();
        int target = controller.getTargetCount();
        String material = controller.getTargetMaterial();
        NPCGear gear = controller.getGear();
        double distance = controller.getWaypointManager().getTotalDistanceTraveled();

        String bar = ChatColor.GRAY + "[" + ChatColor.AQUA + agentName + ChatColor.GRAY + "] "
                + ChatColor.WHITE + (int) distance + "m"
                + ChatColor.GRAY + " | "
                + ChatColor.YELLOW + stateLabel + " " + material + " " + gathered + "/" + target
                + ChatColor.GRAY + " | "
                + ChatColor.RED + "HP: " + gear.getHpDisplay();

        owner.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(bar));
    }

    public void reportMilestone(String message) {
        Player owner = Bukkit.getPlayer(ownerUuid);
        if (owner == null || !owner.isOnline()) return;

        String formatted = ChatColor.GRAY + "[" + ChatColor.AQUA + agentName + ChatColor.GRAY + "] "
                + ChatColor.WHITE + message;
        owner.sendMessage(formatted);
    }

    public void reportFoundMaterial(String material, int x, int y, int z) {
        reportMilestone("Found " + ChatColor.GREEN + material + ChatColor.WHITE
                + " at " + ChatColor.YELLOW + x + ", " + y + ", " + z);
    }

    public void reportCombat(String mobName, boolean killed) {
        if (killed) {
            reportMilestone("Killed a " + ChatColor.RED + mobName + ChatColor.WHITE + "!");
        } else {
            reportMilestone("Engaged in combat with " + ChatColor.RED + mobName + ChatColor.WHITE + "!");
        }
    }

    public void reportFleeing(int mobCount) {
        reportMilestone(ChatColor.YELLOW + "Fleeing from " + mobCount + " hostile mobs!");
    }

    public void reportLowHealth(double hp) {
        reportMilestone(ChatColor.RED + "Low health! " + ChatColor.WHITE
                + String.format("HP: %.0f — eating food", hp));
    }

    public void reportDeath() {
        reportMilestone(ChatColor.DARK_RED + "Died during the expedition! Returning home empty-handed.");
    }

    public void reportReturning(int gathered, String material) {
        reportMilestone("Mission objective met! Returning home with "
                + ChatColor.GREEN + gathered + "x " + material + ChatColor.WHITE + ".");
    }

    public void reportComplete(int gathered, String material, double distance) {
        reportMilestone(ChatColor.GREEN + "Mission complete! "
                + ChatColor.WHITE + "Gathered " + ChatColor.GREEN + gathered + "x " + material
                + ChatColor.WHITE + " after traveling " + ChatColor.YELLOW + (int) distance + "m"
                + ChatColor.WHITE + ".");
    }

    public void reportCancelled() {
        reportMilestone(ChatColor.YELLOW + "Expedition cancelled.");
    }

    private String formatState(ExpeditionState state) {
        return switch (state) {
            case TRAVELING_SURFACE -> "Traveling";
            case DESCENDING -> "Descending";
            case EXPLORING_CAVE -> "Exploring cave";
            case SEARCHING -> "Searching";
            case BRANCH_MINING -> "Branch mining";
            case GATHERING -> "Gathering";
            case COMBAT -> "Fighting";
            case FLEEING -> "Fleeing";
            case EATING -> "Eating";
            case RETURNING_HOME -> "Returning";
            case COMPLETED -> "Complete";
            case FAILED -> "Failed";
        };
    }
}
