package me.azzimov.ortuscapture;

import me.angeschossen.lands.api.LandsIntegration;
import me.angeschossen.lands.api.land.Land;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public class LandsHook {

    private final OrtusCapture plugin;
    private LandsIntegration landsApi;
    private boolean warApiAvailable;

    public LandsHook(OrtusCapture plugin) {
        this.plugin = plugin;
        hook();
    }

    private void hook() {
        this.warApiAvailable = isWarApiClassPresent();
        Plugin lands = Bukkit.getPluginManager().getPlugin("Lands");
        if (lands == null || !lands.isEnabled()) {
            plugin.getLogger().warning(plugin.getLang().formatRaw("log.lands_not_found"));
            return;
        }

        try {
            this.landsApi = LandsIntegration.of(plugin);
            plugin.getLogger().info(plugin.getLang().formatRaw("log.lands_hooked"));
        } catch (Exception ex) {
            plugin.getLogger().severe(plugin.getLang().formatRaw("log.lands_hook_failed", "error", ex.getMessage()));
        }
    }

    public boolean isAvailable() {
        return landsApi != null;
    }

    public LandsIntegration getLandsApi() {
        return landsApi;
    }

    public boolean isWarApiAvailable() {
        return warApiAvailable;
    }

    public boolean isLandInWar(Land land) {
        if (land == null || !warApiAvailable) {
            return false;
        }
        try {
            return land.isInWar() || land.getWar() != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isWarApiClassPresent() {
        try {
            Class.forName("me.angeschossen.lands.api.events.war.WarStartEvent");
            Class.forName("me.angeschossen.lands.api.war.War");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
