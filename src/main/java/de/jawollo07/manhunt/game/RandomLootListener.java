package de.jawollo07.manhunt.game;

import de.jawollo07.manhunt.ManHuntPlugin;
import org.bukkit.Material;
import org.bukkit.block.Container;
import org.bukkit.block.Chest;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Replaces vanilla loot in containers inside the active disposable arena world.
 * Ordinary worlds, mob drops and block drops are deliberately left untouched.
 */
public final class RandomLootListener implements Listener {
    private static final List<Material> LOOT_POOL = List.of(
            Material.BREAD, Material.COOKED_BEEF, Material.COOKED_PORKCHOP, Material.BAKED_POTATO,
            Material.CARROT, Material.GOLDEN_CARROT, Material.APPLE, Material.GOLDEN_APPLE,
            Material.ARROW, Material.TIPPED_ARROW, Material.BOW, Material.CROSSBOW,
            Material.IRON_INGOT, Material.GOLD_INGOT, Material.COPPER_INGOT, Material.EMERALD,
            Material.LAPIS_LAZULI, Material.REDSTONE, Material.DIAMOND, Material.OBSIDIAN,
            Material.ENDER_PEARL, Material.EXPERIENCE_BOTTLE, Material.TNT, Material.STRING,
            Material.LEATHER, Material.FEATHER, Material.BONE, Material.GUNPOWDER,
            Material.OAK_PLANKS, Material.COBBLESTONE, Material.TORCH, Material.CRAFTING_TABLE,
            Material.IRON_PICKAXE, Material.IRON_AXE, Material.SHIELD, Material.COOKED_CHICKEN,
            Material.SADDLE, Material.NAME_TAG, Material.BOOK, Material.COMPASS
    );

    private final ManHuntPlugin plugin;
    private final GameManager gameManager;

    public RandomLootListener(ManHuntPlugin plugin, GameManager gameManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLootGenerate(LootGenerateEvent event) {
        Game game = gameManager.getActiveGame().orElse(null);
        if (game == null || !game.isRunning()
                || !game.getArena().modeBooleanSetting("general", "random_loot_enabled", false)) return;
        if (!(event.getInventoryHolder() instanceof Container)) return;
        if (!(event.getInventoryHolder() instanceof Chest chest)) return;

        // Keep only roughly one in five chests as randomized-loot chests.
        // The decision is deterministic for this game and chest, so reopening
        // or reloading a chunk never rerolls the selection.
        var location = chest.getBlock().getLocation();
        int selection = Math.floorMod(java.util.Objects.hash(
                game.getId(), event.getWorld().getUID(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ()), 100);
        if (selection >= 20) return;

        var mapInstance = game.getMapInstance().orElse(null);
        if (mapInstance == null || !mapInstance.worldName().equals(event.getWorld().getName())) return;

        event.getLoot().clear();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int rolls = random.nextInt(4, 9);
        List<ItemStack> generated = new ArrayList<>(rolls);
        for (int i = 0; i < rolls; i++) {
            Material material = LOOT_POOL.get(random.nextInt(LOOT_POOL.size()));
            int max = material.getMaxStackSize();
            int amount = max <= 1 ? 1 : random.nextInt(1, Math.min(max, 12) + 1);
            generated.add(new ItemStack(material, amount));
        }
        event.getLoot().addAll(generated);
        plugin.getLogger().fine("Generated " + generated.size() + " random loot stacks in " + event.getWorld().getName());
    }
}
