package ai.pollinations.minecraft;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

/**
 * NPC definitions and per-player memory, saved as plugins/Pollinations/npcs.json.
 * Methods are synchronized; file writes go to the plugin's single I/O thread, in order.
 */
final class NpcStore {

    /** Memory key used for commands run from the server console. */
    static final String CONSOLE_MEMORY = "console";

    record Npc(String name, String persona) {}

    /** What {@link #remove} returns: the villager UUID hint, which may be missing. */
    record Removed(String name, String villager) {}

    private static final class Data {
        int version = 1;
        List<Entry> npcs = new ArrayList<>();
    }

    private static final class Entry {
        String name;
        String persona;
        String villager;
        Map<String, List<Turn>> memory = new HashMap<>();
    }

    private final Path file;
    private final Executor io;
    private final Logger log;
    private final Data data;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private NpcStore(Path file, Executor io, Logger log, Data data) {
        this.file = file;
        this.io = io;
        this.log = log;
        this.data = data;
    }

    static NpcStore load(Path file, Executor io, Logger log) {
        Data data = new Data();
        if (Files.exists(file)) {
            try {
                Data loaded = new Gson().fromJson(Files.readString(file, StandardCharsets.UTF_8), Data.class);
                if (loaded != null && loaded.npcs != null) {
                    data = loaded;
                }
            } catch (IOException | JsonParseException unreadable) {
                log.warning("npcs.json could not be read, so no NPCs were loaded. "
                        + "The old file was kept as npcs.json.bak.");
                try {
                    Files.move(file, file.resolveSibling("npcs.json.bak"), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ignored) {
                    // Nothing more to do: the next save will write a fresh file.
                }
            }
        }
        return new NpcStore(file, io, log, data);
    }

    synchronized Optional<Npc> find(String name) {
        Entry entry = entry(name);
        return entry == null ? Optional.empty() : Optional.of(new Npc(entry.name, entry.persona));
    }

    synchronized List<String> names() {
        List<String> names = new ArrayList<>();
        for (Entry entry : data.npcs) {
            names.add(entry.name);
        }
        return names;
    }

    /** Adds an NPC. Returns false when the name is already taken (case-insensitive). */
    synchronized boolean add(String name, String persona, String villagerUuid) {
        if (entry(name) != null) {
            return false;
        }
        Entry entry = new Entry();
        entry.name = name;
        entry.persona = persona;
        entry.villager = villagerUuid;
        data.npcs.add(entry);
        save();
        return true;
    }

    synchronized Optional<Removed> remove(String name) {
        Entry entry = entry(name);
        if (entry == null) {
            return Optional.empty();
        }
        data.npcs.remove(entry);
        save();
        return Optional.of(new Removed(entry.name, entry.villager));
    }

    /** The NPC's recent messages with this player, oldest first. Empty when there is none. */
    synchronized List<Turn> history(String npcName, String memoryKey) {
        Entry entry = entry(npcName);
        if (entry == null || entry.memory.get(memoryKey) == null) {
            return List.of();
        }
        return List.copyOf(entry.memory.get(memoryKey));
    }

    /** Appends one exchange and keeps only the newest {@code maxMessages} turns. */
    synchronized void remember(String npcName, String memoryKey, String userText, String reply, int maxMessages) {
        Entry entry = entry(npcName);
        if (entry == null) {
            return;
        }
        List<Turn> turns = new ArrayList<>(entry.memory.getOrDefault(memoryKey, List.of()));
        turns.add(new Turn("user", userText));
        turns.add(new Turn("assistant", reply));
        entry.memory.put(memoryKey, Turn.trim(turns, maxMessages));
        save();
    }

    private Entry entry(String name) {
        for (Entry entry : data.npcs) {
            if (entry.name.equalsIgnoreCase(name)) {
                return entry;
            }
        }
        return null;
    }

    /** Caller must hold the lock. Serializes now, writes later on the I/O thread. */
    private void save() {
        String json = gson.toJson(data);
        io.execute(() -> write(json));
    }

    private void write(String json) {
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling("npcs.json.tmp");
            Files.writeString(temp, json, StandardCharsets.UTF_8);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.warning("Could not save npcs.json: " + e.getClass().getSimpleName());
        }
    }
}
