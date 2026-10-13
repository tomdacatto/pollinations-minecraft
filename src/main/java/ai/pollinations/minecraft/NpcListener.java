package ai.pollinations.minecraft;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Right-clicking an NPC opens a short chat session. While it lasts, the player's chat lines go to
 * that NPC privately instead of to everyone. The session ends after 'bye' or 3 idle minutes.
 */
final class NpcListener implements Listener {

    private static final long SESSION_MILLIS = 3 * 60_000L;

    private record Talk(String npc, long expiresAt) {}

    private final PollinationsPlugin plugin;
    private final Map<UUID, Talk> talks = new ConcurrentHashMap<>();

    NpcListener(PollinationsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !(event.getRightClicked() instanceof Villager villager)) {
            return;
        }
        String name = plugin.npcNameOf(villager);
        if (name == null || plugin.npc(name).isEmpty()) {
            return;
        }
        // Cancel the trade screen for every NPC villager, even when the player lacks permission.
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!player.hasPermission("pollinations.chat")) {
            plugin.reply(player, "You do not have permission (pollinations.chat).");
            return;
        }
        talks.put(player.getUniqueId(), new Talk(name, System.currentTimeMillis() + SESSION_MILLIS));
        plugin.reply(player, name + " is listening. Type your message in chat, or 'bye' to stop.");
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Talk talk = talks.get(player.getUniqueId());
        if (talk == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now > talk.expiresAt()) {
            talks.remove(player.getUniqueId());
            return;
        }
        // Private: the line is not broadcast to other players.
        event.setCancelled(true);
        String line = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        if (line.equalsIgnoreCase("bye")) {
            talks.remove(player.getUniqueId());
            plugin.reply(player, talk.npc() + " waves goodbye.");
            return;
        }
        talks.put(player.getUniqueId(), new Talk(talk.npc(), now + SESSION_MILLIS));
        plugin.say(player, talk.npc(), line);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        talks.remove(event.getPlayer().getUniqueId());
    }
}
