package me.azzimov.ortuscapture;

import org.bukkit.Material;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

public class FlagStyleManager {

    private final OrtusCapture plugin;
    private final File file;
    private FileConfiguration config;

    private final Map<String, String> typeToColor = new HashMap<>();
    private final Map<String, FlagColorStyle> colorStyles = new HashMap<>();
    private List<String> loreCommon = new ArrayList<>();
    private List<String> loreBound = new ArrayList<>();
    private List<String> loreTyped = new ArrayList<>();
    private String universalFakeType = "Штурмовой";

    public FlagStyleManager(OrtusCapture plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "flags.yml");
    }

    public void reload() {
        if (!plugin.getDataFolder().exists()) {
            //noinspection ResultOfMethodCallIgnored
            plugin.getDataFolder().mkdirs();
        }
        if (!file.exists()) {
            plugin.saveResource("flags.yml", false);
        }
        this.config = YamlConfiguration.loadConfiguration(file);
        loadMappings();
    }

    private void loadMappings() {
        typeToColor.clear();
        colorStyles.clear();

        if (config == null) {
            return;
        }

        ConfigurationSection typeSection = config.getConfigurationSection("flags.types");
        if (typeSection != null) {
            for (String typeKey : typeSection.getKeys(false)) {
                String color = typeSection.getString(typeKey + ".color", "purple");
                typeToColor.put(normalize(typeKey), normalize(color));
            }
        }

        ConfigurationSection loreSection = config.getConfigurationSection("flags.item-lore");
        if (loreSection != null) {
            String configuredUniversalType = loreSection.getString("universal-fake-type");
            if (configuredUniversalType != null && !configuredUniversalType.isBlank()) {
                universalFakeType = configuredUniversalType.trim();
            }
        }
        loreCommon = readStringList(loreSection, "common", List.of(
                "&8✦ &7Тип флага: &e{type}",
                "&8✦ &7Цвет: &e{color}",
                "&8✦ &7Установка: &fПКМ по земле Lands",
                "&8✦ &7Ломается ударами по невидимой коллизии",
                "&8✦ &7Ремонт: &fShift + ПКМ по флагштоку"
        ));
        loreBound = readStringList(loreSection, "bound", List.of(
                "&6◆ &7Привязан к региону: &e{id}",
                "&7Можно поставить только в нужной зоне"
        ));
        loreTyped = readStringList(loreSection, "typed", List.of(
                "&6◆ &7Крафтовый флаг типа: &e{type}",
                "&7Подходит только для регионов этого типа"
        ));

        ConfigurationSection colorsSection = config.getConfigurationSection("flags.colors");
        if (colorsSection != null) {
            for (String colorKey : colorsSection.getKeys(false)) {
                ConfigurationSection section = colorsSection.getConfigurationSection(colorKey);
                if (section == null) {
                    continue;
                }
                FlagColorStyle style = parseColorStyle(colorKey, section);
                colorStyles.put(normalize(colorKey), style);
            }
        }
    }

    public FlagColorStyle getStyleForType(String regionType) {
        String colorKey = typeToColor.getOrDefault(normalize(regionType), "purple");
        return getStyleByColor(colorKey);
    }

    public FlagColorStyle getStyleByColor(String colorKey) {
        FlagColorStyle style = colorStyles.get(normalize(colorKey));
        if (style != null) {
            return style;
        }
        return new FlagColorStyle(
                "purple",
                Material.PURPLE_BANNER,
                100,
                5,
                20,
                2,
                800L,
                2,
                true,
                "BLOCK_ANVIL_HIT",
                1.8f,
                0.9f,
                5.0,
                null,
                List.of(
                        new BlockPart(Material.DARK_OAK_FENCE, 0.5, 0.0, 0.5, 0.18f, 2.0f, 0.18f),
                        new BlockPart(Material.PURPLE_WOOL, 0.95, 1.1, 0.5, 0.10f, 0.75f, 1.1f),
                        new BlockPart(Material.PURPLE_WOOL, 0.95, 1.45, 0.5, 0.10f, 0.45f, 0.85f),
                        new BlockPart(Material.AMETHYST_BLOCK, 0.5, 2.05, 0.5, 0.25f, 0.25f, 0.25f)
                )
        );
    }

    public int getCollisionHeightBlocks() {
        if (config == null) {
            return 2;
        }
        return Math.max(1, config.getInt("flags.mechanics.collision-height-blocks", 2));
    }

    public String getColorKeyForType(String regionType) {
        return typeToColor.getOrDefault(normalize(regionType), "purple");
    }

    public List<String> buildFlagLore(boolean boundFlag, String systemId, String regionType, String colorKey) {
        List<String> out = new ArrayList<>();
        for (String line : loreCommon) {
            out.add(applyLorePlaceholders(line, systemId, regionType, colorKey));
        }
        List<String> specific = boundFlag ? loreBound : loreTyped;
        for (String line : specific) {
            out.add(applyLorePlaceholders(line, systemId, regionType, colorKey));
        }
        return out;
    }

    public List<String> buildUniversalFlagLore(String colorKey) {
        List<String> out = new ArrayList<>();
        for (String line : loreCommon) {
            out.add(applyLorePlaceholders(line, null, universalFakeType, colorKey));
        }
        return out;
    }

    private String applyLorePlaceholders(String line, String systemId, String regionType, String colorKey) {
        if (line == null) {
            return "";
        }
        String out = line
                .replace("{id}", systemId == null ? "-" : systemId)
                .replace("{type}", regionType == null ? "-" : regionType)
                .replace("{color}", colorKey == null ? "-" : colorKey);
        return ChatColor.translateAlternateColorCodes('&', out);
    }

    private FlagColorStyle parseColorStyle(String colorKey, ConfigurationSection section) {
        Material bannerMaterial = parseMaterial(section.getString("item-banner"), Material.WHITE_BANNER);

        int maxHealth = Math.max(1, section.getInt("health.max", 100));
        int hitDamage = Math.max(1, section.getInt("health.damage-per-hit", 5));
        int creativeHitDamage = Math.max(hitDamage, section.getInt("health.damage-per-hit-creative", 20));
        int repairPerClick = Math.max(1, section.getInt("health.repair-per-shift-right-click", 2));
        long repairCooldownMs = Math.max(0L, section.getLong("health.repair-cooldown-ms", 800L));
        int repairRange = Math.max(1, section.getInt("health.repair-range", 2));
        boolean hitSoundEnabled = section.getBoolean("hit-sound.enabled", true);
        String hitSound = section.getString("hit-sound.sound", "BLOCK_ANVIL_HIT");
        float hitSoundVolume = (float) section.getDouble("hit-sound.volume", 1.8D);
        float hitSoundPitch = (float) section.getDouble("hit-sound.pitch", 0.9D);
        double hitSoundRadius = Math.max(1.0D, section.getDouble("hit-sound.radius", 5.0D));
        String summonCommand = null;
        ConfigurationSection modelSection = section.getConfigurationSection("model");
        if (modelSection != null) {
            String raw = modelSection.getString("summon-command");
            if (raw != null && !raw.isBlank()) {
                summonCommand = raw.trim();
            }
        }

        List<BlockPart> parts = new ArrayList<>();
        ConfigurationSection partsSection = section.getConfigurationSection("model.parts");
        if (partsSection != null) {
            for (String partId : partsSection.getKeys(false)) {
                ConfigurationSection partSection = partsSection.getConfigurationSection(partId);
                if (partSection == null || !partSection.getBoolean("enabled", true)) {
                    continue;
                }
                Material block = parseMaterial(partSection.getString("block"), null);
                if (block == null || !block.isBlock()) {
                    continue;
                }
                List<Double> translation = readDoubleList(partSection.get("translation"), List.of(0.5, 0.0, 0.5));
                List<Double> scale = readDoubleList(partSection.get("scale"), List.of(0.2, 0.2, 0.2));
                parts.add(new BlockPart(
                        block,
                        translation.get(0), translation.get(1), translation.get(2),
                        scale.get(0).floatValue(), scale.get(1).floatValue(), scale.get(2).floatValue()
                ));
            }
        }

        if (parts.isEmpty()) {
            parts = List.of(
                    new BlockPart(Material.DARK_OAK_FENCE, 0.5, 0.0, 0.5, 0.18f, 2.0f, 0.18f),
                    new BlockPart(parseMaterial(section.getString("fallback-cloth-block"), Material.WHITE_WOOL), 0.95, 1.1, 0.5, 0.10f, 0.75f, 1.1f),
                    new BlockPart(Material.IRON_BARS, 0.5, 2.05, 0.5, 0.25f, 0.25f, 0.25f)
            );
        }

        return new FlagColorStyle(
                normalize(colorKey),
                bannerMaterial,
                maxHealth,
                hitDamage,
                creativeHitDamage,
                repairPerClick,
                repairCooldownMs,
                repairRange,
                hitSoundEnabled,
                hitSound,
                hitSoundVolume,
                hitSoundPitch,
                hitSoundRadius,
                summonCommand,
                parts
        );
    }

    private Material parseMaterial(String raw, Material fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Material.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private List<Double> readDoubleList(Object raw, List<Double> fallback) {
        if (!(raw instanceof List<?> list) || list.size() < 3) {
            return fallback;
        }
        List<Double> out = new ArrayList<>(3);
        for (int i = 0; i < 3; i++) {
            Object v = list.get(i);
            if (v instanceof Number n) {
                out.add(n.doubleValue());
            } else {
                try {
                    out.add(Double.parseDouble(String.valueOf(v)));
                } catch (Exception ex) {
                    return fallback;
                }
            }
        }
        return out;
    }

    private List<String> readStringList(ConfigurationSection parent, String path, List<String> fallback) {
        if (parent == null) {
            return new ArrayList<>(fallback);
        }
        List<String> list = parent.getStringList(path);
        if (list == null || list.isEmpty()) {
            return new ArrayList<>(fallback);
        }
        return new ArrayList<>(list);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public record BlockPart(Material block,
                            double translateX,
                            double translateY,
                            double translateZ,
                            float scaleX,
                            float scaleY,
                            float scaleZ) {
    }

    public record FlagColorStyle(String colorKey,
                                 Material bannerMaterial,
                                 int maxHealth,
                                 int damagePerHit,
                                 int damagePerHitCreative,
                                 int repairPerShiftRightClick,
                                 long repairCooldownMs,
                                 int repairRange,
                                 boolean hitSoundEnabled,
                                 String hitSound,
                                 float hitSoundVolume,
                                 float hitSoundPitch,
                                 double hitSoundRadius,
                                 String summonCommand,
                                 List<BlockPart> parts) {
    }
}
