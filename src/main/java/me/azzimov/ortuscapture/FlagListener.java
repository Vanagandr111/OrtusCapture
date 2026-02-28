package me.azzimov.ortuscapture;

import me.angeschossen.lands.api.LandsIntegration;
import me.angeschossen.lands.api.land.Land;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.UUID;

public class FlagListener implements Listener {

    private final OrtusCapture plugin;

    public FlagListener(OrtusCapture plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (item == null) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        PersistentDataContainer container = meta.getPersistentDataContainer();
        String systemId = container.get(plugin.getFlagKey(), PersistentDataType.STRING);
        String typedRegionType = container.get(plugin.getFlagTypeKey(), PersistentDataType.STRING);
        String flagKind = container.get(plugin.getFlagKindKey(), PersistentDataType.STRING);
        boolean universalFlag = flagKind != null && flagKind.equalsIgnoreCase("universal");
        if (systemId == null && typedRegionType == null && !universalFlag) {
            return;
        }

        Player player = event.getPlayer();

        if (!plugin.hasWeekendBypass(player.getUniqueId())) {
            if (!plugin.isCaptureAllowedToday()) {
                player.sendMessage(plugin.getLang().format("flag.weekend_only"));
                event.setCancelled(true);
                return;
            }
        }

        LandsHook landsHook = plugin.getLandsHook();
        if (landsHook == null || !landsHook.isAvailable()) {
            player.sendMessage(plugin.getLang().format("flag.lands_unavailable"));
            event.setCancelled(true);
            return;
        }

        Location blockLocation = event.getBlockPlaced().getLocation();
        World world = blockLocation.getWorld();
        LandsIntegration api = landsHook.getLandsApi();

        Land land;
        try {
            int chunkX = blockLocation.getBlockX() >> 4;
            int chunkZ = blockLocation.getBlockZ() >> 4;
            land = api.getLandByChunk(world, chunkX, chunkZ);
        } catch (NoClassDefFoundError | Exception ex) {
            plugin.getLogger().severe(plugin.getLang().formatRaw("log.lands_check_error", "error", ex.getMessage()));
            player.sendMessage(plugin.getLang().format("flag.lands_check_error"));
            event.setCancelled(true);
            return;
        }

        if (land == null) {
            player.sendMessage(plugin.getLang().format("flag.only_in_lands"));
            event.setCancelled(true);
            return;
        }

        String landKey = land.getULID().toString();
        RegionModel region;
        if (systemId != null) {
            region = plugin.getDataManager().getRegion(systemId);
            if (region == null) {
                player.sendMessage(plugin.getLang().format("flag.not_bound"));
                event.setCancelled(true);
                return;
            }
        } else {
            String regionId = plugin.getDataManager().getRegionIdByLandId(landKey);
            if (regionId == null) {
                player.sendMessage(plugin.getLang().format("flag.no_region_in_land"));
                event.setCancelled(true);
                return;
            }
            region = plugin.getDataManager().getRegion(regionId);
            if (region == null) {
                player.sendMessage(plugin.getLang().format("flag.not_bound"));
                event.setCancelled(true);
                return;
            }
            if (!universalFlag && typedRegionType != null && (region.getType() == null || !region.getType().equalsIgnoreCase(typedRegionType))) {
                player.sendMessage(plugin.getLang().format("flag.wrong_type", "flag_type", typedRegionType, "region_type", String.valueOf(region.getType())));
                event.setCancelled(true);
                return;
            }
            systemId = region.getSystemId();
        }

        if (!region.isCaptureEnabled()) {
            player.sendMessage(plugin.getLang().format("flag.capture_disabled"));
            event.setCancelled(true);
            return;
        }

        if (!landKey.equalsIgnoreCase(region.getLandId())) {
            player.sendMessage(plugin.getLang().format("flag.wrong_land"));
            event.setCancelled(true);
            return;
        }

        if (plugin.isLandInWar(land)) {
            player.sendMessage(plugin.getLang().format("flag.land_in_war"));
            event.setCancelled(true);
            return;
        }

        if (!plugin.isFlagPlacementAtConfiguredPole(region, blockLocation)) {
            player.sendMessage(plugin.getLang().format("flag.wrong_flagpole_point"));
            event.setCancelled(true);
            return;
        }

        FileConfiguration config = plugin.getConfig();
        int maxRegions = config.getInt("max-regions-per-player", 3);
        UUID playerId = player.getUniqueId();
        long ownedRegions = plugin.getDataManager().getRegions().stream()
                .filter(r -> r.getCurrentOwner() != null && r.getCurrentOwner().equals(playerId))
                .count();

        if (ownedRegions >= maxRegions) {
            player.sendMessage(plugin.getLang().format("flag.limit_reached", "max", String.valueOf(maxRegions)));

            event.setCancelled(true);
            return;
        }

        long cooldownHours = plugin.getConfig().getLong("region-cooldown-hours", 24L);
        long cooldownMillis = cooldownHours <= 0 ? 0L : cooldownHours * 60L * 60L * 1000L;
        if (cooldownMillis > 0L) {
            long lastCapture = region.getLastCaptureTime();
            long now = System.currentTimeMillis();
            if (lastCapture > 0 && now - lastCapture < cooldownMillis) {
                long remaining = cooldownMillis - (now - lastCapture);
                long remHours = remaining / (60L * 60L * 1000L);
                long remMinutes = (remaining / (60L * 1000L)) % 60L;
                player.sendMessage(plugin.getLang().format("flag.cooldown", "hours", String.valueOf(remHours), "minutes", String.valueOf(remMinutes)));

                event.setCancelled(true);
                return;
            }
        }

        if (plugin.hasActiveCaptureForRegion(region.getSystemId())) {
            player.sendMessage(plugin.getLang().format("flag.already_capturing"));
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
        plugin.consumePlacedFlagItem(player, event.getHand());
        Location anchor = plugin.resolveCaptureFlagAnchor(region, blockLocation);
        plugin.startCapture(player, region, anchor);
    }

    @EventHandler
    public void onBlockDamage(BlockDamageEvent event) {
        if (!plugin.isActiveFlagCollisionBlock(event.getBlock().getLocation())) {
            return;
        }
        event.setCancelled(true);
        boolean creative = event.getPlayer() != null && event.getPlayer().getGameMode().name().equalsIgnoreCase("CREATIVE");
        plugin.damageFlagPoleAt(event.getBlock().getLocation(), event.getPlayer(), creative);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (plugin.isActiveFlagCollisionBlock(event.getBlock().getLocation())) {
            event.setCancelled(true);
            boolean creative = event.getPlayer() != null && event.getPlayer().getGameMode().name().equalsIgnoreCase("CREATIVE");
            plugin.damageFlagPoleAt(event.getBlock().getLocation(), event.getPlayer(), creative);
            return;
        }
        plugin.cancelCaptureByFlagLocation(event.getBlock().getLocation());
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        if (!event.getPlayer().isSneaking()) {
            return;
        }
        if (!plugin.isActiveFlagCollisionBlock(event.getClickedBlock().getLocation())) {
            return;
        }
        if (plugin.tryRepairFlagPoleAt(event.getClickedBlock().getLocation(), event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) {
            return;
        }
        plugin.handleCaptureKill(killer, victim);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.clearWeekendBypass(event.getPlayer().getUniqueId());
    }
}
