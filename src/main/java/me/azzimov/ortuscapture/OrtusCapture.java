package me.azzimov.ortuscapture;

import me.angeschossen.lands.api.LandsIntegration;
import me.angeschossen.lands.api.applicationframework.util.ULID;
import me.angeschossen.lands.api.land.Land;
import me.angeschossen.lands.api.land.enums.LandType;
import me.angeschossen.lands.api.player.LandPlayer;
import me.angeschossen.lands.api.player.TrustedPlayer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.joml.Matrix4f;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class OrtusCapture extends JavaPlugin {

    private LangManager lang;
    private DataManager dataManager;
    private LandsHook landsHook;
    private CraftManager craftManager;
    private FlagStyleManager flagStyleManager;
    private GuiManager guiManager;
    private NamespacedKey flagKey;
    private NamespacedKey flagTypeKey;
    private NamespacedKey flagKindKey;
    private NamespacedKey flagVersionKey;
    private NamespacedKey flagColorKey;

    // Active capture sessions by player UUID
    private final Map<UUID, CaptureSession> activeCaptures = new HashMap<>();
    // Collision blocks of active display flags -> capture owner UUID
    private final Map<String, UUID> activeFlagCollisionIndex = new HashMap<>();
    // Players with debug-bypass for weekend/day restrictions
    private final Set<UUID> debugWeekendBypass = new HashSet<>();
    private UUID systemOwnerId;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        lang = new LangManager(this);
        lang.load();

        dataManager = new DataManager(this);
        landsHook = new LandsHook(this);
        flagKey = new NamespacedKey(this, "flag_system_id");
        flagTypeKey = new NamespacedKey(this, "flag_region_type");
        flagKindKey = new NamespacedKey(this, "flag_kind");
        flagVersionKey = new NamespacedKey(this, "flag_version");
        flagColorKey = new NamespacedKey(this, "flag_color");
        craftManager = new CraftManager(this);
        flagStyleManager = new FlagStyleManager(this);
        guiManager = new GuiManager(this);
        initSystemOwnerId();
        saveLandsHelperResources();
        flagStyleManager.reload();
        craftManager.reload();

        getServer().getPluginManager().registerEvents(new FlagListener(this), this);
        getServer().getPluginManager().registerEvents(new SystemOwnerGuard(this), this);
        getServer().getPluginManager().registerEvents(new GuiListener(guiManager), this);
        getServer().getPluginManager().registerEvents(craftManager, this);
        if (landsHook != null && landsHook.isWarApiAvailable()) {
            try {
                getServer().getPluginManager().registerEvents(new LandsWarsListener(this), this);
                getLogger().info(lang.formatRaw("log.wars_hooked"));
            } catch (Throwable throwable) {
                getLogger().warning(lang.formatRaw("log.wars_hook_failed", "error", throwable.getMessage()));
            }
        } else {
            getLogger().warning(lang.formatRaw("log.wars_not_available"));
        }

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                new OrtusPlaceholderExpansion(this).register();
                getLogger().info(lang.formatRaw("log.placeholder_registered"));
            } catch (Throwable throwable) {
                getLogger().warning(lang.formatRaw("log.placeholder_failed", "error", throwable.getMessage()));
            }
        } else {
            getLogger().warning(lang.formatRaw("log.placeholder_not_found"));
        }

        getLogger().info(lang.formatRaw("log.plugin_enabled"));
    }

    @Override
    public void onDisable() {
        // Cleanup BossBars and armor stands
        for (CaptureSession session : activeCaptures.values()) {
            if (session.getBossBar() != null) {
                session.getBossBar().removeAll();
            }
            if (session.getArmorStand() != null && !session.getArmorStand().isDead()) {
                session.getArmorStand().remove();
            }
            removeFlagPole(session.getFlagPole());
        }
        activeCaptures.clear();
        activeFlagCollisionIndex.clear();
        debugWeekendBypass.clear();
        if (craftManager != null) {
            craftManager.unregisterRecipes();
        }
    }

    // ===================== COMMANDS =====================

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("oc")) {
            return false;
        }
        return handleCommand(sender, args);
    }

    private boolean handleCommand(CommandSender sender, String[] args) {
        if (args == null || args.length == 0) {
            send(sender, "messages.use_help");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        boolean isPlayer = sender instanceof Player;
        Player player = isPlayer ? (Player) sender : null;
        boolean isAdmin = !isPlayer || sender.hasPermission("ortuscapture.admin");

        if (sub.equals("help")) {
            sendHelp(sender, isAdmin);
            return true;
        }

        if (sub.equals("status")) {
            boolean landsOk = landsHook != null && landsHook.isAvailable();
            boolean warsOk = landsHook != null && landsHook.isWarApiAvailable();
            boolean papiOk = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
            boolean dataOk = dataManager != null;
            int regionCount = dataOk ? dataManager.getRegions().size() : 0;
            int active = activeCaptures.size();

            java.time.DayOfWeek day = java.time.LocalDate.now().getDayOfWeek();
            boolean weekend = day == java.time.DayOfWeek.SATURDAY || day == java.time.DayOfWeek.SUNDAY;
            boolean captureAllowedToday = isCaptureAllowedToday();

            String dayName = getDayName(day);
            String dayType = lang.get(weekend ? "values.weekend" : "values.weekday");
            String allowed = lang.get(captureAllowedToday ? "values.allowed" : "values.forbidden");

            String ok = lang.get("values.ok");
            String err = lang.get("values.err");
            String landsStatus = landsOk ? ok : lang.get("values.no");
            String papiStatus = papiOk ? ok : lang.get("values.not_found");

            send(sender, "status.header");
            sender.sendMessage(lang.format("status.plugin_ok", "status", ok));
            sender.sendMessage(lang.format("status.hook_lands", "status", landsStatus));
            sender.sendMessage(lang.format("status.hook_wars", "status", warsOk ? ok : lang.get("values.not_found")));
            sender.sendMessage(lang.format("status.hook_papi", "status", papiStatus));
            sender.sendMessage(lang.format("status.mini_tests",
                    "data", dataOk ? ok : err,
                    "lands", landsOk ? ok : err));
            sender.sendMessage(lang.format("status.records", "count", String.valueOf(regionCount)));
            sender.sendMessage(lang.format("status.active", "count", String.valueOf(active)));
            sender.sendMessage(lang.get("status.author"));
            sender.sendMessage(lang.format("status.day",
                    "day", dayName,
                    "day_type", dayType,
                    "allowed", allowed));
            return true;
        }

        if (sub.equals("gui")) {
            if (!isPlayer) {
                send(sender, "messages.only_player");
                return true;
            }
            if (!isAdmin && !player.hasPermission("ortuscapture.player")) {
                send(sender, "messages.no_permission");
                return true;
            }
            if (guiManager == null) {
                send(sender, "gui.open_error");
                return true;
            }
            if (args.length == 1) {
                if (isAdmin) {
                    guiManager.openAdminMenu(player);
                } else {
                    guiManager.openRegionMenuForCurrentLand(player);
                }
                return true;
            }
            if ("admin".equalsIgnoreCase(args[1])) {
                if (!isAdmin) {
                    send(sender, "messages.no_permission");
                    return true;
                }
                guiManager.openAdminMenu(player);
                return true;
            }
            RegionModel targetRegion = dataManager.getRegion(args[1]);
            if (targetRegion == null) {
                send(sender, "gui.region.not_found");
                return true;
            }
            guiManager.openRegionMenu(player, targetRegion);
            return true;
        }

        if (sub.equals("debug")) {
            if (!isAdmin) {
                send(sender, "messages.no_permission");
                return true;
            }
            if (!isPlayer) {
                send(sender, "messages.only_player");
                return true;
            }
            if (args.length < 2) {
                send(sender, "debug.usage");
                return true;
            }

            String mode = args[1].toLowerCase(Locale.ROOT);
            Player p = player;
            if (mode.equals("admin")) {
                boolean enabled = !hasWeekendBypass(p.getUniqueId());
                setWeekendBypass(p.getUniqueId(), enabled);
                send(p, "debug.toggled", "state", lang.get(enabled ? "values.enabled" : "values.disabled"));
                return true;
            }
            if (mode.equals("status")) {
                boolean bypass = hasWeekendBypass(p.getUniqueId());
                send(p, "debug.status_header");
                p.sendMessage(lang.format("debug.status_bypass", "state", lang.get(bypass ? "values.enabled" : "values.disabled")));
                p.sendMessage(lang.format("debug.status_active_capture", "state", lang.get(hasActiveCapture(p.getUniqueId()) ? "values.yes" : "values.no")));
                return true;
            }
            if (mode.equals("off")) {
                clearWeekendBypass(p.getUniqueId());
                send(p, "debug.disabled");
                return true;
            }

            send(sender, "debug.usage");
            return true;
        }

        if (sub.equals("setup")) {
            if (!isAdmin) {
                send(sender, "messages.no_permission");
                return true;
            }
            if (!isPlayer) {
                send(sender, "messages.only_player");
                return true;
            }
            if (args.length < 3) {
                send(sender, "setup.usage");
                return true;
            }
            if (landsHook == null || !landsHook.isAvailable()) {
                send(sender, "setup.lands_unavailable");
                return true;
            }

            String systemId = args[1];
            String type = args[2];

            type = normalizeRegionType(systemId, type);

            List<String> allowedTypes = getConfig().getStringList("region-types");
            if (allowedTypes == null || allowedTypes.isEmpty()) {
                send(sender, "setup.types_empty");
                return true;
            }

            boolean typeValid = isValidRegionType(type, allowedTypes);
            if (!typeValid) {
                send(sender, "setup.type_invalid", "types", String.join(", ", allowedTypes));
                return true;
            }

            try {
                LandsIntegration api = landsHook.getLandsApi();
                Location loc = player.getLocation();
                World world = loc.getWorld();
                int chunkX = loc.getBlockX() >> 4;
                int chunkZ = loc.getBlockZ() >> 4;

                getLogger().info(lang.formatRaw("log.debug_chunk", "x", String.valueOf(chunkX), "z", String.valueOf(chunkZ)));

                Land land = api.getLandByChunk(world, chunkX, chunkZ);
                if (land == null) {
                    send(sender, "setup.not_in_land");
                    return true;
                }

                String landKey = land.getULID().toString();
                getLogger().info(lang.formatRaw("log.debug_land_found", "id", landKey));

                String existingId = dataManager.getRegionIdByLandId(landKey);
                if (existingId != null && !existingId.equalsIgnoreCase(systemId)) {
                    send(sender, "setup.already_bound", "id", existingId);
                    return true;
                }

                RegionModel region = new RegionModel(systemId, landKey, type, true);
                dataManager.addOrUpdateRegion(region);

                try {
                    LandPlayer lp = api.getLandPlayer(player.getUniqueId());
                    if (lp != null) {
                        land.setLandType(LandType.ADMIN, lp);
                    }
                } catch (Exception ex) {
                    getLogger().severe(lang.formatRaw("log.setup_admin_fail", "error", ex.getMessage()));
                }
                if (systemOwnerId != null) {
                    try {
                        land.setOwner(systemOwnerId);
                    } catch (Exception ex) {
                        getLogger().severe(lang.formatRaw("log.setup_owner_fail", "error", ex.getMessage()));
                    }
                }
                syncLandsCaptureFlag(land, region, true, "setup");

                send(sender, "setup.success", "system_id", systemId, "land_name", land.getName());
            } catch (NoClassDefFoundError | Exception ex) {
                getLogger().severe(lang.formatRaw("log.setup_error", "error", ex.getMessage()));
                send(sender, "setup.error");
            }
            return true;
        }

        if (sub.equals("create")) {
            if (!isAdmin) {
                send(sender, "messages.no_permission");
                return true;
            }
            if (!isPlayer) {
                send(sender, "messages.only_player");
                return true;
            }
            if (args.length < 3) {
                send(sender, "create.usage");
                return true;
            }
            if (landsHook == null || !landsHook.isAvailable()) {
                send(sender, "create.lands_unavailable");
                return true;
            }

            String systemId = args[1];
            String type = normalizeRegionType(systemId, args[2]);
            List<String> allowedTypes = getConfig().getStringList("region-types");
            if (allowedTypes == null || allowedTypes.isEmpty()) {
                send(sender, "setup.types_empty");
                return true;
            }
            if (!isValidRegionType(type, allowedTypes)) {
                send(sender, "setup.type_invalid", "types", String.join(", ", allowedTypes));
                return true;
            }
            if (dataManager.getRegion(systemId) != null) {
                send(sender, "create.id_exists", "id", systemId);
                return true;
            }

            String landName = args.length >= 4 ? joinArgs(args, 3) : systemId;
            Player commandPlayer = player;
            Location createLocation = commandPlayer.getLocation().clone();

            try {
                LandsIntegration api = landsHook.getLandsApi();
                World world = createLocation.getWorld();
                if (world == null) {
                    send(sender, "create.error");
                    return true;
                }

                Land existingLand = api.getLandByChunk(world, createLocation.getBlockX() >> 4, createLocation.getBlockZ() >> 4);
                if (existingLand != null) {
                    send(sender, "create.chunk_busy", "land", existingLand.getName());
                    return true;
                }

                LandPlayer actor = api.getLandPlayer(commandPlayer.getUniqueId());
                if (actor == null) {
                    send(sender, "create.actor_unavailable");
                    return true;
                }

                send(sender, "create.processing", "name", landName);
                final String finalType = type;
                final String finalLandName = landName;
                Land.of(landName, LandType.ADMIN, createLocation, actor, true, true)
                        .whenComplete((createdLand, throwable) -> Bukkit.getScheduler().runTask(this, () -> {
                            if (throwable != null || createdLand == null) {
                                String error = throwable != null ? String.valueOf(throwable.getMessage()) : "unknown";
                                getLogger().severe(lang.formatRaw("log.create_error", "error", error));
                                send(commandPlayer, "create.error");
                                return;
                            }

                            try {
                                try {
                                    createdLand.setLandType(LandType.ADMIN, actor);
                                } catch (Exception ex) {
                                    getLogger().severe(lang.formatRaw("log.create_admin_fail", "error", ex.getMessage()));
                                }

                                if (systemOwnerId != null) {
                                    try {
                                        createdLand.setOwner(systemOwnerId);
                                    } catch (Exception ex) {
                                        getLogger().severe(lang.formatRaw("log.create_owner_fail", "error", ex.getMessage()));
                                    }
                                }

                                RegionModel region = new RegionModel(systemId, createdLand.getULID().toString(), finalType, true);
                                dataManager.addOrUpdateRegion(region);
                                syncLandsCaptureFlag(createdLand, region, true, "create");

                                send(commandPlayer, "create.success",
                                        "system_id", systemId,
                                        "type", finalType,
                                        "land_name", createdLand.getName() != null ? createdLand.getName() : finalLandName);
                            } catch (Exception ex) {
                                getLogger().severe(lang.formatRaw("log.create_error", "error", ex.getMessage()));
                                send(commandPlayer, "create.error");
                            }
                        }));
            } catch (Exception ex) {
                getLogger().severe(lang.formatRaw("log.create_error", "error", ex.getMessage()));
                send(sender, "create.error");
            }
            return true;
        }

        if (sub.equals("giveflag")) {
            if (!isAdmin) {
                send(sender, "messages.no_permission");
                return true;
            }
            if (args.length < 2) {
                send(sender, "giveflag.usage");
                return true;
            }

            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                send(sender, "giveflag.player_not_found", "player", args[1]);
                return true;
            }

            if (args.length < 3) {
                ItemStack flagItem = createUniversalFlagItem();
                target.getInventory().addItem(flagItem);
                send(target, "giveflag.success_target_universal");
                send(sender, "giveflag.success_sender_universal", "player", target.getName());
                return true;
            }

            String systemId = args[2];
            RegionModel region = dataManager.getRegion(systemId);
            if (region == null) {
                send(sender, "giveflag.id_not_found", "id", systemId);
                return true;
            }

            ItemStack flagItem = createFlagItem(systemId);
            target.getInventory().addItem(flagItem);
            send(target, "giveflag.success_target", "id", systemId);
            send(sender, "giveflag.success_sender", "player", target.getName(), "id", systemId);
            return true;
        }

        if (sub.equals("info")) {
            if (isPlayer && !isAdmin && !player.hasPermission("ortuscapture.player")) {
                send(sender, "messages.no_permission");
                return true;
            }
            if (!isPlayer) {
                send(sender, "messages.only_player");
                return true;
            }
            if (landsHook == null || !landsHook.isAvailable()) {
                send(sender, "info.lands_unavailable");
                return true;
            }

            try {
                LandsIntegration api = landsHook.getLandsApi();
                Location loc = player.getLocation();
                World world = loc.getWorld();
                int chunkX = loc.getBlockX() >> 4;
                int chunkZ = loc.getBlockZ() >> 4;

                getLogger().info(lang.formatRaw("log.debug_chunk", "x", String.valueOf(chunkX), "z", String.valueOf(chunkZ)));

                Land land = api.getLandByChunk(world, chunkX, chunkZ);
                if (land == null) {
                    send(sender, "info.not_in_land");
                    return true;
                }

                String landKey = land.getULID().toString();
                getLogger().info(lang.formatRaw("log.debug_land_found", "id", landKey));

                String regionId = dataManager.getRegionIdByLandId(landKey);
                RegionModel registered = regionId != null ? dataManager.getRegion(regionId) : null;
                String noneValue = lang.getRaw("values.none");
                String systemIdInfo = registered != null ? registered.getSystemId() : (regionId != null ? regionId : noneValue);
                String typeInfo = registered != null ? registered.getType() : noneValue;

                java.util.UUID ownerUuid = land.getOwnerUID();
                String ownerName = ownerUuid != null ? Bukkit.getOfflinePlayer(ownerUuid).getName() : null;
                if (ownerName == null || ownerName.isEmpty()) {
                    ownerName = lang.get("values.unknown");
                }
                String ownerUuidText = ownerUuid != null
                        ? lang.formatRaw("info.owner_uuid", "uuid", ownerUuid.toString())
                        : "";

                send(sender, "info.land_line", "name", land.getName(), "id", landKey);
                sender.sendMessage(lang.format("info.owner_line", "name", ownerName, "uuid", ownerUuidText));
                sender.sendMessage(lang.format("info.admin_line", "state", lang.get(isAdministrativeLand(land) ? "values.yes" : "values.no")));

                if (regionId == null) {
                    send(sender, "info.not_registered");
                } else {
                    sender.sendMessage(lang.format("info.registered", "id", systemIdInfo, "type", typeInfo));
                }
            } catch (NoClassDefFoundError | Exception ex) {
                getLogger().severe(lang.formatRaw("log.info_error", "error", ex.getMessage()));
                send(sender, "info.error");
            }
            return true;
        }

        if (sub.equals("reload")) {
            if (!isAdmin) {
                send(sender, "messages.no_permission");
                return true;
            }

            reloadOrtusConfig();
            send(sender, "reload.success");
            return true;
        }

        if (sub.equals("capture")) {
            if (!isAdmin) {
                send(sender, "messages.no_permission");
                return true;
            }
            if (args.length < 3) {
                send(sender, "capturecmd.usage");
                return true;
            }

            String systemId = args[1];
            RegionModel region = dataManager.getRegion(systemId);
            if (region == null) {
                send(sender, "capturecmd.id_not_found", "id", systemId);
                return true;
            }

            Boolean enabled = parseOnOff(args[2]);
            if (enabled == null) {
                send(sender, "capturecmd.usage");
                return true;
            }

            region.setCaptureEnabled(enabled);
            dataManager.addOrUpdateRegion(region);
            if (landsHook != null && landsHook.isAvailable()) {
                try {
                    LandsIntegration api = landsHook.getLandsApi();
                    Land land = api.getLandByULID(ULID.fromString(region.getLandId()));
                    if (land != null) {
                        syncLandsCaptureFlag(land, region, enabled, "capturecmd");
                    }
                } catch (Exception ex) {
                    getLogger().warning(lang.formatRaw("log.capture_flag_sync_exception", "error", ex.getMessage()));
                }
            }
            send(sender, enabled ? "capturecmd.enabled" : "capturecmd.disabled", "id", systemId);
            return true;
        }

        if (sub.equals("point")) {
            if (!isAdmin) {
                send(sender, "messages.no_permission");
                return true;
            }
            if (!isPlayer) {
                send(sender, "messages.only_player");
                return true;
            }
            if (args.length < 4) {
                send(sender, "point.usage");
                return true;
            }
            String action = args[1].toLowerCase(Locale.ROOT);
            String pointName = args[2].toLowerCase(Locale.ROOT);
            String regionId = args[3];
            if (!"flagpole".equals(pointName)) {
                send(sender, "point.only_flagpole");
                return true;
            }
            RegionModel region = dataManager.getRegion(regionId);
            if (region == null) {
                send(sender, "point.id_not_found", "id", regionId);
                return true;
            }

            if ("clear".equals(action)) {
                region.clearFlagpolePoint();
                dataManager.addOrUpdateRegion(region);
                send(sender, "point.cleared", "id", regionId);
                return true;
            }
            if (!"set".equals(action)) {
                send(sender, "point.usage");
                return true;
            }

            Block target = player.getTargetBlockExact(8);
            Location pointLoc = target != null ? target.getLocation() : player.getLocation().getBlock().getLocation();
            if (pointLoc.getWorld() == null) {
                send(sender, "point.error");
                return true;
            }
            region.setFlagpolePoint(pointLoc.getWorld().getName(), pointLoc.getBlockX(), pointLoc.getBlockY(), pointLoc.getBlockZ());
            dataManager.addOrUpdateRegion(region);
            send(sender, "point.set_flagpole",
                    "id", regionId,
                    "world", pointLoc.getWorld().getName(),
                    "x", String.valueOf(pointLoc.getBlockX()),
                    "y", String.valueOf(pointLoc.getBlockY()),
                    "z", String.valueOf(pointLoc.getBlockZ()));
            return true;
        }

        if (sub.equals("regionreset")) {
            if (!isAdmin) {
                send(sender, "messages.no_permission");
                return true;
            }
            if (args.length < 2) {
                send(sender, "regionreset.usage");
                return true;
            }

            String systemId = args[1];
            RegionModel region = dataManager.getRegion(systemId);
            if (region == null) {
                send(sender, "regionreset.id_not_found", "id", systemId);
                return true;
            }

            try {
                cancelCaptureForRegion(systemId);
                dataManager.removeRegion(systemId);
                send(sender, "regionreset.success", "id", systemId);
            } catch (Exception ex) {
                getLogger().severe(lang.formatRaw("log.regionreset_error", "error", ex.getMessage()));
                send(sender, "regionreset.error");
            }
            return true;
        }

        if (sub.equals("delete")) {
            if (!isAdmin) {
                send(sender, "messages.no_permission");
                return true;
            }
            if (args.length < 2) {
                send(sender, "delete.usage");
                return true;
            }
            if (landsHook == null || !landsHook.isAvailable()) {
                send(sender, "delete.lands_unavailable");
                return true;
            }

            String systemId = args[1];
            RegionModel region = dataManager.getRegion(systemId);
            if (region == null) {
                send(sender, "delete.id_not_found", "id", systemId);
                return true;
            }

            try {
                cancelCaptureForRegion(systemId);
                LandsIntegration api = landsHook.getLandsApi();
                Land land = getLandByUlidQuiet(api, region.getLandId());
                if (land == null) {
                    dataManager.removeRegion(systemId);
                    send(sender, "delete.success_no_land", "id", systemId);
                    return true;
                }

                Player actorPlayer = sender instanceof Player ? (Player) sender : null;
                if (!tryDeleteLand(api, land, actorPlayer)) {
                    send(sender, "delete.land_delete_failed", "id", systemId, "land", land.getName() != null ? land.getName() : region.getLandId());
                    return true;
                }

                dataManager.removeRegion(systemId);
                send(sender, "delete.success", "id", systemId, "land", land.getName() != null ? land.getName() : region.getLandId());
            } catch (Exception ex) {
                getLogger().severe(lang.formatRaw("log.delete_error", "error", ex.getMessage()));
                send(sender, "delete.error");
            }
            return true;
        }

        if (sub.equals("edit")) {
            if (!isAdmin) {
                send(sender, "messages.no_permission");
                return true;
            }
            if (args.length < 3) {
                send(sender, "edit.usage");
                return true;
            }

            String systemId = args[1];
            RegionModel region = dataManager.getRegion(systemId);
            if (region == null) {
                send(sender, "edit.id_not_found", "id", systemId);
                return true;
            }

            String newType = normalizeRegionType(systemId, args[2]);
            List<String> allowedTypes = getConfig().getStringList("region-types");
            if (allowedTypes == null || allowedTypes.isEmpty()) {
                send(sender, "setup.types_empty");
                return true;
            }
            if (!isValidRegionType(newType, allowedTypes)) {
                send(sender, "setup.type_invalid", "types", String.join(", ", allowedTypes));
                return true;
            }

            String oldType = region.getType();
            region.setType(newType);
            dataManager.addOrUpdateRegion(region);
            send(sender, "edit.success", "id", systemId, "old_type", String.valueOf(oldType), "new_type", newType);
            return true;
        }

        send(sender, "messages.unknown_command");
        return true;
    }

    private void sendHelp(CommandSender sender, boolean isAdmin) {
        send(sender, "help.header");
        sender.sendMessage(lang.get("help.player_section"));
        for (String line : lang.getList("help.player_lines")) {
            sender.sendMessage(line);
        }
        if (isAdmin) {
            sender.sendMessage(lang.get("help.admin_section"));
            for (String line : lang.getList("help.admin_lines")) {
                sender.sendMessage(line);
            }
            sender.sendMessage(lang.get("gui.help_admin_line"));
            sender.sendMessage(lang.get("point.usage"));
            return;
        }
        sender.sendMessage(lang.get("gui.help_player_line"));
    }

    private void send(CommandSender sender, String key, String... pairs) {
        sender.sendMessage(lang.format(key, pairs));
    }

    private String getDayName(java.time.DayOfWeek day) {
        if (day == null) {
            String noneValue = lang.getRaw("values.none");
            return noneValue != null ? noneValue : "-";
        }
        String key = "days." + day.name().toLowerCase(Locale.ROOT);
        String name = lang.getRaw(key);
        return name != null ? name : day.name();
    }

    public boolean isCaptureAllowedToday() {
        EnumSet<java.time.DayOfWeek> allowedDays = getConfiguredAllowedDays();
        if (allowedDays.isEmpty()) {
            return true;
        }
        return allowedDays.contains(java.time.LocalDate.now().getDayOfWeek());
    }

    public EnumSet<java.time.DayOfWeek> getConfiguredAllowedDays() {
        Object raw = getConfig().get("allowed-days");
        EnumSet<java.time.DayOfWeek> result = EnumSet.noneOf(java.time.DayOfWeek.class);
        if (raw == null) {
            return result;
        }

        if (raw instanceof String stringValue) {
            parseAllowedDayTokens(stringValue, result);
            return result;
        }

        if (raw instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (item != null) {
                    parseAllowedDayTokens(String.valueOf(item), result);
                }
            }
            return result;
        }

        parseAllowedDayTokens(String.valueOf(raw), result);
        return result;
    }

    private void parseAllowedDayTokens(String rawValue, EnumSet<java.time.DayOfWeek> target) {
        if (rawValue == null || rawValue.isBlank()) {
            return;
        }

        for (String token : rawValue.split(",")) {
            String normalized = token == null ? "" : token.trim().toLowerCase(Locale.ROOT);
            if (normalized.isEmpty()) {
                continue;
            }

            if (normalized.equals("weekday") || normalized.equals("weekdays")) {
                target.add(java.time.DayOfWeek.MONDAY);
                target.add(java.time.DayOfWeek.TUESDAY);
                target.add(java.time.DayOfWeek.WEDNESDAY);
                target.add(java.time.DayOfWeek.THURSDAY);
                target.add(java.time.DayOfWeek.FRIDAY);
                continue;
            }

            if (normalized.equals("weekend") || normalized.equals("weekends")) {
                target.add(java.time.DayOfWeek.SATURDAY);
                target.add(java.time.DayOfWeek.SUNDAY);
                continue;
            }

            try {
                target.add(java.time.DayOfWeek.valueOf(normalized.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private String normalizeRegionType(String systemId, String type) {
        String normalized = type == null ? "" : type;
        if (systemId == null) {
            return normalized;
        }
        String lowerId = systemId.toLowerCase(Locale.ROOT);
        if (lowerId.startsWith("mine_")) {
            return "mine";
        }
        if (lowerId.startsWith("farm_")) {
            return "farm";
        }
        if (lowerId.startsWith("fort_")) {
            return "fort";
        }
        if (lowerId.startsWith("outpost_")) {
            return "outpost";
        }
        return normalized;
    }

    private boolean isValidRegionType(String type, List<String> allowedTypes) {
        if (type == null || allowedTypes == null || allowedTypes.isEmpty()) {
            return false;
        }
        for (String t : allowedTypes) {
            if (t != null && t.equalsIgnoreCase(type)) {
                return true;
            }
        }
        return false;
    }

    private String joinArgs(String[] args, int fromIndex) {
        if (args == null || fromIndex >= args.length) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = fromIndex; i < args.length; i++) {
            if (i > fromIndex) {
                builder.append(' ');
            }
            builder.append(args[i]);
        }
        return builder.toString();
    }

    private Boolean parseOnOff(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        if (normalized.equals("on") || normalized.equals("true") || normalized.equals("enable") || normalized.equals("enabled")) {
            return true;
        }
        if (normalized.equals("off") || normalized.equals("false") || normalized.equals("disable") || normalized.equals("disabled")) {
            return false;
        }
        return null;
    }

    // ===================== TAB-COMPLETE =====================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("oc")) {
            return null;
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = List.of("help", "status", "info", "gui", "setup", "create", "capture", "giveflag", "point", "edit", "regionreset", "delete", "reload", "debug");
            String current = args[0].toLowerCase(Locale.ROOT);
            for (String sub : subCommands) {
                if (sub.toLowerCase(Locale.ROOT).startsWith(current)) {
                    completions.add(sub);
                }
            }
            return completions;
        }

        if (args[0].equalsIgnoreCase("setup") || args[0].equalsIgnoreCase("create")) {
            if (args.length == 3) {
                List<String> types = getConfig().getStringList("region-types");
                String current = args[2].toLowerCase(Locale.ROOT);
                for (String type : types) {
                    if (type.toLowerCase(Locale.ROOT).startsWith(current)) {
                        completions.add(type);
                    }
                }
                return completions;
            }
        } else if (args[0].equalsIgnoreCase("capture")) {
            if (args.length == 2) {
                String current = args[1].toLowerCase(Locale.ROOT);
                for (String id : dataManager.getSystemIds()) {
                    if (id.toLowerCase(Locale.ROOT).startsWith(current)) {
                        completions.add(id);
                    }
                }
                return completions;
            } else if (args.length == 3) {
                List<String> states = List.of("on", "off");
                String current = args[2].toLowerCase(Locale.ROOT);
                for (String state : states) {
                    if (state.startsWith(current)) {
                        completions.add(state);
                    }
                }
                return completions;
            }
        } else if (args[0].equalsIgnoreCase("giveflag")) {
            if (args.length == 2) {
                String current = args[1].toLowerCase(Locale.ROOT);
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase(Locale.ROOT).startsWith(current)) {
                        completions.add(player.getName());
                    }
                }
                return completions;
            } else if (args.length == 3) {
                String current = args[2].toLowerCase(Locale.ROOT);
                for (String id : dataManager.getSystemIds()) {
                    if (id.toLowerCase(Locale.ROOT).startsWith(current)) {
                        completions.add(id);
                    }
                }
                return completions;
            }
        } else if (args[0].equalsIgnoreCase("gui")) {
            if (args.length == 2) {
                String current = args[1].toLowerCase(Locale.ROOT);
                if ("admin".startsWith(current)) {
                    completions.add("admin");
                }
                for (String id : dataManager.getSystemIds()) {
                    if (id.toLowerCase(Locale.ROOT).startsWith(current)) {
                        completions.add(id);
                    }
                }
                return completions;
            }
        } else if (args[0].equalsIgnoreCase("point")) {
            if (args.length == 2) {
                for (String mode : List.of("set", "clear")) {
                    if (mode.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                        completions.add(mode);
                    }
                }
                return completions;
            }
            if (args.length == 3) {
                if ("flagpole".startsWith(args[2].toLowerCase(Locale.ROOT))) {
                    completions.add("flagpole");
                }
                return completions;
            }
            if (args.length == 4) {
                String current = args[3].toLowerCase(Locale.ROOT);
                for (String id : dataManager.getSystemIds()) {
                    if (id.toLowerCase(Locale.ROOT).startsWith(current)) {
                        completions.add(id);
                    }
                }
                return completions;
            }
        } else if (args[0].equalsIgnoreCase("edit")) {
            if (args.length == 2) {
                String current = args[1].toLowerCase(Locale.ROOT);
                for (String id : dataManager.getSystemIds()) {
                    if (id.toLowerCase(Locale.ROOT).startsWith(current)) {
                        completions.add(id);
                    }
                }
                return completions;
            }
            if (args.length == 3) {
                String current = args[2].toLowerCase(Locale.ROOT);
                for (String type : getConfig().getStringList("region-types")) {
                    if (type != null && type.toLowerCase(Locale.ROOT).startsWith(current)) {
                        completions.add(type);
                    }
                }
                return completions;
            }
        } else if (args[0].equalsIgnoreCase("regionreset") || args[0].equalsIgnoreCase("delete")) {
            if (args.length == 2) {
                String current = args[1].toLowerCase(Locale.ROOT);
                for (String id : dataManager.getSystemIds()) {
                    if (id.toLowerCase(Locale.ROOT).startsWith(current)) {
                        completions.add(id);
                    }
                }
                return completions;
            }
        } else if (args[0].equalsIgnoreCase("debug")) {
            if (args.length == 2) {
                List<String> debugModes = List.of("admin", "status", "off");
                String current = args[1].toLowerCase(Locale.ROOT);
                for (String mode : debugModes) {
                    if (mode.startsWith(current)) {
                        completions.add(mode);
                    }
                }
                return completions;
            }
        }

        return completions;
    }

    // ===================== API FOR OTHER CLASSES =====================

    public DataManager getDataManager() {
        return dataManager;
    }

    public LandsHook getLandsHook() {
        return landsHook;
    }

    public NamespacedKey getFlagKey() {
        return flagKey;
    }

    public NamespacedKey getFlagTypeKey() {
        return flagTypeKey;
    }

    public NamespacedKey getFlagColorKey() {
        return flagColorKey;
    }

    public FlagStyleManager getFlagStyleManager() {
        return flagStyleManager;
    }

    public NamespacedKey getFlagKindKey() {
        return flagKindKey;
    }

    public NamespacedKey getFlagVersionKey() {
        return flagVersionKey;
    }

    public LangManager getLang() {
        return lang;
    }

    public boolean hasWeekendBypass(UUID playerId) {
        return debugWeekendBypass.contains(playerId);
    }

    public void setWeekendBypass(UUID playerId, boolean enabled) {
        if (enabled) {
            debugWeekendBypass.add(playerId);
            return;
        }
        debugWeekendBypass.remove(playerId);
    }

    public void clearWeekendBypass(UUID playerId) {
        debugWeekendBypass.remove(playerId);
    }

    public Location getRegionFlagpoleLocation(RegionModel region) {
        if (region == null || !region.hasFlagpolePoint()) {
            return null;
        }
        World world = Bukkit.getWorld(region.getFlagpoleWorld());
        if (world == null) {
            return null;
        }
        return new Location(world, region.getFlagpoleX(), region.getFlagpoleY(), region.getFlagpoleZ());
    }

    public boolean isFlagPlacementAtConfiguredPole(RegionModel region, Location placedBlockLocation) {
        if (region == null || placedBlockLocation == null || !region.hasFlagpolePoint()) {
            return true;
        }
        Location pole = getRegionFlagpoleLocation(region);
        if (pole == null || pole.getWorld() == null || placedBlockLocation.getWorld() == null) {
            return true;
        }
        return pole.getWorld().equals(placedBlockLocation.getWorld())
                && pole.getBlockX() == placedBlockLocation.getBlockX()
                && pole.getBlockY() == placedBlockLocation.getBlockY()
                && pole.getBlockZ() == placedBlockLocation.getBlockZ();
    }

    public Location resolveCaptureFlagAnchor(RegionModel region, Location placedBlockLocation) {
        Location pole = getRegionFlagpoleLocation(region);
        return pole != null ? pole : placedBlockLocation;
    }

    public boolean consumePlacedFlagItem(Player player, EquipmentSlot hand) {
        if (player == null || player.getGameMode().name().equalsIgnoreCase("CREATIVE")) {
            return true;
        }
        if (hand == null) {
            return true;
        }
        ItemStack stack = hand == EquipmentSlot.OFF_HAND ? player.getInventory().getItemInOffHand() : player.getInventory().getItemInMainHand();
        if (stack == null || stack.getType() == Material.AIR) {
            return false;
        }
        int amount = stack.getAmount();
        if (amount <= 1) {
            if (hand == EquipmentSlot.OFF_HAND) {
                player.getInventory().setItemInOffHand(null);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
        } else {
            stack.setAmount(amount - 1);
        }
        player.updateInventory();
        return true;
    }

    public String getFlagColorForType(String regionType) {
        return flagStyleManager != null ? flagStyleManager.getColorKeyForType(regionType) : "purple";
    }

    public void logCapture(String message) {
        if (message == null || message.isEmpty()) {
            return;
        }

        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().warning(lang.formatRaw("log.history_dir_fail"));
            return;
        }

        File historyFile = new File(getDataFolder(), "history.log");
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String line = "[" + timestamp + "] " + message + System.lineSeparator();

        try (FileWriter writer = new FileWriter(historyFile, true)) {
            writer.write(line);
        } catch (IOException ex) {
            getLogger().warning(lang.formatRaw("log.history_write_fail", "error", ex.getMessage()));
        }
    }

    private boolean isTitleEnabled() {
        return getConfig().getBoolean("ui.title.enabled", true);
    }

    private boolean isActionBarEnabled() {
        return getConfig().getBoolean("ui.actionbar.enabled", true);
    }

    private void sendCaptureTitle(Player player, String titleKey, String subtitleKey, String... pairs) {
        if (player == null || !player.isOnline() || !isTitleEnabled()) {
            return;
        }
        int fadeIn = Math.max(0, getConfig().getInt("ui.title.fade-in", 10));
        int stay = Math.max(0, getConfig().getInt("ui.title.stay", 40));
        int fadeOut = Math.max(0, getConfig().getInt("ui.title.fade-out", 10));
        String title = lang.format(titleKey, pairs);
        String subtitle = lang.format(subtitleKey, pairs);
        player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
    }

    private void sendCaptureActionBar(Player player, CaptureSession session, String regionId, int percent, int remainingSeconds, boolean paused) {
        if (player == null || !player.isOnline() || !isActionBarEnabled() || session == null) {
            return;
        }
        String message;
        if (session.getMode() == CaptureMode.POINTS) {
            message = lang.format("capture.actionbar_points",
                    "id", regionId,
                    "percent", String.valueOf(percent),
                    "current", String.valueOf(session.getCurrentPoints()),
                    "required", String.valueOf(session.getRequiredPoints()));
        } else if (paused) {
            message = lang.format("capture.actionbar_time_paused",
                    "id", regionId,
                    "percent", String.valueOf(percent),
                    "seconds", String.valueOf(Math.max(0, remainingSeconds)));
        } else {
            message = lang.format("capture.actionbar_time",
                    "id", regionId,
                    "percent", String.valueOf(percent),
                    "seconds", String.valueOf(Math.max(0, remainingSeconds)));
        }
        player.sendActionBar(message);
    }

    private void playConfiguredSound(Player player, String path, Sound fallback) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (!getConfig().getBoolean(path + ".enabled", true)) {
            return;
        }

        String soundName = getConfig().getString(path + ".name");
        Sound sound = fallback;
        if (soundName != null && !soundName.isEmpty()) {
            try {
                sound = Sound.valueOf(soundName.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                getLogger().warning("[OrtusCapture] Invalid sound in config (" + path + ".name): " + soundName);
            }
        }

        float volume = (float) getConfig().getDouble(path + ".volume", 1.0D);
        float pitch = (float) getConfig().getDouble(path + ".pitch", 1.0D);
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    private void broadcastLocalCaptureStart(Player captor, RegionModel region, Location center) {
        String path = "notifications.local-capture-start";
        if (!getConfig().getBoolean(path + ".enabled", true)) {
            return;
        }
        if (center == null || center.getWorld() == null) {
            return;
        }

        int radius = Math.max(1, getConfig().getInt(path + ".radius", 100));
        double radiusSq = (double) radius * radius;
        String regionName = region != null ? region.getSystemId() : "-";
        String regionType = region != null && region.getType() != null ? region.getType() : "-";

        try {
            if (landsHook != null && landsHook.isAvailable() && region != null) {
                Land land = getLandByUlidQuiet(landsHook.getLandsApi(), region.getLandId());
                if (land != null && land.getName() != null && !land.getName().isBlank()) {
                    regionName = land.getName();
                }
            }
        } catch (Exception ignored) {
        }

        for (Player target : center.getWorld().getPlayers()) {
            if (!target.isOnline()) {
                continue;
            }
            if (target.getLocation().distanceSquared(center) > radiusSq) {
                continue;
            }
            target.sendMessage(lang.format("announce.capture_start_local",
                    "region", regionName,
                    "id", region != null ? region.getSystemId() : "-",
                    "type", regionType,
                    "player", captor != null ? captor.getName() : "-"));
        }
    }

    public void playWarsStartHorn(Object event) {
        String path = "sounds.wars-start-horn";
        if (!getConfig().getBoolean(path + ".enabled", true)) {
            return;
        }
        Sound sound = resolveConfigSound(path + ".name", Sound.EVENT_RAID_HORN);
        float volume = (float) getConfig().getDouble(path + ".volume", 2.0D);
        float pitch = (float) getConfig().getDouble(path + ".pitch", 1.0D);
        int radius = Math.max(1, getConfig().getInt(path + ".radius", 80));

        Location center = tryExtractLocation(event, 0);
        if (center != null && center.getWorld() != null) {
            double radiusSq = (double) radius * radius;
            for (Player player : center.getWorld().getPlayers()) {
                if (player.isOnline() && player.getLocation().distanceSquared(center) <= radiusSq) {
                    player.playSound(center, sound, volume, pitch);
                }
            }
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isOnline()) {
                player.playSound(player.getLocation(), sound, volume, pitch);
            }
        }
    }

    private Sound resolveConfigSound(String path, Sound fallback) {
        String soundName = getConfig().getString(path);
        if (soundName == null || soundName.isBlank()) {
            return fallback;
        }
        try {
            return Sound.valueOf(soundName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            getLogger().warning("[OrtusCapture] Invalid sound in config (" + path + "): " + soundName);
            return fallback;
        }
    }

    private Location tryExtractLocation(Object source, int depth) {
        if (source == null || depth > 3) {
            return null;
        }
        if (source instanceof Location loc) {
            return loc.clone();
        }
        if (source instanceof Entity entity) {
            return entity.getLocation();
        }
        if (source instanceof Block block) {
            return block.getLocation().add(0.5, 0.5, 0.5);
        }
        if (source instanceof org.bukkit.Chunk chunk) {
            World world = chunk.getWorld();
            return new Location(world, (chunk.getX() << 4) + 8.0, world.getMinHeight() + 1.0, (chunk.getZ() << 4) + 8.0);
        }
        String[] methods = {"getLocation", "getChunk", "getBlock", "getFlag", "getCaptureFlag", "getPosition", "getPos"};
        for (String methodName : methods) {
            try {
                java.lang.reflect.Method method = source.getClass().getMethod(methodName);
                if (method.getParameterCount() == 0) {
                    Object nested = method.invoke(source);
                    Location found = tryExtractLocation(nested, depth + 1);
                    if (found != null) {
                        return found;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private void cancelCaptureForRegion(String regionId) {
        if (regionId == null || regionId.isBlank()) {
            return;
        }
        List<UUID> toCancel = new ArrayList<>();
        for (Map.Entry<UUID, CaptureSession> entry : activeCaptures.entrySet()) {
            CaptureSession session = entry.getValue();
            if (session != null && regionId.equalsIgnoreCase(session.getRegionId())) {
                toCancel.add(entry.getKey());
            }
        }
        for (UUID playerId : toCancel) {
            endCapture(playerId, false);
        }
    }

    private boolean tryDeleteLand(LandsIntegration api, Land land, Player actorPlayer) {
        if (land == null) {
            return false;
        }

        Object actorLandPlayer = null;
        if (api != null && actorPlayer != null) {
            try {
                actorLandPlayer = api.getLandPlayer(actorPlayer.getUniqueId());
            } catch (Exception ignored) {
            }
        }

        try {
            for (java.lang.reflect.Method method : land.getClass().getMethods()) {
                String name = method.getName().toLowerCase(Locale.ROOT);
                if (!name.contains("delete")) {
                    continue;
                }
                Class<?>[] params = method.getParameterTypes();
                try {
                    Object result;
                    if (params.length == 0) {
                        result = method.invoke(land);
                    } else if (params.length == 1 && actorLandPlayer != null && params[0].isInstance(actorLandPlayer)) {
                        result = method.invoke(land, actorLandPlayer);
                    } else {
                        continue;
                    }
                    if (result instanceof Boolean booleanResult) {
                        return booleanResult;
                    }
                    return true;
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }

        try {
            String cmd = "lands admin land " + land.getULID() + " delete confirm";
            return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        } catch (Exception ignored) {
            return false;
        }
    }

    private String applyCommandPlaceholders(String command, Player player, RegionModel region, Land land) {
        if (command == null) {
            return null;
        }
        String out = command;
        out = out.replace("{player}", player != null ? player.getName() : "");
        out = out.replace("{player_uuid}", player != null ? player.getUniqueId().toString() : "");
        out = out.replace("{region_id}", region != null ? region.getSystemId() : "");
        out = out.replace("{region_type}", region != null ? String.valueOf(region.getType()) : "");
        out = out.replace("{land_ulid}", land != null ? String.valueOf(land.getULID()) : "");
        out = out.replace("{land_name}", land != null && land.getName() != null ? land.getName() : "");
        String captureFlag = getConfig().getString("lands-capture-flag.flag-name", "capture");
        out = out.replace("{capture_flag}", captureFlag != null ? captureFlag : "capture");
        return out;
    }

    private boolean dispatchConsoleCommandTemplate(String template, Player player, RegionModel region, Land land, String logKeyOnFail) {
        String command = applyCommandPlaceholders(template, player, region, land);
        if (command == null || command.isBlank()) {
            return false;
        }
        boolean result = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        if (!result && logKeyOnFail != null) {
            getLogger().warning(lang.formatRaw(logKeyOnFail, "command", command));
        }
        return result;
    }

    private void syncLandsCaptureFlag(Land land, RegionModel region, boolean enabled, String source) {
        if (land == null || region == null) {
            return;
        }
        if (!getConfig().getBoolean("lands-capture-flag.enabled", true)) {
            return;
        }

        String templatePath = enabled ? "lands-capture-flag.set-true-command" : "lands-capture-flag.set-false-command";
        String template = getConfig().getString(templatePath);
        if (template == null || template.isBlank()) {
            return;
        }

        boolean ok = dispatchConsoleCommandTemplate(template, null, region, land, "log.capture_flag_sync_fail");
        if (!ok) {
            String command = applyCommandPlaceholders(template, null, region, land);
            getLogger().warning(lang.formatRaw("log.capture_flag_sync_fail_detailed",
                    "source", source == null ? "unknown" : source,
                    "command", command == null ? "" : command));
        }
    }

    private void spawnCaptureParticles(Location flagLocation, int elapsedSeconds) {
        if (flagLocation == null || flagLocation.getWorld() == null) {
            return;
        }

        double radius = 1.4D;
        Particle particle = elapsedSeconds % 4 == 0 ? resolveWitchParticle() : Particle.FLAME;
        for (int index = 0; index < 12; index++) {
            double angle = 2 * Math.PI * index / 12.0D;
            double x = flagLocation.getX() + 0.5D + radius * Math.cos(angle);
            double y = flagLocation.getY() + 1.1D;
            double z = flagLocation.getZ() + 0.5D + radius * Math.sin(angle);
            flagLocation.getWorld().spawnParticle(particle, x, y, z, 1, 0D, 0D, 0D, 0D);
        }
    }

    private Particle resolveWitchParticle() {
        try {
            return Particle.valueOf("SPELL_WITCH");
        } catch (IllegalArgumentException ignored) {
        }
        try {
            return Particle.valueOf("WITCH");
        } catch (IllegalArgumentException ignored) {
            return Particle.FLAME;
        }
    }

    private String formatLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return "unknown";
        }
        return location.getWorld().getName() + ":"
                + location.getBlockX() + ","
                + location.getBlockY() + ","
                + location.getBlockZ();
    }

    private boolean isAdministrativeLand(Land land) {
        if (land == null) {
            return false;
        }
        try {
            java.lang.reflect.Method isAdmin = land.getClass().getMethod("isAdmin");
            Object result = isAdmin.invoke(land);
            if (result instanceof Boolean) {
                return (Boolean) result;
            }
        } catch (Exception ignored) {
        }

        try {
            java.lang.reflect.Method getLandType = land.getClass().getMethod("getLandType");
            Object type = getLandType.invoke(land);
            return type != null && "ADMIN".equalsIgnoreCase(type.toString());
        } catch (Exception ignored) {
            return false;
        }
    }

    public ItemStack createFlagItem(String systemId) {
        RegionModel region = dataManager != null ? dataManager.getRegion(systemId) : null;
        String regionType = region != null ? region.getType() : null;
        String colorKey = getFlagColorForType(regionType);
        FlagStyleManager.FlagColorStyle style = flagStyleManager != null ? flagStyleManager.getStyleByColor(colorKey) : null;
        Material bannerMaterial = style != null ? style.bannerMaterial() : Material.WHITE_BANNER;

        ItemStack item = new ItemStack(bannerMaterial);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(lang.format("capture.flag_item_name", "id", systemId));
            if (flagStyleManager != null) {
                meta.setLore(flagStyleManager.buildFlagLore(true, systemId, regionType, colorKey));
            }
            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(flagKey, PersistentDataType.STRING, systemId);
            container.set(flagKindKey, PersistentDataType.STRING, "bound");
            container.set(flagVersionKey, PersistentDataType.INTEGER, 1);
            container.set(flagColorKey, PersistentDataType.STRING, colorKey);
            applyFlagItemGlow(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createUniversalFlagItem() {
        String colorKey = "red";
        FlagStyleManager.FlagColorStyle style = flagStyleManager != null ? flagStyleManager.getStyleByColor(colorKey) : null;
        Material bannerMaterial = style != null ? style.bannerMaterial() : Material.RED_BANNER;

        ItemStack item = new ItemStack(bannerMaterial);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(lang.format("capture.flag_item_name_universal"));
            if (flagStyleManager != null) {
                meta.setLore(flagStyleManager.buildUniversalFlagLore(colorKey));
            }
            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(flagKindKey, PersistentDataType.STRING, "universal");
            container.set(flagVersionKey, PersistentDataType.INTEGER, 1);
            container.set(flagColorKey, PersistentDataType.STRING, colorKey);
            applyFlagItemGlow(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createTypedFlagItem(String regionType) {
        String colorKey = getFlagColorForType(regionType);
        FlagStyleManager.FlagColorStyle style = flagStyleManager != null ? flagStyleManager.getStyleByColor(colorKey) : null;
        Material bannerMaterial = style != null ? style.bannerMaterial() : Material.WHITE_BANNER;

        ItemStack item = new ItemStack(bannerMaterial);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(lang.format("capture.flag_item_name_type", "type", regionType, "color", colorKey));
            if (flagStyleManager != null) {
                meta.setLore(flagStyleManager.buildFlagLore(false, null, regionType, colorKey));
            }
            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(flagTypeKey, PersistentDataType.STRING, regionType);
            container.set(flagKindKey, PersistentDataType.STRING, "typed");
            container.set(flagVersionKey, PersistentDataType.INTEGER, 1);
            container.set(flagColorKey, PersistentDataType.STRING, colorKey);
            applyFlagItemGlow(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void applyFlagItemGlow(ItemMeta meta) {
        if (meta == null) {
            return;
        }
        meta.addEnchant(Enchantment.UNBREAKING, 3, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
    }

    public void reloadOrtusConfig() {
        reloadConfig();
        if (lang != null) {
            lang.reload();
        }
        if (dataManager != null) {
            dataManager.reload();
        } else {
            dataManager = new DataManager(this);
        }
        initSystemOwnerId();
        saveLandsHelperResources();
        if (flagStyleManager != null) {
            flagStyleManager.reload();
        } else {
            flagStyleManager = new FlagStyleManager(this);
            flagStyleManager.reload();
        }
        if (craftManager != null) {
            craftManager.reload();
        } else {
            craftManager = new CraftManager(this);
            craftManager.reload();
        }
        getLogger().info(lang.formatRaw("log.config_reloaded"));
    }

    private void initSystemOwnerId() {
        String systemOwnerName = getConfig().getString("system-owner-name", "Wild_land");
        if (systemOwnerName == null || systemOwnerName.isEmpty()) {
            systemOwnerName = "Wild_land";
        }
        String key = ("OrtusCapture-SystemOwner:" + systemOwnerName).toLowerCase(Locale.ROOT);
        this.systemOwnerId = UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        getLogger().info(lang.formatRaw("log.system_owner_uuid", "name", systemOwnerName, "uuid", String.valueOf(systemOwnerId)));
    }

    private void saveLandsHelperResources() {
        try {
            saveResource("landsloadconfig/captor-role.yml", false);
            saveResource("landsloadconfig/README_lands_ru.txt", false);
            saveResource("landsloadconfig/CAPTURE_FLAG_GUIDE_RU.txt", false);
            saveResource("crafts.yml", false);
            saveResource("flags.yml", false);
            File docsDir = new File(getDataFolder(), "documentation");
            if (!docsDir.exists()) {
                //noinspection ResultOfMethodCallIgnored
                docsDir.mkdirs();
            }
            saveResource("documentation/ORTUSCAPTURE_COMMANDS_RU.md", true);
            saveResource("documentation/ORTUSCAPTURE_TECH_AUDIT_RU.md", true);
            saveResource("documentation/ORTUSCAPTURE_DEV_GUIDE_RU.md", true);
            saveResource("documentation/ORTUSCAPTURE_USER_GUIDE_RU.md", true);
            File testBotDoc = new File(docsDir, "TEST_BOT_RUN_RU.md");
            if (testBotDoc.exists()) {
                //noinspection ResultOfMethodCallIgnored
                testBotDoc.delete();
            }
        } catch (IllegalArgumentException ignored) {
        }
    }

    // ===================== CAPTURE MECHANIC =====================

    public boolean hasActiveCapture(UUID playerId) {
        return activeCaptures.containsKey(playerId);
    }

    public boolean hasActiveCaptureForRegion(String regionId) {
        if (regionId == null) {
            return false;
        }
        for (CaptureSession session : activeCaptures.values()) {
            if (regionId.equalsIgnoreCase(session.getRegionId())) {
                return true;
            }
        }
        return false;
    }

    public void cancelCaptureByFlagLocation(Location location) {
        if (location == null) {
            return;
        }
        CaptureSession collisionSession = getCaptureSessionByCollision(location);
        if (collisionSession != null) {
            endCapture(collisionSession.getPlayerUuid(), false);
            return;
        }
        for (Map.Entry<UUID, CaptureSession> entry : activeCaptures.entrySet()) {
            Location flagLoc = entry.getValue().getFlagLocation();
            if (flagLoc == null) {
                continue;
            }
            if (flagLoc.getWorld() != null
                    && flagLoc.getWorld().equals(location.getWorld())
                    && flagLoc.getBlockX() == location.getBlockX()
                    && flagLoc.getBlockY() == location.getBlockY()
                    && flagLoc.getBlockZ() == location.getBlockZ()) {
                endCapture(entry.getKey(), false);
                break;
            }
        }
    }

    private CaptureMode getConfiguredCaptureMode() {
        return CaptureMode.fromString(getConfig().getString("capture-mode", "TIME"));
    }

    private int getCapturePointsRequired() {
        int required = getConfig().getInt("capture-points-required", 100);
        return Math.max(1, required);
    }

    private int getCaptureDurationSeconds(RegionModel region) {
        int base = getConfig().getInt("capture-duration-seconds", 600);
        if (base <= 0) {
            base = 600;
        }
        if (region == null || region.getType() == null || region.getType().isEmpty()) {
            return base;
        }
        String path = "capture-duration-seconds-by-type." + region.getType().toLowerCase(Locale.ROOT);
        int override = getConfig().getInt(path, base);
        return override > 0 ? override : base;
    }

    private int getCapturePointsRequired(RegionModel region) {
        int base = getCapturePointsRequired();
        if (region == null || region.getType() == null || region.getType().isEmpty()) {
            return base;
        }
        String path = "capture-points-required-by-type." + region.getType().toLowerCase(Locale.ROOT);
        int override = getConfig().getInt(path, base);
        return override > 0 ? override : base;
    }

    private int getCapturePointsPerKill() {
        int points = getConfig().getInt("capture-points-per-kill", 10);
        return Math.max(1, points);
    }

    private Land getLandByUlidQuiet(LandsIntegration api, String landId) {
        if (api == null || landId == null || landId.isEmpty()) {
            return null;
        }
        try {
            return api.getLandByULID(ULID.fromString(landId));
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isLandInWarById(LandsIntegration api, String landId) {
        if (landsHook == null || !landsHook.isWarApiAvailable()) {
            return false;
        }
        Land land = getLandByUlidQuiet(api, landId);
        return landsHook.isLandInWar(land);
    }

    private boolean isPlayerInsideLand(LandsIntegration api, Player player, String regionLandId) {
        if (api == null || player == null || regionLandId == null) {
            return false;
        }
        Location loc = player.getLocation();
        World world = loc.getWorld();
        if (world == null) {
            return false;
        }
        Land currentLand = api.getLandByChunk(world, loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
        return currentLand != null && currentLand.getULID().toString().equalsIgnoreCase(regionLandId);
    }

    private boolean isDefenderForLand(Land land, UUID playerId, UUID captorId) {
        if (land == null || playerId == null) {
            return false;
        }
        if (playerId.equals(captorId)) {
            return false;
        }
        if (systemOwnerId != null && playerId.equals(systemOwnerId)) {
            return false;
        }

        UUID ownerId = land.getOwnerUID();
        if (ownerId != null && ownerId.equals(playerId)) {
            return true;
        }

        for (Object obj : land.getTrustedPlayers()) {
            if (!(obj instanceof TrustedPlayer)) {
                continue;
            }
            TrustedPlayer tp = (TrustedPlayer) obj;
            UUID trustedId = tp.getUID();
            if (trustedId != null && trustedId.equals(playerId)) {
                return true;
            }
        }
        return false;
    }

    private String toBlockKey(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        return location.getWorld().getName() + ":"
                + location.getBlockX() + ":"
                + location.getBlockY() + ":"
                + location.getBlockZ();
    }

    private CaptureSession getCaptureSessionByCollision(Location location) {
        String key = toBlockKey(location);
        if (key == null) {
            return null;
        }
        UUID playerId = activeFlagCollisionIndex.get(key);
        if (playerId == null) {
            return null;
        }
        return activeCaptures.get(playerId);
    }

    public boolean isLandInWar(Land land) {
        return landsHook != null && landsHook.isLandInWar(land);
    }

    public int getActiveCaptureCount() {
        return activeCaptures.size();
    }

    public int cancelCapturesConflictingWithWars() {
        if (landsHook == null || !landsHook.isAvailable() || !landsHook.isWarApiAvailable() || activeCaptures.isEmpty()) {
            return 0;
        }
        LandsIntegration api = landsHook.getLandsApi();
        List<UUID> toCancel = new ArrayList<>();
        for (Map.Entry<UUID, CaptureSession> entry : activeCaptures.entrySet()) {
            CaptureSession session = entry.getValue();
            if (session == null) {
                continue;
            }
            if (isLandInWarById(api, session.getLandId())) {
                toCancel.add(entry.getKey());
            }
        }
        for (UUID playerId : toCancel) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                player.sendMessage(lang.format("capture.cancelled_by_war"));
            }
            endCapture(playerId, false);
        }
        if (!toCancel.isEmpty()) {
            getLogger().info(lang.formatRaw("log.wars_cancelled_captures", "count", String.valueOf(toCancel.size())));
        }
        return toCancel.size();
    }

    public boolean isActiveFlagCollisionBlock(Location location) {
        return getCaptureSessionByCollision(location) != null;
    }

    public boolean damageFlagPoleAt(Location location, Player attacker, boolean creativeHit) {
        CaptureSession session = getCaptureSessionByCollision(location);
        if (session == null || session.getFlagPole() == null) {
            return false;
        }
        ActiveFlagPole flagPole = session.getFlagPole();
        RegionModel region = dataManager != null ? dataManager.getRegion(session.getRegionId()) : null;
        String regionType = region != null ? region.getType() : null;
        FlagStyleManager.FlagColorStyle style = flagStyleManager != null
                ? flagStyleManager.getStyleByColor(flagPole.getColorKey())
                : (flagStyleManager != null ? flagStyleManager.getStyleForType(regionType) : null);
        int damage = style != null
                ? (creativeHit ? style.damagePerHitCreative() : style.damagePerHit())
                : (creativeHit ? 20 : 5);
        playFlagHitSound(flagPole, style);
        int hp = flagPole.damage(damage);
        refreshFlagPoleHealthDisplay(session);

        if (attacker != null) {
            attacker.sendActionBar(lang.format("capture.flag_hp_actionbar",
                    "hp", String.valueOf(hp),
                    "max", String.valueOf(flagPole.getMaxHealth()),
                    "region", session.getRegionId()));
        }

        if (flagPole.isBroken()) {
            playFlagBreakBurst(flagPole);
            endCapture(session.getPlayerUuid(), false);
        }
        return true;
    }

    public boolean tryRepairFlagPoleAt(Location location, Player player) {
        if (player == null || !player.isSneaking()) {
            return false;
        }
        CaptureSession session = getCaptureSessionByCollision(location);
        if (session == null || session.getFlagPole() == null) {
            return false;
        }
        ActiveFlagPole flagPole = session.getFlagPole();
        if (flagPole.getHealth() >= flagPole.getMaxHealth()) {
            return true;
        }

        RegionModel region = dataManager != null ? dataManager.getRegion(session.getRegionId()) : null;
        String regionType = region != null ? region.getType() : null;
        FlagStyleManager.FlagColorStyle style = flagStyleManager != null
                ? flagStyleManager.getStyleByColor(flagPole.getColorKey())
                : (flagStyleManager != null ? flagStyleManager.getStyleForType(regionType) : null);
        long cooldownMs = style != null ? style.repairCooldownMs() : 800L;
        int repairAmount = style != null ? style.repairPerShiftRightClick() : 2;
        int repairRange = style != null ? style.repairRange() : 2;

        if (flagPole.getAnchor() != null) {
            Location center = flagPole.getAnchor().clone().add(0.5, 0.5, 0.5);
            if (player.getWorld() != null && center.getWorld() != null && !player.getWorld().equals(center.getWorld())) {
                player.sendActionBar(lang.format("flag.repair_too_far"));
                return true;
            }
            if (player.getLocation().distanceSquared(center) > (repairRange * repairRange)) {
                player.sendActionBar(lang.format("flag.repair_too_far"));
                return true;
            }
        }

        long now = System.currentTimeMillis();
        if (cooldownMs > 0 && now - flagPole.getLastRepairMillis() < cooldownMs) {
            return true;
        }
        flagPole.setLastRepairMillis(now);
        int hp = flagPole.repair(repairAmount);
        refreshFlagPoleHealthDisplay(session);
        player.sendActionBar(lang.format("capture.flag_repair_actionbar",
                "hp", String.valueOf(hp),
                "max", String.valueOf(flagPole.getMaxHealth()),
                "region", session.getRegionId()));
        return true;
    }

    private void refreshFlagPoleHealthDisplay(CaptureSession session) {
        if (session == null) {
            return;
        }
        RegionModel region = dataManager != null ? dataManager.getRegion(session.getRegionId()) : null;
        if (region == null) {
            return;
        }
        int percent;
        if (session.getMode() == CaptureMode.POINTS) {
            percent = (int) Math.round((session.getCurrentPoints() * 100.0) / session.getRequiredPoints());
            updateCaptureDisplay(session, region.getSystemId(), percent, false);
            return;
        }
        BossBar bar = session.getBossBar();
        percent = bar != null ? (int) Math.round(bar.getProgress() * 100.0) : 0;
        updateCaptureDisplay(session, region.getSystemId(), percent, session.isPausedByDefenders());
    }

    private ActiveFlagPole spawnDisplayFlagPole(RegionModel region, Location anchor) {
        if (region == null || anchor == null || anchor.getWorld() == null) {
            return null;
        }
        if (flagStyleManager == null) {
            return null;
        }
        FlagStyleManager.FlagColorStyle style = flagStyleManager.getStyleForType(region.getType());
        if (style == null) {
            return null;
        }

        List<Entity> entities = new ArrayList<>();
        List<Location> collisions = new ArrayList<>();
        World world = anchor.getWorld();

        boolean summonConfigured = style.summonCommand() != null && !style.summonCommand().isBlank();
        if (summonConfigured) {
            entities.addAll(spawnDisplayEntitiesFromSummonCommand(anchor, style.summonCommand()));
        }

        if (entities.isEmpty() && !summonConfigured) {
            for (FlagStyleManager.BlockPart part : style.parts()) {
                try {
                    Location spawnLoc = anchor.clone();
                    BlockDisplay display = world.spawn(spawnLoc, BlockDisplay.class, ent -> {
                        ent.setBlock(part.block().createBlockData());
                        Matrix4f matrix = new Matrix4f()
                                .translate((float) part.translateX(), (float) part.translateY(), (float) part.translateZ())
                                .scale(part.scaleX(), part.scaleY(), part.scaleZ());
                        ent.setTransformationMatrix(matrix);
                        ent.setPersistent(false);
                    });
                    entities.add(display);
                } catch (Exception ex) {
                    getLogger().warning("[OrtusCapture] Failed to spawn flag display part: " + ex.getMessage());
                }
            }
        }

        int collisionHeight = Math.max(1, flagStyleManager.getCollisionHeightBlocks());
        for (int y = 0; y < collisionHeight; y++) {
            Location blockLoc = new Location(world, anchor.getBlockX(), anchor.getBlockY() + 1 + y, anchor.getBlockZ());
            Block block = blockLoc.getBlock();
            if (block.getType() != Material.AIR && block.getType() != Material.BARRIER) {
                // Don't overwrite hard blocks. If point is invalid, just skip this collision segment.
                continue;
            }
            block.setType(Material.BARRIER, false);
            collisions.add(blockLoc);
        }

        ActiveFlagPole flagPole = new ActiveFlagPole(anchor, collisions, entities, style.colorKey(), style.maxHealth());
        return flagPole;
    }

    private void playFlagBreakBurst(ActiveFlagPole flagPole) {
        if (flagPole == null) {
            return;
        }
        Location anchor = flagPole.getAnchor();
        if (anchor == null || anchor.getWorld() == null) {
            return;
        }
        World world = anchor.getWorld();
        Location center = anchor.clone().add(0.5, 1.0, 0.5);
        Particle particle = resolveExplosionParticle();
        world.spawnParticle(particle, center, 1, 0.0, 0.0, 0.0, 0.0);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.1f, 1.0f);
        for (Entity entity : world.getNearbyEntities(center, 4.0, 3.0, 4.0)) {
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            if (living.isDead()) {
                continue;
            }
            org.bukkit.util.Vector push = living.getLocation().toVector().subtract(center.toVector());
            if (push.lengthSquared() < 0.0001) {
                push = new org.bukkit.util.Vector(0.0, 0.25, 0.0);
            } else {
                push.normalize().multiply(0.9).setY(0.35);
            }
            living.setVelocity(living.getVelocity().add(push));
        }
    }

    private void playFlagHitSound(ActiveFlagPole flagPole, FlagStyleManager.FlagColorStyle style) {
        if (flagPole == null) {
            return;
        }
        if (style != null && !style.hitSoundEnabled()) {
            return;
        }
        Location anchor = flagPole.getAnchor();
        if (anchor == null || anchor.getWorld() == null) {
            return;
        }
        String soundKey = style != null ? style.hitSound() : "BLOCK_ANVIL_HIT";
        float volume = style != null ? style.hitSoundVolume() : 1.8f;
        float pitch = style != null ? style.hitSoundPitch() : 0.9f;
        double radius = style != null ? style.hitSoundRadius() : 5.0D;

        Sound sound;
        try {
            sound = Sound.valueOf(soundKey);
        } catch (Exception ignored) {
            sound = Sound.BLOCK_ANVIL_HIT;
        }

        Location center = anchor.clone().add(0.5, 1.0, 0.5);
        for (Player online : anchor.getWorld().getPlayers()) {
            if (!online.isOnline() || online.isDead()) {
                continue;
            }
            if (online.getLocation().distanceSquared(center) > (radius * radius)) {
                continue;
            }
            online.playSound(center, sound, volume, pitch);
        }
    }

    private Particle resolveExplosionParticle() {
        try {
            return Particle.valueOf("EXPLOSION_EMITTER");
        } catch (IllegalArgumentException ignored) {
        }
        try {
            return Particle.valueOf("EXPLOSION");
        } catch (IllegalArgumentException ignored) {
            return Particle.FLAME;
        }
    }

    private List<Entity> spawnDisplayEntitiesFromSummonCommand(Location anchor, String rawSummonCommand) {
        if (anchor == null || anchor.getWorld() == null || rawSummonCommand == null || rawSummonCommand.isBlank()) {
            return List.of();
        }
        World world = anchor.getWorld();
        Location center = anchor.clone().add(0.5, 1.0, 0.5);
        Set<UUID> before = new HashSet<>();
        for (Entity entity : world.getNearbyEntities(center, 4.0, 4.0, 4.0, this::isDisplayEntity)) {
            before.add(entity.getUniqueId());
        }

        String command = rawSummonCommand.trim();
        command = command.replace("\r", " ").replace("\n", " ");
        command = command.replaceAll("\\s+", " ").trim();
        if (command.startsWith("/")) {
            command = command.substring(1);
        }
        String wrapped = String.format(Locale.US,
                "execute in %s positioned %.3f %.3f %.3f run %s",
                world.getKey(),
                anchor.getX(), anchor.getY(), anchor.getZ(),
                command);
        boolean ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), wrapped);
        if (!ok) {
            getLogger().warning("[OrtusCapture] summon-command returned false for display flag.");
            return List.of();
        }

        List<Entity> spawned = new ArrayList<>();
        for (Entity entity : world.getNearbyEntities(center, 4.0, 4.0, 4.0, this::isDisplayEntity)) {
            if (!before.contains(entity.getUniqueId())) {
                entity.setPersistent(false);
                spawned.add(entity);
            }
        }
        return spawned;
    }

    private boolean isDisplayEntity(Entity entity) {
        if (entity == null) {
            return false;
        }
        EntityType type = entity.getType();
        return type == EntityType.BLOCK_DISPLAY || type == EntityType.ITEM_DISPLAY;
    }

    private void attachFlagPoleToSession(CaptureSession session, ActiveFlagPole flagPole) {
        if (session == null || flagPole == null) {
            return;
        }
        session.setFlagPole(flagPole);
        for (Location loc : flagPole.getCollisionBlocks()) {
            String key = toBlockKey(loc);
            if (key != null) {
                activeFlagCollisionIndex.put(key, session.getPlayerUuid());
            }
        }
        refreshFlagPoleHealthDisplay(session);
    }

    private void removeFlagPole(ActiveFlagPole flagPole) {
        if (flagPole == null) {
            return;
        }
        for (Location loc : flagPole.getCollisionBlocks()) {
            String key = toBlockKey(loc);
            if (key != null) {
                activeFlagCollisionIndex.remove(key);
            }
            if (loc != null && loc.getWorld() != null) {
                Block block = loc.getBlock();
                if (block.getType() == Material.BARRIER) {
                    block.setType(Material.AIR, false);
                }
            }
        }
        for (Entity entity : flagPole.getDisplayEntities()) {
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
        }
    }

    private int countOnlineDefendersInLand(LandsIntegration api, String regionLandId, UUID captorId) {
        if (api == null || regionLandId == null || regionLandId.isEmpty()) {
            return 0;
        }
        Land land = getLandByUlidQuiet(api, regionLandId);
        if (land == null) {
            return 0;
        }

        int defenders = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            UUID onlineId = online.getUniqueId();
            if (!isDefenderForLand(land, onlineId, captorId)) {
                continue;
            }
            if (isPlayerInsideLand(api, online, regionLandId)) {
                defenders++;
            }
        }
        return defenders;
    }

    private void updateCaptureDisplay(CaptureSession session, String regionId, int percent, boolean paused) {
        BossBar bossBar = session.getBossBar();
        ArmorStand armorStand = session.getArmorStand();
        String percentText = String.valueOf(percent);
        ActiveFlagPole flagPole = session.getFlagPole();
        String hp = flagPole != null ? String.valueOf(flagPole.getHealth()) : "-";
        String hpMax = flagPole != null ? String.valueOf(flagPole.getMaxHealth()) : "-";
        String hpSuffix = flagPole != null ? " " + lang.format("capture.flag_hp_inline", "hp", hp, "hp_max", hpMax) : "";

        if (session.getMode() == CaptureMode.POINTS) {
            String current = String.valueOf(session.getCurrentPoints());
            String required = String.valueOf(session.getRequiredPoints());
            if (bossBar != null) {
                bossBar.setProgress(Math.max(0.0, Math.min(1.0, percent / 100.0D)));
                bossBar.setTitle(lang.format("capture.bossbar_title_points",
                        "id", regionId,
                        "percent", percentText,
                        "current", current,
                        "required", required,
                        "hp", hp,
                        "hp_max", hpMax) + hpSuffix);
            }
            if (armorStand != null && !armorStand.isDead()) {
                armorStand.setCustomName(lang.format("capture.armorstand_title_points",
                        "percent", percentText,
                        "current", current,
                        "required", required,
                        "hp", hp,
                        "hp_max", hpMax) + hpSuffix);
            }
            return;
        }

        if (bossBar != null) {
            bossBar.setProgress(Math.max(0.0, Math.min(1.0, percent / 100.0D)));
            String key = paused ? "capture.bossbar_title_paused" : "capture.bossbar_title";
            bossBar.setTitle(lang.format(key,
                    "id", regionId,
                    "percent", percentText,
                    "hp", hp,
                    "hp_max", hpMax) + hpSuffix);
        }
        if (armorStand != null && !armorStand.isDead()) {
            String key = paused ? "capture.armorstand_title_paused" : "capture.armorstand_title";
            armorStand.setCustomName(lang.format(key,
                    "percent", percentText,
                    "hp", hp,
                    "hp_max", hpMax) + hpSuffix);
        }
    }

    public void startCapture(Player player, RegionModel region, Location flagLocation) {
        UUID uuid = player.getUniqueId();
        if (activeCaptures.containsKey(uuid)) {
            player.sendMessage(lang.format("capture.already_active"));
            return;
        }

        CaptureMode mode = getConfiguredCaptureMode();
        int durationSeconds = getCaptureDurationSeconds(region);
        int totalSeconds = durationSeconds;
        int requiredPoints = getCapturePointsRequired(region);

        BossBar bossBar = Bukkit.createBossBar(
                mode == CaptureMode.POINTS
                        ? lang.format("capture.bossbar_title_points",
                        "id", region.getSystemId(),
                        "percent", "0",
                        "current", "0",
                        "required", String.valueOf(requiredPoints))
                        : lang.format("capture.bossbar_title", "id", region.getSystemId(), "percent", "0"),
                BarColor.RED,
                BarStyle.SOLID
        );
        bossBar.addPlayer(player);
        bossBar.setProgress(0.0);

        // Floating text above the flag
        Location armorStandLocation = flagLocation.clone().add(0.5, 2.0, 0.5);
        ArmorStand armorStand = armorStandLocation.getWorld().spawn(armorStandLocation, ArmorStand.class, stand -> {
            stand.setInvisible(true);
            stand.setMarker(true);
            stand.setGravity(false);
            stand.setCustomNameVisible(true);
            stand.setCustomName(mode == CaptureMode.POINTS
                    ? lang.format("capture.armorstand_title_points", "percent", "0", "current", "0", "required", String.valueOf(requiredPoints))
                    : lang.format("capture.armorstand_title", "percent", "0"));
        });

        CaptureSession session = new CaptureSession(
                uuid,
                region.getSystemId(),
                region.getLandId(),
                mode,
                System.currentTimeMillis(),
                bossBar,
                armorStand,
                flagLocation.clone(),
                requiredPoints
        );
        activeCaptures.put(uuid, session);
        ActiveFlagPole activeFlagPole = spawnDisplayFlagPole(region, flagLocation.clone());
        if (activeFlagPole != null) {
            attachFlagPoleToSession(session, activeFlagPole);
        }
        logCapture(lang.formatRaw("log.capture_start",
                "player", player.getName(),
                "uuid", uuid.toString(),
                "region", region.getSystemId(),
                "land", region.getLandId(),
                "location", formatLocation(flagLocation)));

        LandsIntegration api = landsHook != null ? landsHook.getLandsApi() : null;
        String regionLandId = region.getLandId();

        new BukkitRunnable() {
            int elapsedActiveSeconds = 0;
            int elapsedTicks = 0;

            @Override
            public void run() {
                if (!activeCaptures.containsKey(uuid) || activeCaptures.get(uuid) != session) {
                    cancel();
                    return;
                }
                Player p = Bukkit.getPlayer(uuid);
                if (p == null || !p.isOnline() || p.isDead()) {
                    endCapture(uuid, false);
                    cancel();
                    return;
                }

                if (api != null) {
                    if (!isPlayerInsideLand(api, p, regionLandId)) {
                        endCapture(uuid, false);
                        cancel();
                        return;
                    }
                    if (isLandInWarById(api, regionLandId)) {
                        p.sendMessage(lang.format("capture.cancelled_by_war"));
                        endCapture(uuid, false);
                        cancel();
                        return;
                    }
                }

                elapsedTicks++;

                if (mode == CaptureMode.TIME) {
                    boolean pauseOnDefenders = getConfig().getBoolean("pause-time-capture-while-defenders-present", true);
                    boolean paused = false;
                    if (pauseOnDefenders && api != null) {
                        paused = countOnlineDefendersInLand(api, regionLandId, uuid) > 0;
                    }
                    session.setPausedByDefenders(paused);

                    if (!paused) {
                        elapsedActiveSeconds++;
                    }

                    double progress = Math.min(1.0, (double) elapsedActiveSeconds / totalSeconds);
                    int percent = (int) Math.round(progress * 100.0);
                    updateCaptureDisplay(session, region.getSystemId(), percent, paused);
                    sendCaptureActionBar(p, session, region.getSystemId(), percent, totalSeconds - elapsedActiveSeconds, paused);

                    if (elapsedTicks % 2 == 0) {
                        spawnCaptureParticles(flagLocation, elapsedTicks);
                    }

                    if (elapsedActiveSeconds >= totalSeconds) {
                        endCapture(uuid, true);
                        cancel();
                    }
                    return;
                }

                session.setPausedByDefenders(false);
                int percent = (int) Math.round((session.getCurrentPoints() * 100.0) / session.getRequiredPoints());
                updateCaptureDisplay(session, region.getSystemId(), percent, false);
                sendCaptureActionBar(p, session, region.getSystemId(), percent, 0, false);
                if (elapsedTicks % 2 == 0) {
                    spawnCaptureParticles(flagLocation, elapsedTicks);
                }
            }
        }.runTaskTimer(this, 20L, 20L);

        player.sendMessage(lang.format("capture.start"));
        broadcastLocalCaptureStart(player, region, flagLocation);
        sendCaptureTitle(player, "capture.title_start", "capture.subtitle_start", "id", region.getSystemId());
        playConfiguredSound(player, "sounds.capture-start", Sound.EVENT_RAID_HORN);
    }

    public void handleCaptureKill(Player killer, Player victim) {
        if (killer == null || victim == null) {
            return;
        }

        UUID killerId = killer.getUniqueId();
        CaptureSession session = activeCaptures.get(killerId);
        if (session == null || session.getMode() != CaptureMode.POINTS) {
            return;
        }

        RegionModel region = dataManager.getRegion(session.getRegionId());
        if (region == null) {
            return;
        }
        if (landsHook == null || !landsHook.isAvailable()) {
            return;
        }

        LandsIntegration api;
        try {
            api = landsHook.getLandsApi();
        } catch (Exception ignored) {
            return;
        }

        if (!isPlayerInsideLand(api, killer, session.getLandId())) {
            return;
        }
        if (!isPlayerInsideLand(api, victim, session.getLandId())) {
            return;
        }

        Land land = getLandByUlidQuiet(api, session.getLandId());
        if (land == null) {
            return;
        }
        if (!isDefenderForLand(land, victim.getUniqueId(), killerId)) {
            return;
        }

        int gained = getCapturePointsPerKill();
        int current = session.addPoints(gained);
        int percent = (int) Math.round((current * 100.0) / session.getRequiredPoints());
        updateCaptureDisplay(session, region.getSystemId(), percent, false);
        sendCaptureActionBar(killer, session, region.getSystemId(), percent, 0, false);

        killer.sendMessage(lang.format("capture.points_add",
                "points", String.valueOf(gained),
                "current", String.valueOf(current),
                "required", String.valueOf(session.getRequiredPoints())));

        if (current >= session.getRequiredPoints()) {
            endCapture(killerId, true);
        }
    }

    public void endCapture(UUID playerId, boolean success) {
        CaptureSession session = activeCaptures.remove(playerId);
        if (session == null) {
            return;
        }
        clearWeekendBypass(playerId);
        String captureResult = success ? lang.getRaw("values.success") : lang.getRaw("values.fail");
        logCapture(lang.formatRaw("log.capture_end",
                "result", captureResult,
                "uuid", playerId.toString(),
                "region", session.getRegionId()));

        BossBar bar = session.getBossBar();
        if (bar != null) {
            bar.removeAll();
        }

        ArmorStand stand = session.getArmorStand();
        if (stand != null && !stand.isDead()) {
            stand.remove();
        }

        removeFlagPole(session.getFlagPole());

        Location flagLocation = session.getFlagLocation();
        if (flagLocation != null) {
            Material type = flagLocation.getBlock().getType();
            if (type.name().endsWith("_BANNER")) {
                flagLocation.getBlock().setType(Material.AIR);
            }
        }

        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return;
        }

        if (success) {
            RegionModel region = dataManager.getRegion(session.getRegionId());
            if (region != null) {
                processCaptureSuccess(player, region);
            }
            player.sendMessage(lang.format("capture.success", "id", session.getRegionId()));
            sendCaptureTitle(player, "capture.title_success", "capture.subtitle_success", "id", session.getRegionId());
            playConfiguredSound(player, "sounds.capture-success", Sound.UI_TOAST_CHALLENGE_COMPLETE);
        } else {
            player.sendMessage(lang.format("capture.failed"));
            sendCaptureTitle(player, "capture.title_failed", "capture.subtitle_failed", "id", session.getRegionId());
            playConfiguredSound(player, "sounds.capture-fail", Sound.ENTITY_VILLAGER_NO);
        }
    }

    /**
     * Called when a capture finished successfully.
     * Transfers control of the Land region to the capturing player using Lands API
     * and updates regions.yml with the new owner.
     */
    private void processCaptureSuccess(Player captor, RegionModel region) {
        if (landsHook == null || !landsHook.isAvailable()) {
            getLogger().warning(lang.formatRaw("log.capture_lands_unavailable"));
            return;
        }

        LandsIntegration api;
        try {
            api = landsHook.getLandsApi();
        } catch (Exception ex) {
            getLogger().severe(lang.formatRaw("log.capture_api_fail", "error", ex.getMessage()));
            return;
        }

        Land land;
        try {
            ULID ulid = ULID.fromString(region.getLandId());
            land = api.getLandByULID(ulid);
        } catch (Exception ex) {
            getLogger().severe(lang.formatRaw("log.capture_land_resolve_fail", "id", region.getLandId(), "error", ex.getMessage()));
            return;
        }

        if (land == null) {
            getLogger().warning(lang.formatRaw("log.capture_land_not_found", "id", region.getLandId()));
            return;
        }

        java.util.UUID captorId = captor.getUniqueId();
        java.util.UUID previousOwnerId = land.getOwnerUID();
        java.util.UUID ownerId = previousOwnerId;

        // Make land administrative and owned by system account (Wild_land)
        try {
            LandPlayer lp = api.getLandPlayer(captorId);
            if (lp != null) {
                land.setLandType(LandType.ADMIN, lp);
            }
        } catch (Exception ex) {
            getLogger().severe(lang.formatRaw("log.capture_set_admin_fail", "error", ex.getMessage()));
        }

        if (systemOwnerId != null && (ownerId == null || !ownerId.equals(systemOwnerId))) {
            try {
                land.setOwner(systemOwnerId);
                ownerId = systemOwnerId;
            } catch (Exception ex) {
                getLogger().severe(lang.formatRaw("log.capture_set_owner_fail", "error", ex.getMessage()));
            }
        }

        // Untrust all trusted players except owner and captor
        java.util.Collection<?> trusted = land.getTrustedPlayers();
        java.util.List<java.util.UUID> kicked = new java.util.ArrayList<>();

        for (Object obj : trusted) {
            if (!(obj instanceof TrustedPlayer)) {
                continue;
            }
            TrustedPlayer trustedPlayer = (TrustedPlayer) obj;
            java.util.UUID uuid = trustedPlayer.getUID();
            if (uuid == null) {
                continue;
            }
            if (uuid.equals(captorId)) {
                continue;
            }
            if (ownerId != null && uuid.equals(ownerId)) {
                continue;
            }

            if (land.untrustPlayer(uuid)) {
                kicked.add(uuid);
            }
        }

        // Trust captor and assign captor role via console-command pipeline (configurable).
        land.trustPlayer(captorId);
        if (getConfig().getBoolean("captor-role.auto-add-role-command.enabled", true)) {
            String addRoleTemplate = getConfig().getString("captor-role.auto-add-role-command.command",
                    "lands admin land {land_ulid} addrole captor confirm");
            dispatchConsoleCommandTemplate(addRoleTemplate, captor, region, land, "log.capture_addrole_fail");
        }

        String setRoleTemplate = getConfig().getString("captor-role.set-role-command",
                "lands admin land {land_ulid} setrole {player} captor");
        boolean setRoleResult = dispatchConsoleCommandTemplate(setRoleTemplate, captor, region, land, "log.capture_setrole_fail");

        for (String extraTemplate : getConfig().getStringList("captor-role.extra-console-commands")) {
            if (extraTemplate == null || extraTemplate.isBlank()) {
                continue;
            }
            dispatchConsoleCommandTemplate(extraTemplate, captor, region, land, "log.capture_role_extra_fail");
        }

        if (!setRoleResult) {
            String resolved = applyCommandPlaceholders(setRoleTemplate, captor, region, land);
            getLogger().warning(lang.formatRaw("log.capture_setrole_fail", "command", resolved));
        }
        logCapture(lang.formatRaw("log.capture_role",
                "player", captor.getName(),
                "uuid", captorId.toString(),
                "region", region.getSystemId(),
                "result", String.valueOf(setRoleResult)));

        // Notify kicked players (online only)
        for (java.util.UUID uuid : kicked) {
            Player kickedPlayer = Bukkit.getPlayer(uuid);
            if (kickedPlayer != null && kickedPlayer.isOnline()) {
                kickedPlayer.sendMessage(lang.format("capture.kicked", "id", region.getSystemId(), "player", captor.getName()));
            }
        }

        if (previousOwnerId != null
                && !previousOwnerId.equals(captorId)
                && (systemOwnerId == null || !previousOwnerId.equals(systemOwnerId))) {
            Player formerOwner = Bukkit.getPlayer(previousOwnerId);
            if (formerOwner != null && formerOwner.isOnline()) {
                formerOwner.sendMessage(lang.format("capture.former_owner_lost",
                        "id", region.getSystemId(),
                        "player", captor.getName()));
            }
        }

        // Inform captor about their control rights (conceptually)
        captor.sendMessage(lang.format("capture.role_assigned", "id", region.getSystemId()));
        captor.sendMessage(lang.format("capture.role_info"));
        playCaptureSuccessFireworks(captor);

        // Update current owner and capture time in regions.yml
        region.setCurrentOwner(captorId);
        region.setLastCaptureTime(System.currentTimeMillis());
        dataManager.addOrUpdateRegion(region);
    }

    private void playCaptureSuccessFireworks(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (!getConfig().getBoolean("effects.capture-success-fireworks.enabled", true)) {
            return;
        }
        int count = Math.max(1, getConfig().getInt("effects.capture-success-fireworks.count", 3));
        int power = Math.max(0, Math.min(2, getConfig().getInt("effects.capture-success-fireworks.power", 1)));
        Location base = player.getLocation().clone();
        for (int i = 0; i < count; i++) {
            Location spawn = base.clone().add((i % 2 == 0 ? 0.8 : -0.8), 0.2, (i == 1 ? 0.8 : (i == 2 ? -0.8 : 0.0)));
            Firework firework = (Firework) player.getWorld().spawnEntity(spawn, EntityType.FIREWORK_ROCKET);
            FireworkMeta meta = firework.getFireworkMeta();
            meta.setPower(power);
            meta.addEffect(FireworkEffect.builder()
                    .with(FireworkEffect.Type.BALL_LARGE)
                    .withColor(Color.ORANGE, Color.YELLOW, Color.RED)
                    .withFade(Color.WHITE)
                    .trail(true)
                    .flicker(true)
                    .build());
            firework.setFireworkMeta(meta);
        }
    }
}
