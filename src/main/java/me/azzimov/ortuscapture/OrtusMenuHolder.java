package me.azzimov.ortuscapture;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class OrtusMenuHolder implements InventoryHolder {

    public enum MenuType {
        REGION,
        ADMIN
    }

    private final MenuType type;
    private final String regionId;

    public OrtusMenuHolder(MenuType type, String regionId) {
        this.type = type;
        this.regionId = regionId;
    }

    public MenuType getType() {
        return type;
    }

    public String getRegionId() {
        return regionId;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
