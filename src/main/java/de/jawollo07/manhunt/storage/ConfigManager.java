package de.jawollo07.manhunt.storage;

import de.jawollo07.manhunt.ManHuntPlugin;
import net.kyori.adventure.text.event.ClickEvent.Payload.Int;

public class ConfigManager {
    private final ManHuntPlugin plugin = new ManHuntPlugin();
    public String getString(String path) {
        try {
            String result = plugin.getConfig().getString(path);
            return result;
        } catch(Exception e) {
            throw new IllegalStateException("Failed to read config value: " + path, e);
        }
    }
    public int getInt(String path) {
       try {
            int result = plugin.getConfig().getInt(path);
            return result;
        } catch(Exception e) {
            throw new IllegalStateException("Failed to read config value: " + path, e);
        } 
    }
}
