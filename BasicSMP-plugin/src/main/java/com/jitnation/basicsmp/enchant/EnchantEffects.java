package com.jitnation.basicsmp.enchant;

import com.jitnation.basicsmp.BasicSMP;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * Vanilla only runs the mace "smash" code for maces, so Density and Wind Burst do nothing on a sword.
 * This emulates them for every non-mace item so they work on any item.
 * Maces are left alone (vanilla already handles them).
 */
public final class EnchantEffects implements Listener {

    private final BasicSMP plugin;
    private final Enchantment density = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("density"));
    private final Enchantment windBurst = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("wind_burst"));

    public EnchantEffects(BasicSMP plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        if (!(e.getDamager() instanceof Player p)) return;

        ItemStack item = p.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR || item.getType() == Material.MACE) return;

        int d = density == null ? 0 : item.getEnchantmentLevel(density);
        int w = windBurst == null ? 0 : item.getEnchantmentLevel(windBurst);
        if (d == 0 && w == 0) return;

        float fall = p.getFallDistance();
        if (fall <= 1.5f || p.isGliding()) return;   // same threshold as vanilla smash attacks

        // Vanilla Density: +0.5 damage per fallen block per level.
        if (d > 0) e.setDamage(e.getDamage() + 0.5 * d * fall);

        // Wind Burst: launch the attacker back up after the hit.
        if (w > 0) {
            double y = 0.8 + 0.3 * w;
            Bukkit.getScheduler().runTask(plugin, () -> {
                Vector v = p.getVelocity();
                p.setVelocity(new Vector(v.getX(), y, v.getZ()));
            });
        }

        // Like a mace smash: the hit cancels the attacker's fall damage.
        p.setFallDistance(0f);
    }
}
