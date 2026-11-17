package com.tawkit;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;

/**
 * One-shot generator for the bundled app icon
 * (src/main/resources/assets/icon.png) — a golden mosque dome with crescent
 * on a dark charcoal disc, matching the dashboard's palette. Run manually:
 * <pre>java src/main/java/com/tawkit/GenerateIcon.java</pre>
 */
public final class GenerateIcon {

    public static void main(String[] args) throws Exception {
        int size = 512;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Dark disc background
        g.setColor(new Color(0x10, 0x10, 0x10));
        g.fillOval(8, 8, size - 16, size - 16);

        Color gold = new Color(0xC5, 0xA6, 0x6A);
        Color copper = new Color(0xB7, 0x7B, 0x43);

        // Crescent (behind the dome, upper right)
        g.setColor(gold);
        double cx = 330, cy = 120, r = 62;
        g.fill(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
        g.setColor(new Color(0x10, 0x10, 0x10));
        g.fill(new Ellipse2D.Double(cx - r + 30, cy - r - 6, 2 * r, 2 * r));

        // Dome (onion shape)
        g.setColor(gold);
        Path2DDome dome = new Path2DDome();
        g.fill(dome.build(size));

        // Dome outline
        g.setStroke(new BasicStroke(10));
        g.setColor(copper);

        // Base / mosque body
        g.setColor(gold);
        g.fillRect(130, 330, 252, 70);
        g.setColor(copper);
        g.setStroke(new BasicStroke(8));
        g.drawRect(130, 330, 252, 70);

        // Door (dark arch)
        g.setColor(new Color(0x05, 0x05, 0x05));
        g.fillArc(216, 300, 80, 130, 0, 180);
        g.fillRect(216, 365, 80, 35);

        // Finial
        g.setColor(gold);
        g.fillRect(252, 96, 8, 40);
        g.fillOval(246, 82, 20, 20);

        g.dispose();
        Path out = Path.of("src/main/resources/assets/icon.png");
        if (args.length > 0) out = Path.of(args[0]);
        File file = out.toFile();
        file.getParentFile().mkdirs();
        ImageIO.write(img, "png", file);
        System.out.println("Icon written to " + out.toAbsolutePath());
    }

    /** Builds the onion-dome path. */
    private static final class Path2DDome {
        java.awt.geom.Path2D.Double build(int size) {
            java.awt.geom.Path2D.Double p = new java.awt.geom.Path2D.Double();
            p.moveTo(150, 340);
            p.quadTo(140, 240, 256, 150);
            p.quadTo(372, 240, 362, 340);
            p.closePath();
            return p;
        }
    }
}
