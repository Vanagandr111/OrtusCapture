package me.azzimov.ortuscapture;

public class RegionModel {

    private final String systemId;
    private final String landId;
    private String type;
    private boolean captureEnabled;
    private java.util.UUID currentOwner;
    private long lastCaptureTime;
    private String flagpoleWorld;
    private Integer flagpoleX;
    private Integer flagpoleY;
    private Integer flagpoleZ;

    public RegionModel(String systemId, String landId, String type, boolean captureEnabled) {
        this(systemId, landId, type, captureEnabled, null, 0L, null, null, null, null);
    }

    public RegionModel(String systemId,
                       String landId,
                       String type,
                       boolean captureEnabled,
                       java.util.UUID currentOwner,
                       long lastCaptureTime) {
        this(systemId, landId, type, captureEnabled, currentOwner, lastCaptureTime, null, null, null, null);
    }

    public RegionModel(String systemId,
                       String landId,
                       String type,
                       boolean captureEnabled,
                       java.util.UUID currentOwner,
                       long lastCaptureTime,
                       String flagpoleWorld,
                       Integer flagpoleX,
                       Integer flagpoleY,
                       Integer flagpoleZ) {
        this.systemId = systemId;
        this.landId = landId;
        this.type = type;
        this.captureEnabled = captureEnabled;
        this.currentOwner = currentOwner;
        this.lastCaptureTime = lastCaptureTime;
        this.flagpoleWorld = flagpoleWorld;
        this.flagpoleX = flagpoleX;
        this.flagpoleY = flagpoleY;
        this.flagpoleZ = flagpoleZ;
    }

    public String getSystemId() {
        return systemId;
    }

    public String getLandId() {
        return landId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isCaptureEnabled() {
        return captureEnabled;
    }

    public void setCaptureEnabled(boolean captureEnabled) {
        this.captureEnabled = captureEnabled;
    }

    public java.util.UUID getCurrentOwner() {
        return currentOwner;
    }

    public void setCurrentOwner(java.util.UUID currentOwner) {
        this.currentOwner = currentOwner;
    }

    public long getLastCaptureTime() {
        return lastCaptureTime;
    }

    public void setLastCaptureTime(long lastCaptureTime) {
        this.lastCaptureTime = lastCaptureTime;
    }

    public String getFlagpoleWorld() {
        return flagpoleWorld;
    }

    public Integer getFlagpoleX() {
        return flagpoleX;
    }

    public Integer getFlagpoleY() {
        return flagpoleY;
    }

    public Integer getFlagpoleZ() {
        return flagpoleZ;
    }

    public boolean hasFlagpolePoint() {
        return flagpoleWorld != null && flagpoleX != null && flagpoleY != null && flagpoleZ != null;
    }

    public void setFlagpolePoint(String world, Integer x, Integer y, Integer z) {
        this.flagpoleWorld = world;
        this.flagpoleX = x;
        this.flagpoleY = y;
        this.flagpoleZ = z;
    }

    public void clearFlagpolePoint() {
        this.flagpoleWorld = null;
        this.flagpoleX = null;
        this.flagpoleY = null;
        this.flagpoleZ = null;
    }
}
