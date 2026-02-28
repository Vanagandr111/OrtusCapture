package me.azzimov.ortuscapture;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class DataManager {

    private final OrtusCapture plugin;
    private final File file;
    private FileConfiguration config;
    private final Map<String, RegionModel> regions = new HashMap<>();

    public DataManager(OrtusCapture plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "regions.yml");

        if (!plugin.getDataFolder().exists()) {
            //noinspection ResultOfMethodCallIgnored
            plugin.getDataFolder().mkdirs();
        }
        if (!file.exists()) {
            try {
                //noinspection ResultOfMethodCallIgnored
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe(plugin.getLang().formatRaw("log.regions_create_fail", "error", e.getMessage()));
            }
        }

        reload();
    }

    private void loadRegions() {
        regions.clear();

        ConfigurationSection root = config.getConfigurationSection("regions");
        if (root == null) {
            plugin.getLogger().info(plugin.getLang().formatRaw("log.regions_loaded", "count", "0"));
            return;
        }

        for (String systemId : root.getKeys(false)) {
            String landId = root.getString(systemId + ".landsId");
            String type = root.getString(systemId + ".type");
            boolean hasCaptureEnabled = root.contains(systemId + ".captureEnabled");
            boolean captureEnabled = hasCaptureEnabled
                    ? root.getBoolean(systemId + ".captureEnabled", true)
                    : root.getBoolean(systemId + ".capture_zone", true);
            String ownerStr = root.getString(systemId + ".currentOwner");
            long lastCaptureTime = root.getLong(systemId + ".lastCaptureTime", 0L);
            String flagpoleWorld = root.getString(systemId + ".flagpole.world");
            Integer flagpoleX = root.contains(systemId + ".flagpole.x") ? root.getInt(systemId + ".flagpole.x") : null;
            Integer flagpoleY = root.contains(systemId + ".flagpole.y") ? root.getInt(systemId + ".flagpole.y") : null;
            Integer flagpoleZ = root.contains(systemId + ".flagpole.z") ? root.getInt(systemId + ".flagpole.z") : null;
            java.util.UUID currentOwner = null;
            if (ownerStr != null && !ownerStr.isEmpty()) {
                try {
                    currentOwner = java.util.UUID.fromString(ownerStr);
                } catch (IllegalArgumentException ignored) {
                    // ignore invalid UUID
                }
            }

            if (landId == null || type == null) {
                continue;
            }

            RegionModel model = new RegionModel(systemId, landId, type, captureEnabled, currentOwner, lastCaptureTime,
                    flagpoleWorld, flagpoleX, flagpoleY, flagpoleZ);
            regions.put(systemId, model);
        }

        plugin.getLogger().info(plugin.getLang().formatRaw("log.regions_loaded", "count", String.valueOf(regions.size())));
    }

    public void reload() {
        this.config = YamlConfiguration.loadConfiguration(file);
        loadRegions();
    }

    public void saveRegions() {
        config.set("regions", null);

        for (RegionModel region : regions.values()) {
            String basePath = "regions." + region.getSystemId();
            config.set(basePath + ".landsId", region.getLandId());
            config.set(basePath + ".type", region.getType());
            config.set(basePath + ".captureEnabled", region.isCaptureEnabled());
            config.set(basePath + ".capture_zone", region.isCaptureEnabled());
            java.util.UUID owner = region.getCurrentOwner();
            config.set(basePath + ".currentOwner", owner != null ? owner.toString() : null);
            config.set(basePath + ".lastCaptureTime", region.getLastCaptureTime());
            if (region.hasFlagpolePoint()) {
                config.set(basePath + ".flagpole.world", region.getFlagpoleWorld());
                config.set(basePath + ".flagpole.x", region.getFlagpoleX());
                config.set(basePath + ".flagpole.y", region.getFlagpoleY());
                config.set(basePath + ".flagpole.z", region.getFlagpoleZ());
            } else {
                config.set(basePath + ".flagpole", null);
            }
        }

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe(plugin.getLang().formatRaw("log.regions_save_fail", "error", e.getMessage()));
        }
    }

    public void addOrUpdateRegion(RegionModel region) {
        regions.put(region.getSystemId(), region);
        saveRegions();
    }

    public boolean removeRegion(String systemId) {
        if (systemId == null) {
            return false;
        }
        RegionModel removed = regions.remove(systemId);
        if (removed != null) {
            saveRegions();
            return true;
        }
        return false;
    }

    public String getRegionIdByLandId(String searchLandId) {
        if (searchLandId == null) {
            return null;
        }

        ConfigurationSection root = config.getConfigurationSection("regions");
        if (root == null) {
            return null;
        }

        for (String key : root.getKeys(false)) {
            String cfgLandId = root.getString(key + ".landsId");
            if (cfgLandId != null && cfgLandId.equalsIgnoreCase(searchLandId)) {
                return key;
            }
        }
        return null;
    }

    public Set<String> getSystemIds() {
        return regions.keySet();
    }

    public RegionModel getRegion(String systemId) {
        return regions.get(systemId);
    }

    public Collection<RegionModel> getRegions() {
        return regions.values();
    }
}
