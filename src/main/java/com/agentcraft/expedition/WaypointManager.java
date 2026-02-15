package com.agentcraft.expedition;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.List;

/**
 * Spawns armor stand markers every 100 blocks along the expedition path.
 */
public class WaypointManager {

    private static final int WAYPOINT_INTERVAL = 100;

    private final String agentName;
    private final List<ArmorStand> markers = new ArrayList<>();

    private double totalDistanceTraveled;
    private Location lastLocation;
    private int nextWaypointAt;

    public WaypointManager(String agentName, Location startLocation) {
        this.agentName = agentName;
        this.lastLocation = startLocation.clone();
        this.nextWaypointAt = WAYPOINT_INTERVAL;
    }

    public void tick(Location currentLocation) {
        if (lastLocation != null && lastLocation.getWorld().equals(currentLocation.getWorld())) {
            totalDistanceTraveled += lastLocation.distance(currentLocation);
        }
        lastLocation = currentLocation.clone();

        if (totalDistanceTraveled >= nextWaypointAt) {
            spawnMarker(currentLocation);
            nextWaypointAt += WAYPOINT_INTERVAL;
        }
    }

    private void spawnMarker(Location location) {
        World world = location.getWorld();
        ArmorStand stand = (ArmorStand) world.spawnEntity(location, EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setCustomName("[" + agentName + "] " + (int) totalDistanceTraveled + "m");
        stand.setCustomNameVisible(true);
        stand.setGravity(false);
        stand.setMarker(true);
        stand.setInvulnerable(true);
        markers.add(stand);
    }

    public void cleanup() {
        for (ArmorStand stand : markers) {
            if (stand != null && !stand.isDead()) {
                stand.remove();
            }
        }
        markers.clear();
    }

    public double getTotalDistanceTraveled() { return totalDistanceTraveled; }
    public int getMarkerCount() { return markers.size(); }
}
