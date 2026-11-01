package com.jitnation.basicsmp.tpa;

import com.jitnation.basicsmp.BasicSMP;
import com.jitnation.basicsmp.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;

public final class TpaManager implements CommandExecutor, TabCompleter, Listener {

    private record Request(UUID from, boolean here, long expiresAt) {}

    private final BasicSMP plugin;
    /** target -> (sender -> request) */
    private final Map<UUID, Map<UUID, Request>> pending = new HashMap<>();

    public TpaManager(BasicSMP plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }
        switch (cmd.getName().toLowerCase(Locale.ROOT)) {
            case "tpahere": return send(p, args, true);
            case "tpaccept": return accept(p, args);
            case "tpdeny": return deny(p, args);
            default:
                if (args.length > 0) {
                    String sub = args[0].toLowerCase(Locale.ROOT);
                    String[] rest = Arrays.copyOfRange(args, 1, args.length);
                    switch (sub) {
                        case "accept": return accept(p, rest);
                        case "deny": return deny(p, rest);
                        case "send": return send(p, rest, false);
                        case "here": return send(p, rest, true);
                        default: break;
                    }
                }
                return send(p, args, false);
        }
    }

    private boolean send(Player p, String[] args, boolean here) {
        if (args.length < 1) {
            p.sendMessage(Msg.err("Usage: /" + (here ? "tpahere" : "tpa") + " <player>"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || !target.isOnline()) {
            p.sendMessage(Msg.err("Player not found."));
            return true;
        }
        if (target.equals(p)) {
            p.sendMessage(Msg.err("You can't teleport to yourself."));
            return true;
        }
        long ttl = plugin.getConfig().getInt("tpa.timeout-seconds", 60) * 1000L;
        pending.computeIfAbsent(target.getUniqueId(), k -> new LinkedHashMap<>())
                .put(p.getUniqueId(), new Request(p.getUniqueId(), here, System.currentTimeMillis() + ttl));

        p.sendMessage(Msg.ok("Teleport request sent to " + target.getName() + "."));
        Component text = Component.text(p.getName(), NamedTextColor.GOLD)
                .append(Component.text(here ? " wants you to teleport to them. " : " wants to teleport to you. ",
                        NamedTextColor.YELLOW))
                .append(Component.text("[Accept]", NamedTextColor.GREEN)
                        .clickEvent(ClickEvent.runCommand("/tpaccept " + p.getName())))
                .append(Component.space())
                .append(Component.text("[Deny]", NamedTextColor.RED)
                        .clickEvent(ClickEvent.runCommand("/tpdeny " + p.getName())));
        target.sendMessage(text);
        return true;
    }

    private Request resolve(Player target, String[] args) {
        Map<UUID, Request> m = pending.get(target.getUniqueId());
        if (m == null) return null;
        long now = System.currentTimeMillis();
        m.values().removeIf(r -> r.expiresAt() < now);
        if (m.isEmpty()) { pending.remove(target.getUniqueId()); return null; }
        if (args.length > 0) {
            Player s = Bukkit.getPlayerExact(args[0]);
            return s == null ? null : m.get(s.getUniqueId());
        }
        Request last = null;
        for (Request r : m.values()) last = r;
        return last;
    }

    private void remove(Player target, Request r) {
        Map<UUID, Request> m = pending.get(target.getUniqueId());
        if (m != null) {
            m.remove(r.from());
            if (m.isEmpty()) pending.remove(target.getUniqueId());
        }
    }

    private boolean accept(Player p, String[] args) {
        Request r = resolve(p, args);
        if (r == null) { p.sendMessage(Msg.err("No pending teleport request.")); return true; }
        remove(p, r);
        Player from = Bukkit.getPlayer(r.from());
        if (from == null || !from.isOnline()) { p.sendMessage(Msg.err("That player went offline.")); return true; }
        if (r.here()) p.teleport(from.getLocation());
        else from.teleport(p.getLocation());
        p.sendMessage(Msg.ok("Teleport request accepted."));
        from.sendMessage(Msg.ok(p.getName() + " accepted your teleport request."));
        return true;
    }

    private boolean deny(Player p, String[] args) {
        Request r = resolve(p, args);
        if (r == null) { p.sendMessage(Msg.err("No pending teleport request.")); return true; }
        remove(p, r);
        p.sendMessage(Msg.info("Teleport request denied."));
        Player from = Bukkit.getPlayer(r.from());
        if (from != null) from.sendMessage(Msg.err(p.getName() + " denied your teleport request."));
        return true;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        pending.remove(id);
        for (Map<UUID, Request> m : pending.values()) m.remove(id);
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        boolean isTpa = cmd.getName().equalsIgnoreCase("tpa");
        if (isTpa && args.length == 1) out.addAll(List.of("send", "accept", "deny", "here"));
        if (args.length == 1 || (isTpa && args.length == 2)) {
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        }
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
