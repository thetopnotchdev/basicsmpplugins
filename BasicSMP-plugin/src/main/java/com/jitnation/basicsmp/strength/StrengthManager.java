package com.jitnation.basicsmp.strength;

import com.jitnation.basicsmp.BasicSMP;
import com.jitnation.basicsmp.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Kill a player -> they drop a Strength Core. Right-click it for +1 attack damage (max +2).
 * Dying costs 1 attack damage (min -1).
 */
public final class StrengthManager implements Listener, CommandExecutor, TabCompleter {

    private final BasicSMP plugin;
    private final NamespacedKey coreKey;
    private final NamespacedKey levelKey;
    private final NamespacedKey modifierKey;

    public StrengthManager(BasicSMP plugin) {
        this.plugin = plugin;
        this.coreKey = new NamespacedKey(plugin, "strength_core");
        this.levelKey = new NamespacedKey(plugin, "strength_level");
        this.modifierKey = new NamespacedKey(plugin, "strength_bonus");
    }

    private int min() { return plugin.getConfig().getInt("strength.min", -1); }
    private int max() { return plugin.getConfig().getInt("strength.max", 2); }

    public int getLevel(Player p) {
        return p.getPersistentDataContainer().getOrDefault(levelKey, PersistentDataType.INTEGER, 0);
    }

    private void setLevel(Player p, int level) {
        p.getPersistentDataContainer().set(levelKey, PersistentDataType.INTEGER, Math.max(min(), Math.min(max(), level)));
        apply(p);
    }

    /** (Re)applies the attack damage modifier for the player's stored level. */
    public void apply(Player p) {
        AttributeInstance attr = p.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attr == null) return;
        for (AttributeModifier m : new ArrayList<>(attr.getModifiers())) {
            if (m.getKey().equals(modifierKey)) attr.removeModifier(m);
        }
        int level = getLevel(p);
        if (level != 0) {
            attr.addModifier(new AttributeModifier(modifierKey, level,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
        }
    }

    public ItemStack createCore() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Strength Core", NamedTextColor.DARK_RED)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Right-click to gain +1 attack damage", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Max +" + max() + ". Dying costs 1.", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(coreKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isCore(ItemStack item) {
        if (item == null || item.getType() != Material.NETHER_STAR || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(coreKey, PersistentDataType.BYTE);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) { apply(e.getPlayer()); }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> apply(p));
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Player killer = victim.getKiller();

        if (killer != null && !killer.equals(victim)) {
            ItemStack core = createCore();
            if (e.getKeepInventory()) {
                Location l = victim.getLocation();
                l.getWorld().dropItemNaturally(l, core);
            } else {
                e.getDrops().add(core);
            }
        }

        int before = getLevel(victim);
        setLevel(victim, before - 1);
        int after = getLevel(victim);
        if (after != before) victim.sendMessage(Msg.err("You lost 1 attack damage. Strength: " + fmt(after)));
    }

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        ItemStack item = p.getInventory().getItemInMainHand();
        if (!isCore(item)) return;

        e.setCancelled(true);
        int level = getLevel(p);
        if (level >= max()) {
            p.sendMessage(Msg.err("You're already at max strength (" + fmt(level) + ")."));
            return;
        }
        item.setAmount(item.getAmount() - 1);
        p.getInventory().setItemInMainHand(item.getAmount() <= 0 ? null : item);
        setLevel(p, level + 1);
        p.sendMessage(Msg.ok("Strength Core absorbed. Attack damage: " + fmt(getLevel(p))));
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1f);
    }

    private String fmt(int v) { return v > 0 ? "+" + v : String.valueOf(v); }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("give")) {
            if (!s.hasPermission("basicsmp.admin")) { s.sendMessage(Msg.err("No permission.")); return true; }
            Player t = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : (s instanceof Player pl ? pl : null);
            if (t == null) { s.sendMessage(Msg.err("Player not found.")); return true; }
            t.getInventory().addItem(createCore()).values()
                    .forEach(left -> t.getWorld().dropItemNaturally(t.getLocation(), left));
            s.sendMessage(Msg.ok("Gave a Strength Core to " + t.getName() + "."));
            return true;
        }
        if (s instanceof Player p) p.sendMessage(Msg.info("Your strength: " + fmt(getLevel(p))
                + " (range " + fmt(min()) + " to " + fmt(max()) + ")"));
        else s.sendMessage("Players only.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1 && s.hasPermission("basicsmp.admin")) out.add("give");
        if (args.length == 2 && args[0].equalsIgnoreCase("give"))
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
