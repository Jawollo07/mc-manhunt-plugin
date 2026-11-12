package de.jawollo07.manhunt.player;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.List;

/**
 * In-memory snapshot of the player state that a minigame temporarily changes.
 * Item stacks and locations are copied so later mutations do not affect the snapshot.
 */
final class PlayerSnapshot {
    private final ItemStack[] contents;
    private final ItemStack[] armor;
    private final ItemStack[] extra;
    private final Location location;
    private final double health;
    private final int foodLevel;
    private final float saturation;
    private final float exhaustion;
    private final int level;
    private final int totalExperience;
    private final float experienceProgress;
    private final GameMode gameMode;
    private final boolean allowFlight;
    private final boolean flying;
    private final int fireTicks;
    private final float fallDistance;
    private final List<PotionEffect> potionEffects;

    private PlayerSnapshot(Player player) {
        PlayerInventory inventory = player.getInventory();
        contents = copyItems(inventory.getContents());
        armor = copyItems(inventory.getArmorContents());
        extra = copyItems(inventory.getExtraContents());
        location = player.getLocation().clone();
        health = player.getHealth();
        foodLevel = player.getFoodLevel();
        saturation = player.getSaturation();
        exhaustion = player.getExhaustion();
        level = player.getLevel();
        totalExperience = player.getTotalExperience();
        experienceProgress = player.getExp();
        gameMode = player.getGameMode();
        allowFlight = player.getAllowFlight();
        flying = player.isFlying();
        fireTicks = player.getFireTicks();
        fallDistance = player.getFallDistance();
        potionEffects = List.copyOf(player.getActivePotionEffects());
    }

    static PlayerSnapshot capture(Player player) {
        return new PlayerSnapshot(player);
    }

    void restore(Player player) {
        player.closeInventory();
        player.teleport(location);

        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setContents(copyItems(contents));
        inventory.setArmorContents(copyItems(armor));
        inventory.setExtraContents(copyItems(extra));

        player.setGameMode(gameMode);
        player.setAllowFlight(allowFlight);
        player.setFlying(allowFlight && flying);
        player.setFoodLevel(foodLevel);
        player.setSaturation(saturation);
        player.setExhaustion(exhaustion);
        player.setLevel(level);
        player.setExp(experienceProgress);
        player.setTotalExperience(totalExperience);
        player.setFireTicks(fireTicks);
        player.setFallDistance(fallDistance);

        for (PotionEffect active : new ArrayList<>(player.getActivePotionEffects())) {
            player.removePotionEffect(active.getType());
        }
        for (PotionEffect effect : potionEffects) {
            player.addPotionEffect(effect, true);
        }

        player.setHealth(Math.max(0.1, Math.min(health, player.getMaxHealth())));
    }

    private static ItemStack[] copyItems(ItemStack[] source) {
        ItemStack[] copy = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i] == null ? null : source[i].clone();
        }
        return copy;
    }
}
