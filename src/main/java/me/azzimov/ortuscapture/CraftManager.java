package me.azzimov.ortuscapture;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CraftManager implements Listener {

    private final OrtusCapture plugin;
    private final File file;
    private FileConfiguration config;
    private final List<NamespacedKey> registeredKeys = new ArrayList<>();
    private final Map<NamespacedKey, String> recipePermissions = new HashMap<>();

    public CraftManager(OrtusCapture plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "crafts.yml");
    }

    public void reload() {
        if (!plugin.getDataFolder().exists()) {
            //noinspection ResultOfMethodCallIgnored
            plugin.getDataFolder().mkdirs();
        }
        if (!file.exists()) {
            plugin.saveResource("crafts.yml", false);
        }
        this.config = YamlConfiguration.loadConfiguration(file);
        registerRecipes();
    }

    public void unregisterRecipes() {
        for (NamespacedKey key : registeredKeys) {
            Bukkit.removeRecipe(key);
        }
        registeredKeys.clear();
        recipePermissions.clear();
    }

    private void registerRecipes() {
        unregisterRecipes();
        if (config == null || !config.getBoolean("crafts.enabled", true)) {
            return;
        }

        ConfigurationSection flagsSection = config.getConfigurationSection("crafts.flags");
        if (flagsSection == null) {
            return;
        }

        for (String type : flagsSection.getKeys(false)) {
            if (type == null || type.isBlank()) {
                continue;
            }
            ConfigurationSection section = flagsSection.getConfigurationSection(type);
            if (section == null || !section.getBoolean("enabled", true)) {
                continue;
            }

            List<String> shape = section.getStringList("shape");
            if (shape == null || shape.size() != 3) {
                plugin.getLogger().warning("[OrtusCapture] Invalid craft shape for type: " + type);
                continue;
            }

            String mode = section.getString("mode", "universal");
            ItemStack result = "typed".equalsIgnoreCase(mode)
                    ? plugin.createTypedFlagItem(type.toLowerCase(Locale.ROOT))
                    : plugin.createUniversalFlagItem();
            int amount = Math.max(1, section.getInt("amount", 1));
            result.setAmount(Math.min(64, amount));

            NamespacedKey key = new NamespacedKey(plugin, "craft_flag_" + type.toLowerCase(Locale.ROOT));
            ShapedRecipe recipe = new ShapedRecipe(key, result);
            recipe.shape(shape.get(0), shape.get(1), shape.get(2));

            ConfigurationSection ingredients = section.getConfigurationSection("ingredients");
            if (ingredients == null) {
                plugin.getLogger().warning("[OrtusCapture] Missing ingredients for craft type: " + type);
                continue;
            }

            boolean ok = true;
            for (String symbolKey : ingredients.getKeys(false)) {
                if (symbolKey == null || symbolKey.length() != 1) {
                    ok = false;
                    break;
                }
                String materialName = ingredients.getString(symbolKey);
                Material material = parseMaterial(materialName);
                if (material == null) {
                    plugin.getLogger().warning("[OrtusCapture] Invalid material '" + materialName + "' in crafts.yml for type " + type);
                    ok = false;
                    break;
                }
                recipe.setIngredient(symbolKey.charAt(0), material);
            }

            if (!ok) {
                continue;
            }

            if (Bukkit.addRecipe(recipe)) {
                registeredKeys.add(key);
                String permission = section.getString("permission", "").trim();
                if (!permission.isEmpty()) {
                    recipePermissions.put(key, permission);
                }
            } else {
                plugin.getLogger().warning("[OrtusCapture] Failed to add craft recipe for type: " + type);
            }
        }
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (event.getInventory() == null) {
            return;
        }
        Recipe recipe = event.getRecipe();
        if (!(recipe instanceof org.bukkit.Keyed keyed)) {
            return;
        }
        NamespacedKey key = keyed.getKey();
        String permission = recipePermissions.get(key);
        if (permission == null || permission.isBlank()) {
            return;
        }
        HumanEntity viewer = event.getView() != null ? event.getView().getPlayer() : null;
        if (viewer == null || viewer.isOp() || viewer.hasPermission(permission)) {
            return;
        }
        event.getInventory().setResult(null);
    }

    @EventHandler
    public void onCraftItem(CraftItemEvent event) {
        Recipe recipe = event.getRecipe();
        if (!(recipe instanceof org.bukkit.Keyed keyed)) {
            return;
        }
        NamespacedKey key = keyed.getKey();
        String permission = recipePermissions.get(key);
        if (permission == null || permission.isBlank()) {
            return;
        }
        if (!(event.getWhoClicked() instanceof org.bukkit.entity.Player player)) {
            return;
        }
        if (player.isOp() || player.hasPermission(permission)) {
            return;
        }
        event.setCancelled(true);
        player.sendMessage(plugin.getLang().format("craft.no_permission", "permission", permission));
    }

    private Material parseMaterial(String materialName) {
        if (materialName == null || materialName.isBlank()) {
            return null;
        }
        try {
            return Material.valueOf(materialName.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
