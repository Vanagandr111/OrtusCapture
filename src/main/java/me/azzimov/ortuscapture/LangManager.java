package me.azzimov.ortuscapture;

import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LangManager {

    private final JavaPlugin plugin;
    private File file;
    private FileConfiguration config;

    public LangManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        if (file == null) {
            file = new File(plugin.getDataFolder(), "lang.yml");
        }
        if (!file.exists()) {
            plugin.saveResource("lang.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
        mergeDefaultsFromResource();
        migrateLegacyValues();
    }

    public void reload() {
        load();
    }

    public String getRaw(String key) {
        if (config == null) {
            return null;
        }
        return config.getString(key);
    }

    public String get(String key) {
        String raw = getRawOrMissing(key);
        return color(raw);
    }

    public String format(String key, String... pairs) {
        String raw = formatRaw(key, pairs);
        return color(raw);
    }

    public String formatRaw(String key, String... pairs) {
        String raw = getRawOrMissing(key);
        return applyPlaceholders(raw, pairs);
    }

    public List<String> getList(String key) {
        if (config == null) {
            return Collections.emptyList();
        }
        List<String> list = config.getStringList(key);
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        for (String line : list) {
            out.add(color(line));
        }
        return out;
    }

    public List<String> formatList(String key, String... pairs) {
        if (config == null) {
            return Collections.emptyList();
        }
        List<String> list = config.getStringList(key);
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        for (String line : list) {
            out.add(color(applyPlaceholders(line, pairs)));
        }
        return out;
    }

    private String getRawOrMissing(String key) {
        String value = getRaw(key);
        if (value == null) {
            String missingTemplate = getRaw("values.missing_lang");
            if (missingTemplate == null) {
                return key;
            }
            return missingTemplate.replace("{key}", key);
        }
        return value;
    }

    private String applyPlaceholders(String text, String... pairs) {
        if (text == null) {
            return null;
        }
        String prefix = getRaw("prefix");
        if (prefix != null) {
            text = text.replace("{prefix}", prefix);
        }
        if (pairs != null) {
            for (int i = 0; i + 1 < pairs.length; i += 2) {
                String key = pairs[i];
                String value = pairs[i + 1] == null ? "" : pairs[i + 1];
                if (key != null) {
                    text = text.replace("{" + key + "}", value);
                }
            }
        }
        return text;
    }

    private String color(String text) {
        if (text == null) {
            return null;
        }
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private void migrateLegacyValues() {
        if (config == null || file == null) {
            return;
        }

        boolean changed = false;
        String weekday = config.getString("values.weekday");
        if ("Р±СѓРґРЅРёР№".equalsIgnoreCase(weekday) || "будний".equalsIgnoreCase(weekday)) {
            config.set("values.weekday", "будни");
            changed = true;
        }

        changed |= ensureDefault("point.usage", "{prefix}&eИспользование: &f/oc point <set|clear> flagpole <systemId>");
        changed |= ensureDefault("point.only_flagpole", "{prefix}&cСейчас поддерживается только точка &eflagpole&c.");
        changed |= ensureDefault("point.id_not_found", "{prefix}&cСистемный ID не найден: &e{id}");
        changed |= ensureDefault("point.set_flagpole", "{prefix}&aТочка флагштока для &e{id}&a сохранена: &f{world} {x} {y} {z}");
        changed |= ensureDefault("point.cleared", "{prefix}&aТочка флагштока для &e{id}&a очищена.");
        changed |= ensureDefault("point.error", "{prefix}&cНе удалось сохранить точку.");
        changed |= ensureDefault("flag.wrong_flagpole_point", "{prefix}&cФлаг можно ставить только в точке флагштока этого региона.");
        changed |= ensureDefault("flag.repair_too_far", "{prefix}&cСлишком далеко от флагштока для ремонта.");
        changed |= ensureDefault("capture.flag_hp_inline", "&8[&cHP: &f{hp}/{hp_max}&8]");
        changed |= ensureDefault("capture.flag_hp_actionbar", "&cФлаг {region}: &f{hp}&7/&f{max} HP");
        changed |= ensureDefault("capture.flag_repair_actionbar", "&aРемонт флага {region}: &f{hp}&7/&f{max} HP");
        changed |= ensureDefault("gui.help_player_line", "&f/oc gui &7- открыть меню региона (по текущей земле)");
        changed |= ensureDefault("gui.help_admin_line", "&f/oc gui [admin|id] &7- открыть GUI (админ / регион)");
        changed |= ensureDefault("gui.open_error", "{prefix}&cНе удалось открыть GUI.");
        changed |= ensureDefault("gui.admin.title", "&8OrtusCapture • Админ");
        changed |= ensureDefault("gui.admin.regions_name", "&6Регионы");
        changed |= ensureDefault("gui.admin.regions_count", "&7Записей в базе: &e{count}");
        changed |= ensureDefault("gui.admin.regions_hint", "&8Нажмите для подсказки");
        changed |= ensureDefault("gui.admin.regions_click_hint", "{prefix}&7Используйте &f/oc gui <id>&7 для открытия конкретного региона.");
        changed |= ensureDefault("gui.admin.reload_name", "&aПерезагрузка");
        changed |= ensureDefault("gui.admin.reload_l1", "&7Перезагрузить все конфиги");
        changed |= ensureDefault("gui.admin.reload_l2", "&7config, regions, lang, crafts, flags");
        changed |= ensureDefault("gui.admin.server_day_name", "&bДень сервера");
        changed |= ensureDefault("gui.admin.server_day_l1", "&7Сегодня: &e{day}");
        changed |= ensureDefault("gui.admin.server_day_l2", "&7Тип дня: &e{kind}");
        changed |= ensureDefault("gui.admin.server_day_click_hint", "{prefix}&7Проверяйте allowed-days в &fconfig.yml");
        changed |= ensureDefault("gui.region.title", "&8Регион • {id}");
        changed |= ensureDefault("gui.region.not_found", "{prefix}&cРегион не найден.");
        changed |= ensureDefault("gui.region.system_id_name", "&6Системный ID");
        changed |= ensureDefault("gui.region.system_id_l1", "&7ID: &e{id}");
        changed |= ensureDefault("gui.region.type_l1", "&7Тип: &e{type}");
        changed |= ensureDefault("gui.region.capture_name", "&cСтатус захвата");
        changed |= ensureDefault("gui.region.capture_state", "&7Захват: {state}");
        changed |= ensureDefault("gui.region.active_capture", "&7Активный захват сейчас: &e{count}");
        changed |= ensureDefault("gui.region.capture_chat", "{prefix}&7Регион &e{id}&7: захват {state}&7.");
        changed |= ensureDefault("gui.region.flagpole_name", "&eФлагшток");
        changed |= ensureDefault("gui.region.flagpole_l1", "&7Точка: &f{coords}");
        changed |= ensureDefault("gui.region.flagpole_l2", "&8Нажмите для вывода в чат");
        changed |= ensureDefault("gui.region.flagpole_chat", "{prefix}&7Точка флагштока: &f{coords}");
        changed |= ensureDefault("gui.region.owner_name", "&aВладелец / КД");
        changed |= ensureDefault("gui.region.owner_l1", "&7Текущий владелец: &e{owner}");
        changed |= ensureDefault("gui.region.cooldown_l1", "&7Кулдаун: &e{cooldown}");

        if (changed) {
            try {
                config.save(file);
            } catch (IOException ignored) {
            }
        }
    }

    private void mergeDefaultsFromResource() {
        if (config == null || file == null) {
            return;
        }
        try (InputStream in = plugin.getResource("lang.yml")) {
            if (in == null) {
                return;
            }
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            boolean changed = mergeMissing(defaults, config, "");
            if (changed) {
                config.save(file);
            }
        } catch (Exception ignored) {
        }
    }

    private boolean mergeMissing(ConfigurationSection source, FileConfiguration target, String path) {
        boolean changed = false;
        for (String key : source.getKeys(false)) {
            String currentPath = path == null || path.isEmpty() ? key : path + "." + key;
            Object value = source.get(key);
            if (value instanceof ConfigurationSection nested) {
                if (!target.isConfigurationSection(currentPath) && !target.contains(currentPath)) {
                    target.createSection(currentPath);
                    changed = true;
                }
                changed |= mergeMissing(nested, target, currentPath);
                continue;
            }
            if (!target.contains(currentPath)) {
                target.set(currentPath, value);
                changed = true;
            }
        }
        return changed;
    }

    private boolean ensureDefault(String path, String value) {
        if (config.contains(path)) {
            return false;
        }
        config.set(path, value);
        return true;
    }
}
