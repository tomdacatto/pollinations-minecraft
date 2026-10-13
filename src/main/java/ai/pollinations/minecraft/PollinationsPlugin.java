package ai.pollinations.minecraft;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Pollinations for Paper: NPC villagers that talk through the chat API, and prompt-to-map images.
 * Targets Paper 1.21 only; uses Paper's region and entity schedulers.
 */
public final class PollinationsPlugin extends JavaPlugin {

    static final String NOT_CONFIGURED =
            "Pollinations is not set up yet. The server owner needs to add an API key to plugins/Pollinations/config.yml.";
    static final String USAGE_SAY = "Usage: /polli say <name> <message>";
    static final String USAGE_IMAGE = "Usage: /polli image <prompt>";
    static final String USAGE_CREATE = "Usage: /polli npc create <name> <persona>";

    private static final int IMAGE_PROMPT_MAX = 300;
    private static final int PERSONA_MAX = 400;
    private static final long HOUR_MILLIS = 3_600_000L;
    private static final String TEXT_CATALOGUE = "/text/models";
    private static final String IMAGE_CATALOGUE = "/image/models";

    private volatile Settings settings;
    private volatile RateLimiter chatLimit;
    private volatile RateLimiter imageLimit;

    private ExecutorService pool;
    private ExecutorService io;
    private Api api;
    private NpcStore npcs;
    private MapService maps;
    private NamespacedKey npcKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        pool = Executors.newCachedThreadPool(daemon("pollinations-http"));
        io = Executors.newSingleThreadExecutor(daemon("pollinations-io"));
        api = new Api(pool, getLogger());
        npcKey = new NamespacedKey(this, "npc");
        npcs = NpcStore.load(getDataFolder().toPath().resolve("npcs.json"), io, getLogger());
        maps = new MapService(this, getDataFolder().toPath().resolve("maps"), io);
        applyConfig();
        maps.restore();

        getServer().getPluginManager().registerEvents(new NpcListener(this), this);
        PluginCommand command = getCommand("polli");
        if (command != null) {
            PolliCommand handler = new PolliCommand(this);
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        }

