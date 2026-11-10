import org.bukkit.plugin.java.JavaPlugin;

public class ManHuntPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        saveDefaultConfig();
        getLogger().info("ManHuntPlugin has been enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("ManHuntPlugin has been disabled!");
        // Perform any necessary cleanup here
    }
    
}
