package me.azzimov.ortuscapture;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

public class SystemOwnerGuard implements Listener {

    private final OrtusCapture plugin;

    public SystemOwnerGuard(OrtusCapture plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onAsyncPreLogin(AsyncPlayerPreLoginEvent event) {
        String systemName = plugin.getConfig().getString("system-owner-name", "Wild_land");
        if (systemName == null || systemName.isEmpty()) {
            return;
        }

        if (event.getName().equalsIgnoreCase(systemName)) {
            String message = plugin.getLang().format("guard.kick", "name", systemName);

            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, message);
        }
    }
}

