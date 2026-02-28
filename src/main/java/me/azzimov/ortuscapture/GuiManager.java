package me.azzimov.ortuscapture;

import me.angeschossen.lands.api.LandsIntegration;
import me.angeschossen.lands.api.land.Land;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class GuiManager {

    private final OrtusCapture plugin;

    public GuiManager(OrtusCapture plugin) {
        this.plugin = plugin;
    }

    public void openRegionMenu(Player player, RegionModel region) {
        if (player == null || region == null) {
            return;
        }

        String title = plugin.getLang().format("gui.region.title", "id", region.getSystemId());
        Inventory inv = Bukkit.createInventory(new OrtusMenuHolder(OrtusMenuHolder.MenuType.REGION, region.getSystemId()), 27, title);
        fillFrame(inv);

        inv.setItem(10, button(Material.NAME_TAG,
                plugin.getLang().format("gui.region.system_id_name"),
                List.of(
                        plugin.getLang().format("gui.region.system_id_l1", "id", region.getSystemId()),
                        plugin.getLang().format("gui.region.type_l1", "type", safe(region.getType()))
                )));

        inv.setItem(12, button(region.isCaptureEnabled() ? Material.LIME_DYE : Material.GRAY_DYE,
                plugin.getLang().format("gui.region.capture_name"),
                List.of(
                        plugin.getLang().format("gui.region.capture_state", "state",
                                plugin.getLang().get(region.isCaptureEnabled() ? "values.enabled" : "values.disabled")),
                        plugin.getLang().format("gui.region.active_capture", "count",
                                plugin.hasActiveCaptureForRegion(region.getSystemId()) ? "1" : "0")
                )));

        inv.setItem(14, button(Material.RECOVERY_COMPASS,
                plugin.getLang().format("gui.region.flagpole_name"),
                buildFlagpoleLore(region)));

        inv.setItem(16, button(Material.PLAYER_HEAD,
                plugin.getLang().format("gui.region.owner_name"),
                buildOwnerLore(region)));

        player.openInventory(inv);
    }

    public void openAdminMenu(Player player) {
        if (player == null) {
            return;
        }
        Inventory inv = Bukkit.createInventory(new OrtusMenuHolder(OrtusMenuHolder.MenuType.ADMIN, null), 27,
                plugin.getLang().format("gui.admin.title"));
        fillFrame(inv);

        inv.setItem(10, button(Material.COMPASS,
                plugin.getLang().format("gui.admin.regions_name"),
                List.of(
                        plugin.getLang().format("gui.admin.regions_count", "count", String.valueOf(plugin.getDataManager().getRegions().size())),
                        plugin.getLang().format("gui.admin.regions_hint")
                )));

        inv.setItem(11, button(Material.GOAT_HORN,
                plugin.getLang().format("gui.admin.wars_name"),
                List.of(
                        plugin.getLang().format("gui.admin.wars_active_l1", "count", String.valueOf(plugin.getActiveCaptureCount())),
                        plugin.getLang().format("gui.admin.wars_hook_l1",
                                "state", plugin.getLang().get(plugin.getLandsHook() != null && plugin.getLandsHook().isWarApiAvailable()
                                        ? "values.enabled" : "values.disabled"))
                )));

        inv.setItem(13, button(Material.REDSTONE,
                plugin.getLang().format("gui.admin.reload_name"),
                List.of(
                        plugin.getLang().format("gui.admin.reload_l1"),
                        plugin.getLang().format("gui.admin.reload_l2")
                )));

        inv.setItem(15, button(Material.WRITABLE_BOOK,
                plugin.getLang().format("gui.admin.about_name"),
                List.of(
                        plugin.getLang().format("gui.admin.about_author_l1"),
                        plugin.getLang().format("gui.admin.about_version_l1", "version", plugin.getDescription().getVersion())
                )));

        inv.setItem(16, button(Material.CLOCK,
                plugin.getLang().format("gui.admin.server_day_name"),
                buildDayLore()));

        player.openInventory(inv);
    }

    public void handleClick(Player player, OrtusMenuHolder holder, int rawSlot) {
        if (player == null || holder == null) {
            return;
        }
        if (holder.getType() == OrtusMenuHolder.MenuType.ADMIN) {
            if (rawSlot == 13) {
                if (!player.hasPermission("ortuscapture.admin")) {
                    player.sendMessage(plugin.getLang().format("messages.no_permission"));
                    return;
                }
                plugin.reloadOrtusConfig();
                player.sendMessage(plugin.getLang().format("reload.success"));
                openAdminMenu(player);
                return;
            }
            if (rawSlot == 10) {
                player.sendMessage(plugin.getLang().format("gui.admin.regions_click_hint"));
                return;
            }
            if (rawSlot == 11) {
                player.sendMessage(plugin.getLang().format("gui.admin.wars_click_hint",
                        "count", String.valueOf(plugin.getActiveCaptureCount())));
                return;
            }
            if (rawSlot == 15) {
                player.sendMessage(plugin.getLang().format("gui.admin.about_click_hint"));
                return;
            }
            if (rawSlot == 16) {
                player.sendMessage(plugin.getLang().format("gui.admin.server_day_click_hint"));
            }
            return;
        }

        if (holder.getType() == OrtusMenuHolder.MenuType.REGION) {
            RegionModel region = plugin.getDataManager().getRegion(holder.getRegionId());
            if (region == null) {
                player.closeInventory();
                player.sendMessage(plugin.getLang().format("gui.region.not_found"));
                return;
            }
            if (rawSlot == 14) {
                player.sendMessage(plugin.getLang().format("gui.region.flagpole_chat", "coords", formatFlagpole(region)));
            } else if (rawSlot == 12) {
                player.sendMessage(plugin.getLang().format("gui.region.capture_chat",
                        "id", region.getSystemId(),
                        "state", plugin.getLang().get(region.isCaptureEnabled() ? "values.enabled" : "values.disabled")));
            }
        }
    }

    private void fillFrame(Inventory inv) {
        ItemStack filler = filler();
        for (int slot = 0; slot < inv.getSize(); slot++) {
            inv.setItem(slot, filler);
        }
        // Симметричное "окно" по центру (3 строки, рамка по краям)
        int[] openSlots = {10, 11, 12, 13, 14, 15, 16};
        for (int slot : openSlots) {
            inv.setItem(slot, null);
        }
    }

    private ItemStack filler() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack button(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(lore);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private List<String> buildFlagpoleLore(RegionModel region) {
        List<String> lore = new ArrayList<>();
        lore.add(plugin.getLang().format("gui.region.flagpole_l1", "coords", formatFlagpole(region)));
        lore.add(plugin.getLang().format("gui.region.flagpole_l2"));
        return lore;
    }

    private List<String> buildOwnerLore(RegionModel region) {
        List<String> lore = new ArrayList<>();
        String owner = plugin.getLang().get("values.none");
        if (region.getCurrentOwner() != null) {
            String name = Bukkit.getOfflinePlayer(region.getCurrentOwner()).getName();
            owner = name != null ? name : region.getCurrentOwner().toString();
        }
        lore.add(plugin.getLang().format("gui.region.owner_l1", "owner", owner));
        lore.add(plugin.getLang().format("gui.region.cooldown_l1", "cooldown", formatCooldown(region)));
        return lore;
    }

    private List<String> buildDayLore() {
        DayOfWeek day = LocalDate.now().getDayOfWeek();
        boolean weekend = day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
        List<String> lore = new ArrayList<>();
        lore.add(plugin.getLang().format("gui.admin.server_day_l1", "day", getDayName(day)));
        lore.add(plugin.getLang().format("gui.admin.server_day_l2",
                "kind", plugin.getLang().get(weekend ? "values.weekend" : "values.weekday")));
        return lore;
    }

    private String getDayName(DayOfWeek day) {
        String key = "days." + day.name().toLowerCase(Locale.ROOT);
        String raw = plugin.getLang().getRaw(key);
        return raw != null ? org.bukkit.ChatColor.translateAlternateColorCodes('&', raw) : day.name();
    }

    private String formatFlagpole(RegionModel region) {
        if (region == null || !region.hasFlagpolePoint()) {
            return plugin.getLang().get("values.none");
        }
        return region.getFlagpoleWorld() + " " + region.getFlagpoleX() + " " + region.getFlagpoleY() + " " + region.getFlagpoleZ();
    }

    private String formatCooldown(RegionModel region) {
        if (region == null) {
            return "-";
        }
        long cooldownHours = plugin.getConfig().getLong("region-cooldown-hours", 24L);
        long cooldownMillis = Math.max(0L, cooldownHours) * 60L * 60L * 1000L;
        long lastCapture = region.getLastCaptureTime();
        if (cooldownMillis <= 0L || lastCapture <= 0L) {
            return plugin.getLang().get("placeholders.cooldown_zero");
        }
        long now = System.currentTimeMillis();
        long remaining = cooldownMillis - (now - lastCapture);
        if (remaining <= 0L) {
            return plugin.getLang().get("placeholders.cooldown_zero");
        }
        long h = remaining / (60L * 60L * 1000L);
        long m = (remaining / (60L * 1000L)) % 60L;
        return plugin.getLang().format("placeholders.cooldown_format", "hours", String.valueOf(h), "minutes", String.valueOf(m));
    }

    public void openRegionMenuForCurrentLand(Player player) {
        if (player == null) {
            return;
        }
        if (plugin.getLandsHook() == null || !plugin.getLandsHook().isAvailable()) {
            player.sendMessage(plugin.getLang().format("info.lands_unavailable"));
            return;
        }
        try {
            LandsIntegration api = plugin.getLandsHook().getLandsApi();
            Land land = api.getLandByChunk(player.getWorld(), player.getLocation().getBlockX() >> 4, player.getLocation().getBlockZ() >> 4);
            if (land == null) {
                player.sendMessage(plugin.getLang().format("info.not_in_land"));
                return;
            }
            String regionId = plugin.getDataManager().getRegionIdByLandId(land.getULID().toString());
            if (regionId == null) {
                player.sendMessage(plugin.getLang().format("info.not_registered"));
                return;
            }
            RegionModel region = plugin.getDataManager().getRegion(regionId);
            if (region == null) {
                player.sendMessage(plugin.getLang().format("gui.region.not_found"));
                return;
            }
            openRegionMenu(player, region);
        } catch (Exception ex) {
            player.sendMessage(plugin.getLang().format("gui.open_error"));
        }
    }

    private String safe(String value) {
        return value == null ? "-" : value;
    }
}
