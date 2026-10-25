package dev.example.socialspy;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SocialSpyPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    /** Private-message commands (lowercase, without the slash). */
    private static final Set<String> PM_COMMANDS = Set.of("msg", "tell", "w", "whisper", "m", "t", "pm", "message");
    private static final List<String> ACTIONS = List.of("on", "off", "status");

    /** Players who currently have social spy enabled. */
    private final Set<UUID> spies = ConcurrentHashMap.newKeySet();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        var cmd = getCommand("socialspy");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }
        getLogger().info("SocialSpy enabled.");
    }

    @Override
    public void onDisable() {
        spies.clear();
    }

    // ----- /socialspy (/ss) -----

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        String action = args.length == 0 ? "toggle" : args[0].toLowerCase(Locale.ROOT);
        UUID id = player.getUniqueId();

        switch (action) {
            case "toggle" -> {
                if (spies.remove(id)) {
                    player.sendMessage(Component.text("SocialSpy disabled.", NamedTextColor.RED));
                } else {
                    spies.add(id);
                    player.sendMessage(Component.text("SocialSpy enabled.", NamedTextColor.GREEN));
                }
            }
            case "on" -> {
                spies.add(id);
                player.sendMessage(Component.text("SocialSpy enabled.", NamedTextColor.GREEN));
            }
            case "off" -> {
                spies.remove(id);
                player.sendMessage(Component.text("SocialSpy disabled.", NamedTextColor.RED));
            }
            case "status" -> player.sendMessage(Component.text(
                    "SocialSpy is " + (spies.contains(id) ? "enabled." : "disabled."), NamedTextColor.GRAY));
            default -> player.sendMessage(Component.text("Usage: /" + label + " [on|off|status]", NamedTextColor.RED));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return ACTIONS.stream().filter(a -> a.startsWith(prefix)).toList();
        }
        return List.of();
    }

    // ----- Message interception -----

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (spies.isEmpty()) return;

        // Strip the leading '/' and split into: label, target, message
        String raw = event.getMessage().substring(1);
        String[] parts = raw.split(" ", 3);
        if (parts.length < 3) return;

        String label = parts[0].toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':'); // handles namespaced forms like minecraft:msg
        if (colon >= 0) label = label.substring(colon + 1);
        if (!PM_COMMANDS.contains(label)) return;

        Player sender = event.getPlayer();
        Component line = Component.text("[SS] ", NamedTextColor.DARK_GRAY)
                .append(Component.text(sender.getName(), NamedTextColor.YELLOW))
                .append(Component.text(" -> ", NamedTextColor.GRAY))
                .append(Component.text(parts[1], NamedTextColor.YELLOW))
                .append(Component.text(": " + parts[2], NamedTextColor.WHITE));

        for (UUID spyId : spies) {
            Player spy = Bukkit.getPlayer(spyId);
            if (spy == null || spy.equals(sender)) continue;
            // Folia: send through the spy's own entity scheduler so we stay on their region thread
            spy.getScheduler().run(this, task -> spy.sendMessage(line), null);
        }
    }
}
