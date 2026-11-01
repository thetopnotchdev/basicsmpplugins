package com.jitnation.basicsmp.enchant;

import com.jitnation.basicsmp.BasicSMP;
import com.jitnation.basicsmp.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.*;

/**
 * Every interval, one random enchant is applied to the item every player holds in their main hand.
 * The timer pauses while the server is empty.
 */
public final class EnchantRotation implements CommandExecutor, TabCompleter {

    private final BasicSMP plugin;
    private final Random random = new Random();
    private BukkitTask task;
    private int interval = 600;
    private int remaining = 600;
    private Set<Integer> warnings = new HashSet<>();
    private List<Enchantment> pool = new ArrayList<>();
    private Enchantment last;

    public EnchantRotation(BasicSMP plugin) { this.plugin = plugin; }

    public void start() {
        stop();
        FileConfiguration cfg = plugin.getConfig();
        if (!cfg.getBoolean("enchant-rotation.enabled", true)) return;
        interval = Math.max(30, cfg.getInt("enchant-rotation.interval-seconds", 600));
        remaining = interval;
        warnings = new HashSet<>(cfg.getIntegerList("enchant-rotation.warnings"));
        pool = new ArrayList<>();
        for (String key : cfg.getStringList("enchant-rotation.pool")) {
            Enchantment e = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key.toLowerCase(Locale.ROOT)));
            if (e == null) plugin.getLogger().warning("Unknown enchant in pool: " + key);
            else pool.add(e);
        }
        if (pool.isEmpty()) {
            plugin.getLogger().warning("Enchant pool is empty - rotation disabled.");
            return;
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (task != null) { task.cancel(); task = null; }
    }

    private void tick() {
        if (Bukkit.getOnlinePlayers().isEmpty()) return;
        remaining--;
        if (remaining <= 0) {
            rollAndApply();
            remaining = interval;
            return;
        }
        if (warnings.contains(remaining)) warn(remaining);
    }

    private void warn(int seconds) {
        String when = seconds >= 60 && seconds % 60 == 0 ? (seconds / 60) + " minute(s)" : seconds + " second(s)";
        Component msg = Component.text("A new enchant will be applied to everyone's held item in " + when + "!",
                NamedTextColor.LIGHT_PURPLE);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(msg);
            if (seconds <= 10) {
                p.showTitle(Title.title(
                        Component.text("Enchant in " + seconds, NamedTextColor.LIGHT_PURPLE),
                        Component.text("Hold the item you want enchanted", NamedTextColor.GRAY),
                        Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(100))));
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, seconds <= 5 ? 1.5f : 1f);
            }
        }
    }

    /** Picks one enchant and applies it to every player's held item. */
    public void rollAndApply() {
        if (pool.isEmpty()) return;
        List<Enchantment> candidates = new ArrayList<>(pool);
        if (candidates.size() > 1 && last != null) candidates.remove(last);
        Enchantment chosen = candidates.get(random.nextInt(candidates.size()));
        last = chosen;

        int cap = plugin.getConfig().getInt("enchant-rotation.max-level-cap", 5);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR) continue;
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand.getType() == Material.AIR) {
                p.sendMessage(Msg.err("You weren't holding an item, so you missed this enchant: ")
                        .append(chosen.displayName(chosen.getMaxLevel())));
                continue;
            }
            int existing = hand.getEnchantmentLevel(chosen);
            int level = existing == 0 ? chosen.getMaxLevel() : Math.min(existing + 1, cap);
            if (chosen.getMaxLevel() == 1) level = 1;
            hand.addUnsafeEnchantment(chosen, level);
            p.getInventory().setItemInMainHand(hand);

            p.sendMessage(Component.text("Your ", NamedTextColor.GREEN)
                    .append(Component.translatable(hand.getType().translationKey()))
                    .append(Component.text(" gained ", NamedTextColor.GREEN))
                    .append(chosen.displayName(level).color(NamedTextColor.GOLD)));
            p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1f);
        }
        Bukkit.broadcast(Component.text("This round's enchant: ", NamedTextColor.LIGHT_PURPLE)
                .append(chosen.displayName(chosen.getMaxLevel()).color(NamedTextColor.GOLD)));
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "now" -> { rollAndApply(); remaining = interval; }
            case "reload" -> {
                plugin.reloadConfig();
                start();
                s.sendMessage(Msg.ok("Enchant rotation reloaded."));
            }
            default -> s.sendMessage(Msg.info("Next enchant in " + remaining + "s (interval " + interval + "s, pool "
                    + pool.size() + ")."));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] args) {
        if (args.length != 1) return List.of();
        List<String> out = new ArrayList<>(List.of("status", "now", "reload"));
        out.removeIf(x -> !x.startsWith(args[0].toLowerCase(Locale.ROOT)));
        return out;
    }
}
