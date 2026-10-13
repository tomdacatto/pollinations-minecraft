package ai.pollinations.minecraft;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.bukkit.map.MapPalette;

/** Decodes, scales and palettes images for one 128x128 map. */
final class ImageMaps {

    static final int SIZE = 128;

    /** Refuse anything bigger than about 2048x2048 before decoding, so a crafted file cannot exhaust memory. */
    private static final long MAX_PIXELS = 4L * 1024 * 1024;

    /** Map palette indices (row-major, SIZE*SIZE bytes) plus a PNG of the scaled image for disk. */
    record Prepared(byte[] palette, byte[] png) {}

    private ImageMaps() {}

    static Prepared prepare(byte[] encoded) throws IOException {
        BufferedImage scaled = MapPalette.resizeImage(decode(encoded));
        return new Prepared(MapPalette.imageToBytes(scaled), toPng(scaled));
    }

    /** Rebuilds palette indices from a PNG written by {@link #prepare}. */
    static byte[] paletteFromPng(byte[] png) throws IOException {
        return MapPalette.imageToBytes(MapPalette.resizeImage(decode(png)));
    }

    /** Reads the first image in {@code encoded}. Throws IllegalArgumentException for unsupported or oversized input. */
    static BufferedImage decode(byte[] encoded) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(encoded))) {
            if (in == null) {
                throw new IllegalArgumentException("not a supported image format");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException("not a supported image format");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels > MAX_PIXELS) {
                    throw new IllegalArgumentException("image is too large");
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        }
    }

    private static byte[] toPng(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
