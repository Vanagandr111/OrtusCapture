package me.azzimov.ortuscapture;

import me.angeschossen.lands.api.events.war.WarStartEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class LandsWarsListener implements Listener {

    private final OrtusCapture plugin;

    public LandsWarsListener(OrtusCapture plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onWarStart(WarStartEvent event) {
        plugin.playWarsStartHorn(event);
        plugin.cancelCapturesConflictingWithWars();
    }
}
