package me.azzimov.ortuscapture;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.Locale;
import java.util.UUID;

public class OrtusPlaceholderExpansion extends PlaceholderExpansion {

    private final OrtusCapture plugin;

    public OrtusPlaceholderExpansion(OrtusCapture plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "ortus";
    }

    @Override
    public String getAuthor() {
        return "azzimov";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (params == null || params.isEmpty()) {
            return "";
        }

        String normalized = params.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("owner_")) {
            String regionId = params.substring("owner_".length());
            return resolveOwner(regionId);
        }

        if (normalized.startsWith("cooldown_")) {
            String regionId = params.substring("cooldown_".length());
            return resolveCooldown(regionId);
        }

        return null;
    }

    private String resolveOwner(String regionId) {
        if (regionId == null || regionId.isEmpty()) {
            return plugin.getLang().get("values.none");
        }
        RegionModel region = plugin.getDataManager().getRegion(regionId);
        if (region == null) {
            return plugin.getLang().get("placeholders.owner_missing");
        }

        UUID owner = region.getCurrentOwner();
        if (owner == null) {
            return plugin.getLang().get("placeholders.owner_none");
        }

        String name = Bukkit.getOfflinePlayer(owner).getName();
        return name != null && !name.isEmpty() ? name : owner.toString();
    }

    private String resolveCooldown(String regionId) {
        if (regionId == null || regionId.isEmpty()) {
            return plugin.getLang().get("placeholders.cooldown_zero");
        }
        RegionModel region = plugin.getDataManager().getRegion(regionId);
        if (region == null) {
            return plugin.getLang().get("placeholders.cooldown_zero");
        }

        long cooldownHours = plugin.getConfig().getLong("region-cooldown-hours", 24L);
        long cooldownMillis = cooldownHours <= 0L ? 0L : cooldownHours * 60L * 60L * 1000L;
        if (cooldownMillis == 0L) {
            return plugin.getLang().get("placeholders.cooldown_zero");
        }

        long lastCapture = region.getLastCaptureTime();
        if (lastCapture <= 0L) {
            return plugin.getLang().get("placeholders.cooldown_zero");
        }

        long remaining = cooldownMillis - (System.currentTimeMillis() - lastCapture);
        if (remaining <= 0L) {
            return plugin.getLang().get("placeholders.cooldown_zero");
        }

        long hours = remaining / (60L * 60L * 1000L);
        long minutes = (remaining / (60L * 1000L)) % 60L;
        return plugin.getLang().format("placeholders.cooldown_format", "hours", String.valueOf(hours), "minutes", String.valueOf(minutes));
    }
}
