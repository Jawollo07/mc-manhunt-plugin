package de.jawollo07.manhunt.modes.builtin;

import de.jawollo07.manhunt.game.Game;
import de.jawollo07.manhunt.game.GameManager;
import de.jawollo07.manhunt.player.PlayerStatus;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Implements the four active Juggernaut abilities with independent cooldowns. */
public final class JuggernautAbilityListener implements Listener {
    private final GameManager games;
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();

    public JuggernautAbilityListener(GameManager games) {
        this.games = games;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        Player boss = event.getPlayer();
        Game game = games.getActiveGame().orElse(null);
        if (game == null || !game.isRunning()
                || game.getPlayer(boss.getUniqueId())
                .filter(participant -> participant.getStatus() == PlayerStatus.PLAYING).isEmpty()
                || !game.getRoleManager().getRoleId(boss.getUniqueId()).filter("juggernaut"::equals).isPresent()) return;

        Material held = boss.getInventory().getItemInMainHand().getType();
        switch (held) {
            case BLAZE_ROD -> {
                if (!game.getArena().modeBooleanSetting("juggernaut", "ability_slam", true)) return;
                event.setCancelled(true);
                if (!ready(boss, "slam", 15)) return;
                for (Player target : boss.getWorld().getPlayers()) {
                    if (!isActiveHunter(game, target) || target.getLocation().distanceSquared(boss.getLocation()) > 36) continue;
                    target.damage(8.0, boss);
                    target.setVelocity(target.getVelocity().add(new Vector(0, 0.85, 0)));
                }
                boss.sendMessage("§cGround Slam ausgelöst!");
            }
            case IRON_INGOT -> {
                if (!game.getArena().modeBooleanSetting("juggernaut", "ability_charge", true)) return;
                event.setCancelled(true);
                if (!ready(boss, "charge", 12)) return;
                Vector direction = boss.getLocation().getDirection().normalize().multiply(2.1);
                direction.setY(Math.max(0.25, direction.getY()));
                boss.setVelocity(direction);
                boss.sendMessage("§cCharge!");
            }
            case GOAT_HORN -> {
                if (!game.getArena().modeBooleanSetting("juggernaut", "ability_roar", true)) return;
                event.setCancelled(true);
                if (!ready(boss, "roar", 20)) return;
                for (Player target : boss.getWorld().getPlayers()) {
                    if (!isActiveHunter(game, target) || target.getLocation().distanceSquared(boss.getLocation()) > 100) continue;
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1, true, true, true));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 0, true, true, true));
                }
                boss.sendMessage("§cRoar! Jäger in deiner Nähe wurden geschwächt.");
            }
            case GOLDEN_APPLE -> {
                if (!game.getArena().modeBooleanSetting("juggernaut", "ability_regeneration", true)) return;
                event.setCancelled(true);
                if (!ready(boss, "regeneration", 30)) return;
                boss.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 160, 2, true, true, true));
                boss.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 200, 1, true, true, true));
                boss.sendMessage("§aRegeneration aktiviert!");
            }
            default -> { }
        }
    }

    private boolean isActiveHunter(Game game, Player target) {
        return game.hasPlayer(target.getUniqueId())
                && game.getPlayer(target.getUniqueId())
                .filter(participant -> participant.getStatus() == PlayerStatus.PLAYING)
                .isPresent()
                && !game.getRoleManager().getRoleId(target.getUniqueId())
                .filter("juggernaut"::equals).isPresent();
    }

    private boolean ready(Player player, String ability, long seconds) {
        long now = System.currentTimeMillis();
        Map<String, Long> playerCooldowns = cooldowns.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>());
        long next = playerCooldowns.getOrDefault(ability, 0L);
        if (now < next) {
            player.sendMessage("§eFähigkeit bereit in " + ((next - now + 999) / 1000) + " Sekunden.");
            return false;
        }
        playerCooldowns.put(ability, now + seconds * 1000);
        return true;
    }

    public static ItemStack abilityItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§c" + name);
            item.setItemMeta(meta);
        }
        return item;
    }
}
