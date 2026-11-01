package com.jitnation.basicsmp;

import com.jitnation.basicsmp.enchant.EnchantEffects;
import com.jitnation.basicsmp.enchant.EnchantRotation;
import com.jitnation.basicsmp.home.HomeManager;
import com.jitnation.basicsmp.strength.StrengthManager;
import com.jitnation.basicsmp.tpa.TpaManager;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public final class BasicSMP extends JavaPlugin {

    private HomeManager homes;
    private EnchantRotation rotation;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        homes = new HomeManager(this);
        TpaManager tpa = new TpaManager(this);
        rotation = new EnchantRotation(this);
        StrengthManager strength = new StrengthManager(this);

        for (String c : new String[]{"tpa", "tpahere", "tpaccept", "tpdeny"}) bind(c, tpa, tpa);
        for (String c : new String[]{"sethome", "delhome", "home", "homes"}) bind(c, homes, homes);
        bind("strength", strength, strength);
        bind("enchantrotation", rotation, rotation);

        getServer().getPluginManager().registerEvents(tpa, this);
        getServer().getPluginManager().registerEvents(strength, this);
        getServer().getPluginManager().registerEvents(new EnchantEffects(this), this);
        getServer().getPluginManager().registerEvents(new Extras(this), this);

        int sleep = getConfig().getInt("extras.sleeping-percentage", 50);
        for (World w : Bukkit.getWorlds()) w.setGameRule(GameRule.PLAYERS_SLEEPING_PERCENTAGE, sleep);

        rotation.start();
        getLogger().info("BasicSMP enabled");
    }

    @Override
    public void onDisable() {
        if (rotation != null) rotation.stop();
        if (homes != null) homes.save();
    }

    private void bind(String name, CommandExecutor ex, TabCompleter tc) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) { getLogger().warning("Command missing in plugin.yml: " + name); return; }
        cmd.setExecutor(ex);
        cmd.setTabCompleter(tc);
    }
}