        Settings current = settings;
        if (!current.hasKey()) {
            getLogger().warning("No api-key in config.yml, so AI features are off. Get a key at https://enter.pollinations.ai/keys");
        }
        getLogger().info("Enabled. Text model " + current.textModel() + ", image model " + current.imageModel() + ".");
        checkCatalogues();
    }

    @Override
    public void onDisable() {
        if (io != null) {
            io.shutdown();
            try {
                io.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        if (pool != null) {
            pool.shutdownNow();
        }
    }

    /** Reads config.yml and resets the rate limiters. Called on enable and on /polli reload. */
    void applyConfig() {
        Settings current = Settings.from(getConfig());
        settings = current;
        chatLimit = new RateLimiter(current.cooldownMillis(), current.maxRequestsPerHour(), HOUR_MILLIS);
        imageLimit = new RateLimiter(current.cooldownMillis(), current.maxRequestsPerHour(), HOUR_MILLIS);
    }

    void reload(CommandSender sender) {
        reloadConfig();
        applyConfig();
        checkCatalogues();
        reply(sender, "Pollinations config reloaded.");
    }

    /** Warns the admin log when a configured model is missing, paid-only or down. Uses public endpoints only. */
    private void checkCatalogues() {
        Settings current = settings;
        api.get(TEXT_CATALOGUE).thenAccept(result -> reportCatalogue(result, current.textModel(), "text"));
        api.get(IMAGE_CATALOGUE).thenAccept(result -> reportCatalogue(result, current.imageModel(), "image"));
    }

    private void reportCatalogue(Result<String> result, String model, String kind) {
        if (!isEnabled()) {
            return;
        }
        if (result.failed()) {
            getLogger().info("Could not check the live " + kind + " model list. Continuing without it.");
            return;
        }
        for (String warning : Catalogue.warnings(result.value(), model, kind)) {
            getLogger().warning(warning);
        }
    }

    /** /polli say <name> <message>, also used by right-click chat sessions. Replies go to the sender only. */
    void say(CommandSender sender, String npcName, String message) {
        Settings current = settings;
        String text = Text.clean(message, ChatCodec.USER_MESSAGE_MAX);
        if (text.isEmpty()) {
            reply(sender, USAGE_SAY);
            return;
        }
        Optional<NpcStore.Npc> npc = npcs.find(npcName);
        if (npc.isEmpty()) {
            reply(sender, "No NPC named " + npcName + ". Ask an admin to create one.");
            return;
        }
        if (!current.hasKey()) {
            reply(sender, NOT_CONFIGURED);
            return;
        }
        if (!allowed(sender, chatLimit)) {
            return;
        }

        String memoryKey = memoryKey(sender);
        String name = npc.get().name();
        String body = ChatCodec.buildBody(
                current.textModel(),
                current.maxTokens(),
                name,
                npc.get().persona(),
                npcs.history(name, memoryKey),
                text);
        api.chat(current.apiKey(), current.textModel(), body).thenAccept(result -> {
            if (!isEnabled()) {
                return;
            }
            if (result.failed()) {
                reply(sender, result.error());
                return;
            }
            npcs.remember(name, memoryKey, text, result.value(), current.memoryMessages());
            reply(sender, "<" + name + "> " + result.value());
        });
    }

    /** /polli image <prompt>: draws the image onto a new map item. */
    void image(CommandSender sender, String prompt) {
        Settings current = settings;
        String text = Text.clean(prompt, IMAGE_PROMPT_MAX);
        if (text.isEmpty()) {
            reply(sender, USAGE_IMAGE);
            return;
        }
        if (!current.hasKey()) {
            reply(sender, NOT_CONFIGURED);
            return;
        }
        if (!allowed(sender, imageLimit)) {
            return;
        }

        reply(sender, "Drawing \"" + text + "\". This can take up to a minute.");
        api.image(current.apiKey(), current.imageModel(), text).thenAccept(result -> {
            if (!isEnabled()) {
                return;
            }
            if (result.failed()) {
                reply(sender, result.error());
                return;
            }
            Location at = sender instanceof Player player
                    ? player.getLocation()
                    : Bukkit.getWorlds().get(0).getSpawnLocation();
            maps.create(at, result.value(), "Pollinations: " + Text.clean(text, 40), item -> give(sender, at, item));
        });
    }

    /** Runs on the region thread that owns {@code at}. */
    private void give(CommandSender sender, Location at, ItemStack item) {
        if (sender instanceof Player player) {
            player.getScheduler().run(this, task -> {
                player.getInventory().addItem(item).values()
                        .forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
                reply(player, "Your map is in your inventory.");
            }, () -> at.getWorld().dropItem(at, item));
        } else {
            at.getWorld().dropItem(at, item);
            reply(sender, "Map created at spawn.");
        }
    }

    void createNpc(CommandSender sender, String name, String persona) {
        if (!Text.validNpcName(name)) {
            reply(sender, "NPC names use letters, digits and underscores, up to 16 characters.");
            return;
        }
        String text = Text.clean(persona, PERSONA_MAX);
        if (text.isEmpty()) {
            reply(sender, USAGE_CREATE);
            return;
        }
        if (npcs.find(name).isPresent()) {
            reply(sender, "An NPC named " + name + " already exists.");
            return;
        }
        Location at = sender instanceof Player player
                ? player.getLocation().add(1, 0, 0)
                : Bukkit.getWorlds().get(0).getSpawnLocation();
        Bukkit.getRegionScheduler().execute(this, at, () -> {
            Villager villager = at.getWorld().spawn(at, Villager.class, npc -> configureNpc(npc, name));
            if (!npcs.add(name, text, villager.getUniqueId().toString())) {
                villager.remove();
                reply(sender, "An NPC named " + name + " already exists.");
                return;
            }
            reply(sender, "Created NPC " + name + " in " + at.getWorld().getName() + ". Right-click it to talk.");
        });
    }

    void removeNpc(CommandSender sender, String name) {
        Optional<NpcStore.Removed> removed = npcs.remove(name);
        if (removed.isEmpty()) {
            reply(sender, "No NPC named " + name + ".");
            return;
        }
        Entity villager = findVillager(removed.get().villager());
        if (villager == null) {
            reply(sender, "Removed " + removed.get().name() + " from the list. Its villager is in an unloaded chunk "
                    + "or was already gone; delete it by hand if it is still there.");
            return;
        }
        villager.getScheduler().run(this, task -> villager.remove(), null);
        reply(sender, "Removed NPC " + removed.get().name() + ".");
    }

    void listNpcs(CommandSender sender) {
        List<String> names = npcs.names();
        reply(sender, names.isEmpty()
                ? "No NPCs yet. Create one with /polli npc create <name> <persona>."
                : "NPCs: " + String.join(", ", names));
    }

    /** NPC name stored on a villager, or null when the villager is not one of ours. */
    String npcNameOf(Villager villager) {
        return villager.getPersistentDataContainer().get(npcKey, PersistentDataType.STRING);
    }

    Optional<NpcStore.Npc> npc(String name) {
        return npcs.find(name);
    }

    List<String> npcNames() {
        return npcs.names();
    }

    /** Sends a line to a player on their own thread, or straight to the console. Never blocks. */
    void reply(CommandSender sender, String text) {
        reply(sender, Component.text(text));
    }

    void reply(CommandSender sender, Component message) {
        if (sender instanceof Player player) {
            player.getScheduler().run(this, task -> player.sendMessage(message), null);
        } else {
            sender.sendMessage(message);
        }
    }

    /** Console is never rate limited. Players get a friendly wait message when they are over a limit. */
    private boolean allowed(CommandSender sender, RateLimiter limiter) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        RateLimiter.Decision decision = limiter.tryAcquire(player.getUniqueId(), System.currentTimeMillis());
        if (!decision.allowed()) {
            long seconds = Math.max(1, (decision.retryAfterMillis() + 999) / 1000);
            reply(sender, "Slow down. Try again in " + seconds + "s.");
        }
        return decision.allowed();
    }

    private static String memoryKey(CommandSender sender) {
        return sender instanceof Player player ? player.getUniqueId().toString() : NpcStore.CONSOLE_MEMORY;
    }

    private void configureNpc(Villager villager, String name) {
        villager.customName(Component.text(name));
        villager.setCustomNameVisible(true);
        villager.setAI(false);
        villager.setInvulnerable(true);
        villager.setPersistent(true);
        villager.setRemoveWhenFarAway(false);
        villager.setSilent(true);
        villager.setProfession(Villager.Profession.NONE);
        villager.getPersistentDataContainer().set(npcKey, PersistentDataType.STRING, name);
    }

    private Entity findVillager(String uuid) {
        if (uuid == null || uuid.isEmpty()) {
            return null;
        }
        try {
            return Bukkit.getEntity(UUID.fromString(uuid));
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    private static ThreadFactory daemon(String name) {
        return runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        };
    }
}
