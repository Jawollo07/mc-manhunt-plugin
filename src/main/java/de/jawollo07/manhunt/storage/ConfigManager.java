package de.jawollo07.manhunt.storage;

import de.jawollo07.manhunt.ManHuntPlugin;

import java.util.Objects;

public final class ConfigManager {
    private final ManHuntPlugin plugin;

    public ConfigManager(ManHuntPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public String getString(String path) {
        String value = plugin.getConfig().getString(path);
        if (value == null) {
            throw new IllegalArgumentException("Missing string configuration value: " + path);
        }
        return value;
    }

    public String getString(String path, String defaultValue) {
        return plugin.getConfig().getString(path, defaultValue);
    }

    public int getInt(String path) {
        if (!plugin.getConfig().contains(path)) {
            throw new IllegalArgumentException("Missing integer configuration value: " + path);
        }
        return plugin.getConfig().getInt(path);
    }

    public int getInt(String path, int defaultValue) {
        return plugin.getConfig().getInt(path, defaultValue);
    }

    public boolean getBoolean(String path, boolean defaultValue) {
        return plugin.getConfig().getBoolean(path, defaultValue);
    }

    public void reload() {
        plugin.reloadConfig();
    }
}
