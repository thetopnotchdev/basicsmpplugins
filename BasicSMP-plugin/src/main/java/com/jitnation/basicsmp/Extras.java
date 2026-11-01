package com.jitnation.basicsmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

/** Small vanilla-friendly quality-of-life extras. */
public final class Extras implements Listener {
    private final BasicSMP plugin;

    public Extras(BasicSMP plugin) { this.plugin = plugin; }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        if (!plugin.getConfig().getBoolean("extras.death-coordinates", true)) return;
        Player p = e.getEntity();
        Location l = p.getLocation();
        String world = l.getWorld() == null ? "?" : l.getWorld().getName();
        p.sendMessage(Component.text("You died at " + l.getBlockX() + ", " + l.getBlockY() + ", "
                + l.getBlockZ() + " (" + world + ")", NamedTextColor.GRAY));
    }
}
