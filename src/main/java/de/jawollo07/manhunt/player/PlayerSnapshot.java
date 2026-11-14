package de.jawollo07.manhunt.player;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Snapshot of the player state that a minigame temporarily changes.
 * The binary representation is versioned so snapshots can survive restarts.
 */
final class PlayerSnapshot {
    private static final int FORMAT_VERSION = 1;

    private final ItemStack[] contents;
    private final ItemStack[] armor;
    private final ItemStack[] extra;
    private final UUID worldId;
    private final String worldName;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;
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
        Location location = player.getLocation();
        World world = location.getWorld();
        worldId = world == null ? null : world.getUID();
        worldName = world == null ? null : world.getName();
        x = location.getX();
        y = location.getY();
        z = location.getZ();
        yaw = location.getYaw();
        pitch = location.getPitch();
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

    private PlayerSnapshot(
            ItemStack[] contents, ItemStack[] armor, ItemStack[] extra,
            UUID worldId, String worldName, double x, double y, double z,
            float yaw, float pitch, double health, int foodLevel,
            float saturation, float exhaustion, int level, int totalExperience,
            float experienceProgress, GameMode gameMode, boolean allowFlight,
            boolean flying, int fireTicks, float fallDistance,
            List<PotionEffect> potionEffects
    ) {
        this.contents = copyItems(contents);
        this.armor = copyItems(armor);
        this.extra = copyItems(extra);
        this.worldId = worldId;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.health = health;
        this.foodLevel = foodLevel;
        this.saturation = saturation;
        this.exhaustion = exhaustion;
        this.level = level;
        this.totalExperience = totalExperience;
        this.experienceProgress = experienceProgress;
        this.gameMode = gameMode;
        this.allowFlight = allowFlight;
        this.flying = flying;
        this.fireTicks = fireTicks;
        this.fallDistance = fallDistance;
        this.potionEffects = List.copyOf(potionEffects);
    }

    static PlayerSnapshot capture(Player player) {
        return new PlayerSnapshot(player);
    }

    byte[] serialize() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (BukkitObjectOutputStream output = new BukkitObjectOutputStream(bytes)) {
            output.writeInt(FORMAT_VERSION);
            output.writeObject(contents);
            output.writeObject(armor);
            output.writeObject(extra);
            output.writeBoolean(worldId != null);
            if (worldId != null) output.writeUTF(worldId.toString());
            output.writeBoolean(worldName != null);
            if (worldName != null) output.writeUTF(worldName);
            output.writeDouble(x);
            output.writeDouble(y);
            output.writeDouble(z);
            output.writeFloat(yaw);
            output.writeFloat(pitch);
            output.writeDouble(health);
            output.writeInt(foodLevel);
            output.writeFloat(saturation);
            output.writeFloat(exhaustion);
            output.writeInt(level);
            output.writeInt(totalExperience);
            output.writeFloat(experienceProgress);
            output.writeUTF(gameMode.name());
            output.writeBoolean(allowFlight);
            output.writeBoolean(flying);
            output.writeInt(fireTicks);
            output.writeFloat(fallDistance);
            output.writeObject(new ArrayList<>(potionEffects));
        }
        return bytes.toByteArray();
    }

    @SuppressWarnings("unchecked")
    static PlayerSnapshot deserialize(byte[] bytes) throws IOException {
        try (BukkitObjectInputStream input = new BukkitObjectInputStream(new ByteArrayInputStream(bytes))) {
            int version = input.readInt();
            if (version != FORMAT_VERSION) {
                throw new IOException("Unsupported player snapshot format: " + version);
            }
            ItemStack[] contents = (ItemStack[]) input.readObject();
            ItemStack[] armor = (ItemStack[]) input.readObject();
            ItemStack[] extra = (ItemStack[]) input.readObject();
            UUID worldId = input.readBoolean() ? UUID.fromString(input.readUTF()) : null;
            String worldName = input.readBoolean() ? input.readUTF() : null;
            double x = input.readDouble();
            double y = input.readDouble();
            double z = input.readDouble();
            float yaw = input.readFloat();
            float pitch = input.readFloat();
            double health = input.readDouble();
            int foodLevel = input.readInt();
            float saturation = input.readFloat();
            float exhaustion = input.readFloat();
            int level = input.readInt();
            int totalExperience = input.readInt();
            float experienceProgress = input.readFloat();
            GameMode gameMode = GameMode.valueOf(input.readUTF());
            boolean allowFlight = input.readBoolean();
            boolean flying = input.readBoolean();
            int fireTicks = input.readInt();
            float fallDistance = input.readFloat();
            List<PotionEffect> effects = (List<PotionEffect>) input.readObject();
            return new PlayerSnapshot(contents, armor, extra, worldId, worldName,
                    x, y, z, yaw, pitch, health, foodLevel, saturation, exhaustion,
                    level, totalExperience, experienceProgress, gameMode, allowFlight,
                    flying, fireTicks, fallDistance, effects);
        } catch (ClassNotFoundException | ClassCastException exception) {
            throw new IOException("Could not decode player snapshot", exception);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Player snapshot contains an invalid value", exception);
        }
    }

    void restore(Player player) {
        player.closeInventory();
        World world = worldId == null ? null : Bukkit.getWorld(worldId);
        if (world == null && worldName != null) world = Bukkit.getWorld(worldName);
        Location destination = world == null
                ? player.getWorld().getSpawnLocation()
                : new Location(world, x, y, z, yaw, pitch);
        if (!player.teleport(destination)) {
            throw new IllegalStateException("Could not return player to their pre-game location");
        }

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
