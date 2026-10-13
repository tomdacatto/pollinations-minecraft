package ai.pollinations.minecraft;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

/**
 * Turns generated images into filled-map items. Each map's PNG is kept under maps/<id>.png,
 * so the images come back after a restart.
 */
final class MapService {

    private final PollinationsPlugin plugin;
    private final Path dir;
    private final ExecutorService io;

    MapService(PollinationsPlugin plugin, Path dir, ExecutorService io) {
        this.plugin = plugin;
        this.dir = dir;
        this.io = io;
    }

    /**
     * Creates a map on the region thread that owns {@code at}, then passes the item to {@code handOut},
     * still on that thread. Callers must check the plugin is enabled first.
     */
    void create(Location at, ImageMaps.Prepared image, String title, Consumer<ItemStack> handOut) {
        Bukkit.getRegionScheduler().execute(plugin, at, () -> {
            MapView view = Bukkit.createMap(at.getWorld());
            attach(view, image.palette());
            ItemStack item = new ItemStack(Material.FILLED_MAP);
            MapMeta meta = (MapMeta) item.getItemMeta();
            meta.setMapView(view);
            meta.displayName(Component.text(title));
            item.setItemMeta(meta);
            savePng(view.getId(), image.png());
            handOut.accept(item);
        });
    }

    /** Re-attaches saved images to their maps after a restart. Runs on the server thread during enable. */
    void restore() {
        File[] files = dir.toFile().listFiles((folder, name) -> name.endsWith(".png"));
        if (files == null) {
            return;
        }
        Logger log = plugin.getLogger();
        int restored = 0;
        int missing = 0;
        for (File file : files) {
            int id;
            try {
                id = Integer.parseInt(file.getName().substring(0, file.getName().length() - 4));
            } catch (NumberFormatException notOurs) {
                continue;
            }
            MapView view = Bukkit.getMap(id);
            if (view == null) {
                missing++;
                continue;
            }
            try {
                attach(view, ImageMaps.paletteFromPng(Files.readAllBytes(file.toPath())));
                restored++;
            } catch (IOException | IllegalArgumentException unreadable) {
                log.warning("Could not restore the image for map " + id + ": " + unreadable.getClass().getSimpleName());
            }
        }
        log.info("Restored " + restored + " map image(s); " + missing + " saved map id(s) no longer exist.");
    }

    /**
     * Removes every existing renderer (including the default terrain one), locks the map and
     * stops position tracking, then draws the image.
     */
    private void attach(MapView view, byte[] palette) {
        for (MapRenderer renderer : List.copyOf(view.getRenderers())) {
            view.removeRenderer(renderer);
        }
        view.setLocked(true);
        view.setTrackingPosition(false);
        view.setUnlimitedTracking(false);
        view.addRenderer(new PaletteRenderer(palette));
    }

    private void savePng(int id, byte[] png) {
        io.execute(() -> {
            try {
                Files.createDirectories(dir);
                Files.write(dir.resolve(id + ".png"), png);
            } catch (IOException e) {
                plugin.getLogger().warning("Could not save the image for map " + id + ": " + e.getClass().getSimpleName());
            }
        });
    }

    /** Writes the stored palette into the canvas, touching only pixels that differ from what is shown. */
    private static final class PaletteRenderer extends MapRenderer {

        private final byte[] palette;

        PaletteRenderer(byte[] palette) {
            this.palette = palette;
        }

        @Override
        public void render(MapView map, MapCanvas canvas, Player player) {
            for (int y = 0; y < ImageMaps.SIZE; y++) {
                for (int x = 0; x < ImageMaps.SIZE; x++) {
                    byte color = palette[y * ImageMaps.SIZE + x];
                    if (canvas.getPixel(x, y) != color) {
                        canvas.setPixel(x, y, color);
                    }
                }
            }
        }
    }
}
