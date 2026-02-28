package me.azzimov.ortuscapture;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ActiveFlagPole {

    private final Location anchor;
    private final List<Location> collisionBlocks;
    private final List<Entity> displayEntities;
    private final String colorKey;
    private final int maxHealth;
    private int health;
    private long lastRepairMillis;

    public ActiveFlagPole(Location anchor,
                          List<Location> collisionBlocks,
                          List<Entity> displayEntities,
                          String colorKey,
                          int maxHealth) {
        this.anchor = anchor == null ? null : anchor.clone();
        this.collisionBlocks = cloneLocations(collisionBlocks);
        this.displayEntities = new ArrayList<>(displayEntities == null ? List.of() : displayEntities);
        this.colorKey = colorKey;
        this.maxHealth = Math.max(1, maxHealth);
        this.health = this.maxHealth;
        this.lastRepairMillis = 0L;
    }

    public Location getAnchor() {
        return anchor == null ? null : anchor.clone();
    }

    public List<Location> getCollisionBlocks() {
        return Collections.unmodifiableList(collisionBlocks);
    }

    public List<Entity> getDisplayEntities() {
        return Collections.unmodifiableList(displayEntities);
    }

    public String getColorKey() {
        return colorKey;
    }

    public int getMaxHealth() {
        return maxHealth;
    }

    public int getHealth() {
        return health;
    }

    public int damage(int amount) {
        if (amount <= 0) {
            return health;
        }
        health = Math.max(0, health - amount);
        return health;
    }

    public int repair(int amount) {
        if (amount <= 0) {
            return health;
        }
        health = Math.min(maxHealth, health + amount);
        return health;
    }

    public boolean isBroken() {
        return health <= 0;
    }

    public long getLastRepairMillis() {
        return lastRepairMillis;
    }

    public void setLastRepairMillis(long lastRepairMillis) {
        this.lastRepairMillis = lastRepairMillis;
    }

    public double getHealthPercent() {
        return Math.max(0D, Math.min(1D, (double) health / (double) maxHealth));
    }

    private static List<Location> cloneLocations(List<Location> input) {
        if (input == null || input.isEmpty()) {
            return new ArrayList<>();
        }
        List<Location> out = new ArrayList<>(input.size());
        for (Location loc : input) {
            if (loc != null) {
                out.add(loc.clone());
            }
        }
        return out;
    }
}
