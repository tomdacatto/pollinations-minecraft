package ai.pollinations.minecraft;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

/** Routes /polli subcommands. Each subcommand checks its own permission node. */
final class PolliCommand implements CommandExecutor, TabCompleter {

    private static final String CHAT = "pollinations.chat";
    private static final String IMAGE = "pollinations.image";
    private static final String ADMIN = "pollinations.admin";
    private static final String USAGE =
            "Usage: /polli say <name> <message> | image <prompt> | npc create|remove|list | reload";
    private static final List<String> SUBCOMMANDS = List.of("say", "image", "npc", "reload");
    private static final List<String> NPC_ACTIONS = List.of("create", "remove", "list");

    private final PollinationsPlugin plugin;

    PolliCommand(PollinationsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            plugin.reply(sender, USAGE);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "say" -> {
                if (!has(sender, CHAT)) {
                    return true;
                }
                if (args.length < 3) {
                    plugin.reply(sender, PollinationsPlugin.USAGE_SAY);
                    return true;
                }
                plugin.say(sender, args[1], join(args, 2));
            }
            case "image" -> {
                if (!has(sender, IMAGE)) {
                    return true;
                }
                if (args.length < 2) {
                    plugin.reply(sender, PollinationsPlugin.USAGE_IMAGE);
                    return true;
                }
                plugin.image(sender, join(args, 1));
            }
            case "npc" -> npc(sender, args);
            case "reload" -> {
                if (has(sender, ADMIN)) {
                    plugin.reload(sender);
                }
            }
            default -> plugin.reply(sender, USAGE);
        }
        return true;
    }

    private void npc(CommandSender sender, String[] args) {
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (action) {
            case "create" -> {
                if (!has(sender, ADMIN)) {
                    return;
                }
                if (args.length < 4) {
                    plugin.reply(sender, PollinationsPlugin.USAGE_CREATE);
                    return;
                }
                plugin.createNpc(sender, args[2], join(args, 3));
            }
            case "remove" -> {
                if (!has(sender, ADMIN)) {
                    return;
                }
                if (args.length < 3) {
                    plugin.reply(sender, "Usage: /polli npc remove <name>");
                    return;
                }
                plugin.removeNpc(sender, args[2]);
            }
            case "list" -> {
                if (has(sender, CHAT)) {
                    plugin.listNpcs(sender);
                }
            }
            default -> plugin.reply(sender, "Usage: /polli npc create <name> <persona> | remove <name> | list");
        }
    }

    private boolean has(CommandSender sender, String node) {
        if (sender.hasPermission(node)) {
            return true;
        }
        plugin.reply(sender, "You do not have permission (" + node + ").");
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return matching(args[0], SUBCOMMANDS);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("npc")) {
            return matching(args[1], NPC_ACTIONS);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("say")) {
            return matching(args[1], plugin.npcNames());
        }
        return List.of();
    }

    private static List<String> matching(String prefix, List<String> options) {
        String wanted = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(wanted)) {
                out.add(option);
            }
        }
        return out;
    }

    private static String join(String[] args, int from) {
        return String.join(" ", Arrays.copyOfRange(args, from, args.length));
    }
}
