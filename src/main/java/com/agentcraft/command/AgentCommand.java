package com.agentcraft.command;

import com.agentcraft.AgentCraftPlugin;
import com.agentcraft.agent.AIAgent;
import com.agentcraft.agent.AgentManager;
import com.agentcraft.expedition.ExpeditionController;
import com.agentcraft.util.MessageUtil;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class AgentCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = Arrays.asList(
            "spawn", "despawn", "task", "list", "status", "stop", "output", "reload",
            "tp", "recall", "cancel", "expedition", "exp", "mission", "help"
    );

    private final AgentCraftPlugin plugin;

    public AgentCommand(AgentCraftPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        if (args.length == 0) {
            showHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "spawn" -> handleSpawn(player, args);
            case "despawn" -> handleDespawn(player, args);
            case "task" -> handleTask(player, args);
            case "list" -> handleList(player);
            case "status" -> handleStatus(player, args);
            case "stop" -> handleStop(player, args);
            case "output" -> handleOutput(player, args);
            case "reload" -> handleReload(player);
            case "tp" -> handleTp(player, args);
            case "recall" -> handleRecall(player, args);
            case "cancel" -> handleCancel(player, args);
            case "expedition", "exp", "mission" -> handleExpedition(player, args);
            case "help" -> showHelp(player);
            default -> MessageUtil.send(player, MessageUtil.error("Unknown subcommand. Use /agent help"));
        }

        return true;
    }

    private void handleSpawn(Player player, String[] args) {
        if (!player.hasPermission("agentcraft.admin")) {
            MessageUtil.send(player, MessageUtil.error("No permission."));
            return;
        }

        if (args.length < 2) {
            MessageUtil.send(player, MessageUtil.error("Usage: /agent spawn <profile>"));
            return;
        }

        String profileId = args[1].toLowerCase();
        AgentManager mgr = AgentManager.getInstance();

        AIAgent agent = mgr.spawnAgent(profileId, player.getLocation(), player);
        if (agent == null) {
            MessageUtil.send(player, MessageUtil.error("Failed to spawn agent. Check profile name or agent already exists."));
            return;
        }

        MessageUtil.send(player, MessageUtil.success("Spawned agent " + MessageUtil.highlight(agent.getNpc().getName())
                + " (" + agent.getProfile().getSpecialty() + ")."));
    }

    private void handleDespawn(Player player, String[] args) {
        if (!player.hasPermission("agentcraft.admin")) {
            MessageUtil.send(player, MessageUtil.error("No permission."));
            return;
        }

        if (args.length < 2) {
            MessageUtil.send(player, MessageUtil.error("Usage: /agent despawn <name>"));
            return;
        }

        String name = args[1];
        if (AgentManager.getInstance().despawnAgent(name)) {
            MessageUtil.send(player, MessageUtil.success("Despawned agent " + MessageUtil.highlight(name) + "."));
        } else {
            MessageUtil.send(player, MessageUtil.error("No agent found with name: " + name));
        }
    }

    private void handleTask(Player player, String[] args) {
        if (!player.hasPermission("agentcraft.use")) {
            MessageUtil.send(player, MessageUtil.error("No permission."));
            return;
        }

        if (args.length < 3) {
            MessageUtil.send(player, MessageUtil.error("Usage: /agent task <name> <task description>"));
            return;
        }

        String name = args[1];
        AIAgent agent = AgentManager.getInstance().getAgent(name);
        if (agent == null) {
            MessageUtil.send(player, MessageUtil.error("No agent found with name: " + name));
            return;
        }

        String task = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        plugin.getAiIntegration().submitTask(agent, player, task);
    }

    private void handleList(Player player) {
        var agents = AgentManager.getInstance().getAllAgents();
        if (agents.isEmpty()) {
            MessageUtil.send(player, MessageUtil.info("No agents currently spawned."));
            return;
        }

        MessageUtil.send(player, MessageUtil.info("Active agents:"));
        for (AIAgent agent : agents) {
            String status = switch (agent.getState()) {
                case IDLE -> "&7IDLE";
                case THINKING -> "&eTHINKING";
                case WORKING -> "&aWORKING";
                case PAUSED -> "&cPAUSED";
                case ON_EXPEDITION -> "&dEXPEDITION";
            };

            String extra = "";
            if (agent.getExpedition() != null) {
                ExpeditionController exp = agent.getExpedition();
                extra = ChatColor.WHITE + " - " + exp.getState().name().toLowerCase()
                        + " " + exp.getTargetMaterial() + " " + exp.getGathered() + "/" + exp.getTargetCount();
            } else if (agent.getCurrentTask() != null) {
                extra = ChatColor.WHITE + " - " + agent.getCurrentTask();
            }

            player.sendMessage("  " + ChatColor.AQUA + agent.getNpc().getName()
                    + ChatColor.GRAY + " (" + agent.getProfile().getSpecialty() + ")"
                    + " [" + ChatColor.translateAlternateColorCodes('&', status)
                    + ChatColor.GRAY + "]"
                    + extra);
        }
    }

    private void handleStatus(Player player, String[] args) {
        if (args.length < 2) {
            MessageUtil.send(player, MessageUtil.error("Usage: /agent status <name>"));
            return;
        }

        AIAgent agent = AgentManager.getInstance().getAgent(args[1]);
        if (agent == null) {
            MessageUtil.send(player, MessageUtil.error("No agent found with name: " + args[1]));
            return;
        }

        AgentManager mgr = AgentManager.getInstance();
        String hostPath = mgr.getAgentOutputHost() + "/" + agent.getNpc().getName().toLowerCase();

        MessageUtil.send(player, MessageUtil.info("Agent: " + MessageUtil.highlight(agent.getNpc().getName())));
        player.sendMessage("  State: " + agent.getState());
        player.sendMessage("  Profile: " + agent.getProfile().getId());
        player.sendMessage("  Specialty: " + agent.getProfile().getSpecialty());
        player.sendMessage("  Working dir: " + hostPath);
        player.sendMessage("  Task: " + (agent.getCurrentTask() != null ? agent.getCurrentTask() : "None"));

        // Expedition details
        ExpeditionController exp = agent.getExpedition();
        if (exp != null) {
            Location npcLoc = agent.getNpc().getLocation();
            player.sendMessage("  " + ChatColor.LIGHT_PURPLE + "--- Expedition ---");
            player.sendMessage("  Target: " + exp.getTargetMaterial() + " x" + exp.getTargetCount()
                    + " (" + exp.getCategory().name() + ")");
            player.sendMessage("  State: " + exp.getState().name());
            player.sendMessage("  Progress: " + exp.getGathered() + "/" + exp.getTargetCount());
            player.sendMessage("  Distance: " + (int) exp.getWaypointManager().getTotalDistanceTraveled() + "m");
            player.sendMessage("  HP: " + exp.getGear().getHpDisplay()
                    + " | Pick: " + exp.getGear().getPickaxeDurability()
                    + " | Food: " + exp.getGear().getFoodCount());
            player.sendMessage("  Location: " + npcLoc.getBlockX() + ", "
                    + npcLoc.getBlockY() + ", " + npcLoc.getBlockZ());
            player.sendMessage("  Kills: " + exp.getCombatHandler().getKillCount());
        }
    }

    private void handleStop(Player player, String[] args) {
        if (!player.hasPermission("agentcraft.admin")) {
            MessageUtil.send(player, MessageUtil.error("No permission."));
            return;
        }

        if (args.length < 2) {
            MessageUtil.send(player, MessageUtil.error("Usage: /agent stop <name>"));
            return;
        }

        AIAgent agent = AgentManager.getInstance().getAgent(args[1]);
        if (agent == null) {
            MessageUtil.send(player, MessageUtil.error("No agent found with name: " + args[1]));
            return;
        }

        agent.stop();
        MessageUtil.send(player, MessageUtil.success("Stopped agent " + MessageUtil.highlight(agent.getNpc().getName()) + "."));
    }

    private void handleOutput(Player player, String[] args) {
        if (args.length < 2) {
            MessageUtil.send(player, MessageUtil.error("Usage: /agent output <name>"));
            return;
        }

        AIAgent agent = AgentManager.getInstance().getAgent(args[1]);
        if (agent == null) {
            MessageUtil.send(player, MessageUtil.error("No agent found with name: " + args[1]));
            return;
        }

        AgentManager mgr = AgentManager.getInstance();
        String hostPath = mgr.getAgentOutputHost() + "/" + agent.getNpc().getName().toLowerCase();
        MessageUtil.send(player, MessageUtil.info("Output directory for "
                + MessageUtil.highlight(agent.getNpc().getName()) + ":"));
        player.sendMessage("  " + ChatColor.WHITE + hostPath);
    }

    private void handleReload(Player player) {
        if (!player.hasPermission("agentcraft.admin")) {
            MessageUtil.send(player, MessageUtil.error("No permission."));
            return;
        }

        plugin.reloadConfig();
        AgentManager.getInstance().loadProfiles();
        MessageUtil.send(player, MessageUtil.success("Configuration reloaded."));
    }

    private void handleTp(Player player, String[] args) {
        if (!player.hasPermission("agentcraft.use")) {
            MessageUtil.send(player, MessageUtil.error("No permission."));
            return;
        }

        if (args.length < 2) {
            MessageUtil.send(player, MessageUtil.error("Usage: /agent tp <name>"));
            return;
        }

        AIAgent agent = AgentManager.getInstance().getAgent(args[1]);
        if (agent == null) {
            MessageUtil.send(player, MessageUtil.error("No agent found with name: " + args[1]));
            return;
        }

        Location npcLoc = agent.getNpc().getLocation();
        player.teleport(npcLoc);

        // Respawn NPC packets for the player so they can see it
        agent.getNpc().spawn(player);

        MessageUtil.send(player, MessageUtil.success("Teleported to "
                + MessageUtil.highlight(agent.getNpc().getName()) + "."));
    }

    private void handleRecall(Player player, String[] args) {
        if (!player.hasPermission("agentcraft.use")) {
            MessageUtil.send(player, MessageUtil.error("No permission."));
            return;
        }

        if (args.length < 2) {
            MessageUtil.send(player, MessageUtil.error("Usage: /agent recall <name>"));
            return;
        }

        AIAgent agent = AgentManager.getInstance().getAgent(args[1]);
        if (agent == null) {
            MessageUtil.send(player, MessageUtil.error("No agent found with name: " + args[1]));
            return;
        }

        // Cancel expedition if active
        if (agent.getExpedition() != null) {
            agent.getExpedition().cancelAndTeleportHome();
            agent.getBehaviorController().cancelExpedition();
        }

        // Teleport NPC to player
        Location playerLoc = player.getLocation();
        for (Player viewer : org.bukkit.Bukkit.getOnlinePlayers()) {
            agent.getNpc().teleport(viewer, playerLoc);
        }

        MessageUtil.send(player, MessageUtil.success("Recalled "
                + MessageUtil.highlight(agent.getNpc().getName()) + " to your location."));
    }

    private void handleCancel(Player player, String[] args) {
        if (!player.hasPermission("agentcraft.use")) {
            MessageUtil.send(player, MessageUtil.error("No permission."));
            return;
        }

        if (args.length < 2) {
            MessageUtil.send(player, MessageUtil.error("Usage: /agent cancel <name>"));
            return;
        }

        AIAgent agent = AgentManager.getInstance().getAgent(args[1]);
        if (agent == null) {
            MessageUtil.send(player, MessageUtil.error("No agent found with name: " + args[1]));
            return;
        }

        if (agent.getExpedition() == null) {
            MessageUtil.send(player, MessageUtil.error(agent.getNpc().getName() + " is not on an expedition."));
            return;
        }

        agent.cancelExpedition();
        MessageUtil.send(player, MessageUtil.success("Cancelled expedition for "
                + MessageUtil.highlight(agent.getNpc().getName()) + "."));
    }

    private void handleExpedition(Player player, String[] args) {
        if (!player.hasPermission("agentcraft.use")) {
            MessageUtil.send(player, MessageUtil.error("No permission."));
            return;
        }

        if (args.length < 3) {
            MessageUtil.send(player, MessageUtil.error("Usage: /agent expedition <name> <material> [count]"));
            return;
        }

        String name = args[1];
        AIAgent agent = AgentManager.getInstance().getAgent(name);
        if (agent == null) {
            MessageUtil.send(player, MessageUtil.error("No agent found with name: " + name));
            return;
        }

        if (agent.getExpedition() != null) {
            MessageUtil.send(player, MessageUtil.error(name + " is already on an expedition. Cancel it first."));
            return;
        }

        String material = args[2];
        int count = 16;
        if (args.length >= 4) {
            try {
                count = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                MessageUtil.send(player, MessageUtil.error("Invalid count: " + args[3]));
                return;
            }
        }

        ExpeditionController expedition = new ExpeditionController(agent, player, material, count);
        agent.getBehaviorController().startExpedition(expedition);

        MessageUtil.send(player, MessageUtil.success("Sent "
                + MessageUtil.highlight(agent.getNpc().getName())
                + " on an expedition for " + MessageUtil.highlight(material + " x" + count) + "."));
    }

    private void showHelp(Player player) {
        MessageUtil.send(player, MessageUtil.info("Commands:"));
        player.sendMessage("  /agent spawn <profile> - Spawn an agent");
        player.sendMessage("  /agent despawn <name> - Remove an agent");
        player.sendMessage("  /agent task <name> <task> - Assign a task");
        player.sendMessage("  /agent list - List active agents");
        player.sendMessage("  /agent status <name> - Agent details");
        player.sendMessage("  /agent stop <name> - Stop current task");
        player.sendMessage("  /agent output <name> - Show output directory");
        player.sendMessage("  /agent expedition <name> <material> [count] - Send on expedition");
        player.sendMessage("  /agent tp <name> - Teleport to agent");
        player.sendMessage("  /agent recall <name> - Recall agent to you");
        player.sendMessage("  /agent cancel <name> - Cancel expedition");
        player.sendMessage("  /agent reload - Reload configuration");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return SUBCOMMANDS.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            return switch (sub) {
                case "spawn" -> AgentManager.getInstance().getProfileIds().stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
                case "despawn", "task", "status", "stop", "output",
                     "tp", "recall", "cancel", "expedition", "exp", "mission" ->
                        AgentManager.getInstance().getAgentNames().stream()
                                .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                                .collect(Collectors.toList());
                default -> new ArrayList<>();
            };
        }

        if (args.length == 3) {
            String sub = args[0].toLowerCase();
            if (sub.equals("expedition") || sub.equals("exp") || sub.equals("mission")) {
                // Suggest common materials
                List<String> materials = Arrays.asList(
                        "log", "stone", "coal_ore", "iron_ore", "gold_ore", "diamond_ore",
                        "redstone_ore", "lapis_ore", "emerald_ore", "stone_brick",
                        "cobblestone", "sand", "dirt", "gravel", "obsidian"
                );
                return materials.stream()
                        .filter(s -> s.startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        return new ArrayList<>();
    }
}
