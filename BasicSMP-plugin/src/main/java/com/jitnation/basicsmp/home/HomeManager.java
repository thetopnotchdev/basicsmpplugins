package com.jitnation.basicsmp.home;

import com.jitnation.basicsmp.BasicSMP;
import com.jitnation.basicsmp.Msg;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class HomeManager implements CommandExecutor, TabCompleter {

    private final BasicSMP plugin;
    private final File file;
    private final YamlConfiguration data;

    public HomeManager(BasicSMP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "homes.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        try {
            plugin.getDataFolder().mkdirs();
            data.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save homes.yml: " + ex.getMessage());
        }
    }

    private String path(UUID id, String name) { return "homes." + id + "." + name; }

    private Set<String> names(UUID id) {
        ConfigurationSection s = data.getConfigurationSection("homes." + id);
        return s == null ? Set.of() : s.getKeys(false);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
        String name = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "home";
        UUID id = p.getUniqueId();

        switch (cmd.getName().toLowerCase(Locale.ROOT)) {
            case "sethome" -> {
                if (!name.matches("[a-z0-9_-]{1,16}")) {
                    p.sendMessage(Msg.err("Home names: 1-16 characters, a-z 0-9 _ -"));
                    return true;
                }
                int max = plugin.getConfig().getInt("homes.max-homes", 3);
                boolean exists = names(id).contains(name);
                if (!exists && names(id).size() >= max && !p.hasPermission("basicsmp.homes.unlimited")) {
                    p.sendMessage(Msg.err("You can only have " + max + " homes. Use /delhome first."));
                    return true;
                }
                data.set(path(id, name), p.getLocation());
                save();
                p.sendMessage(Msg.ok("Home '" + name + "' " + (exists ? "updated." : "set.")));
            }
            case "delhome" -> {
                if (args.length < 1) { p.sendMessage(Msg.err("Usage: /delhome <name>")); return true; }
                if (!names(id).contains(name)) { p.sendMessage(Msg.err("No home named '" + name + "'.")); return true; }
                data.set(path(id, name), null);
                save();
                p.sendMessage(Msg.ok("Home '" + name + "' deleted."));
            }
            case "home" -> {
                Set<String> have = names(id);
                if (args.length == 0 && !have.contains("home") && have.size() == 1) name = have.iterator().next();
                if (!have.contains(name)) {
                    p.sendMessage(Msg.err("No home named '" + name + "'. Your homes: "
                            + (have.isEmpty() ? "none" : String.join(", ", have))));
                    return true;
                }
                Location loc = data.getLocation(path(id, name));
                if (loc == null || loc.getWorld() == null) { p.sendMessage(Msg.err("That home's world is unavailable.")); return true; }
                p.teleport(loc);
                p.sendMessage(Msg.ok("Teleported to '" + name + "'."));
            }
            case "homes" -> {
                Set<String> have = names(id);
                p.sendMessage(Msg.info("Homes (" + have.size() + "/" + plugin.getConfig().getInt("homes.max-homes", 3) + "): "
                        + (have.isEmpty() ? "none" : String.join(", ", have))));
            }
            default -> { return false; }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] args) {
        if (!(s instanceof Player p) || args.length != 1) return List.of();
        List<String> out = new ArrayList<>(names(p.getUniqueId()));
        out.removeIf(x -> !x.startsWith(args[0].toLowerCase(Locale.ROOT)));
        return out;
    }
}
