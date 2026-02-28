package me.azzimov.ortuscapture;

import org.bukkit.Location;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.ArmorStand;

import java.util.UUID;

/**
 * Capture session for a single player and region.
 * Stores basic data and the BossBar + armor stand used for display.
 */
public class CaptureSession {

    private final UUID playerUuid;
    private final String regionId;
    private final String landId;
    private final CaptureMode mode;
    private final long startTimeMillis;
    private final BossBar bossBar;
    private final ArmorStand armorStand;
    private final Location flagLocation;
    private final int requiredPoints;
    private ActiveFlagPole flagPole;
    private int currentPoints;
    private boolean pausedByDefenders;

    public CaptureSession(UUID playerUuid,
                          String regionId,
                          String landId,
                          CaptureMode mode,
                          long startTimeMillis,
                          BossBar bossBar,
                          ArmorStand armorStand,
                          Location flagLocation,
                          int requiredPoints) {
        this.playerUuid = playerUuid;
        this.regionId = regionId;
        this.landId = landId;
        this.mode = mode;
        this.startTimeMillis = startTimeMillis;
        this.bossBar = bossBar;
        this.armorStand = armorStand;
        this.flagLocation = flagLocation;
        this.requiredPoints = Math.max(1, requiredPoints);
        this.currentPoints = 0;
        this.pausedByDefenders = false;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getRegionId() {
        return regionId;
    }

    public String getLandId() {
        return landId;
    }

    public CaptureMode getMode() {
        return mode;
    }

    public long getStartTimeMillis() {
        return startTimeMillis;
    }

    public BossBar getBossBar() {
        return bossBar;
    }

    public ArmorStand getArmorStand() {
        return armorStand;
    }

    public Location getFlagLocation() {
        return flagLocation;
    }

    public int getRequiredPoints() {
        return requiredPoints;
    }

    public ActiveFlagPole getFlagPole() {
        return flagPole;
    }

    public void setFlagPole(ActiveFlagPole flagPole) {
        this.flagPole = flagPole;
    }

    public int getCurrentPoints() {
        return currentPoints;
    }

    public int addPoints(int points) {
        if (points <= 0) {
            return currentPoints;
        }
        currentPoints = Math.min(requiredPoints, currentPoints + points);
        return currentPoints;
    }

    public boolean isPausedByDefenders() {
        return pausedByDefenders;
    }

    public void setPausedByDefenders(boolean pausedByDefenders) {
        this.pausedByDefenders = pausedByDefenders;
    }
}
