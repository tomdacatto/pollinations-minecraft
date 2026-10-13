package ai.pollinations.minecraft;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.bukkit.map.MapPalette;
import org.junit.jupiter.api.Test;

class ImageMapsTest {

    @Test
    void solidColourImageBecomesA128SquarePaletteOfTheNearestMapColour() throws IOException {
        byte[] encoded = png(512, 512, Color.RED);

        ImageMaps.Prepared prepared = ImageMaps.prepare(encoded);

        assertEquals(ImageMaps.SIZE * ImageMaps.SIZE, prepared.palette().length);
        byte expected = MapPalette.matchColor(Color.RED);
        assertEquals(expected, prepared.palette()[0]);
        assertEquals(expected, prepared.palette()[ImageMaps.SIZE * ImageMaps.SIZE - 1]);
        BufferedImage stored = ImageMaps.decode(prepared.png());
        assertEquals(ImageMaps.SIZE, stored.getWidth());
        assertEquals(ImageMaps.SIZE, stored.getHeight());
    }

    @Test
    void palettePngRoundTripRebuildsTheSameIndices() throws IOException {
        ImageMaps.Prepared prepared = ImageMaps.prepare(png(300, 200, Color.BLUE));

        assertArrayEquals(prepared.palette(), ImageMaps.paletteFromPng(prepared.png()));
    }

    @Test
    void nonImageBytesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ImageMaps.prepare("definitely not an image".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    void imagesWithTooManyPixelsAreRejectedBeforeDecoding() throws IOException {
        byte[] huge = png(2049, 2049, Color.BLACK);

        assertThrows(IllegalArgumentException.class, () -> ImageMaps.decode(huge));
    }

    private static byte[] png(int width, int height, Color colour) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, colour.getRGB());
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
