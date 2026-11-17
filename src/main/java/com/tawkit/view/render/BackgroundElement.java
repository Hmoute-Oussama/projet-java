package com.tawkit.view.render;

import com.tawkit.view.TawkitTheme;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Full-bleed background photograph (bundled or user-provided) plus the dark
 * cinematic overlay for the active theme.
 */
public final class BackgroundElement implements DashboardElement {

    private static final String BUNDLED_RESOURCE = "/assets/backgrounds/dome-of-the-rock.jpg";

    private String theme = "night";
    private BufferedImage customImage;
    private BufferedImage bundledImage;

    @Override
    public void render(Graphics2D g2, RenderContext ctx) {
        BufferedImage bg = customImage != null ? customImage : bundled();
        if (bg != null) {
            double scale = Math.max(ctx.width() / bg.getWidth(), ctx.height() / bg.getHeight());
            int bw = (int) Math.round(bg.getWidth() * scale);
            int bh = (int) Math.round(bg.getHeight() * scale);
            g2.drawImage(bg, (int) ((ctx.width() - bw) / 2), (int) ((ctx.height() - bh) / 2), bw, bh, null);
        } else {
            g2.setPaint(backgroundFor(theme, ctx));
            g2.fillRect(0, 0, (int) ctx.width(), (int) ctx.height());
        }
        g2.setColor(overlayFor(theme));
        g2.fillRect(0, 0, (int) ctx.width(), (int) ctx.height());
    }

    public void setTheme(String theme) {
        this.theme = theme == null ? "night" : theme;
        this.customImage = null;
    }

    public void setCustomImage(String path) {
        customImage = null;
        if (path == null || path.isBlank()) return;
        try {
            customImage = ImageIO.read(new File(path));
            if (customImage == null) {
                com.tawkit.infrastructure.Log.warn("Background not an image: " + path);
            }
        } catch (Exception e) {
            com.tawkit.infrastructure.Log.warn("Cannot load background " + path
                    + " (" + e.getMessage() + ")");
        }
    }

    private java.awt.Paint backgroundFor(String theme, RenderContext ctx) {
        Color top;
        Color bottom;
        switch (theme) {
            case "green" -> {
                top = new Color(8, 72, 48);
                bottom = new Color(2, 20, 18);
            }
            case "blue" -> {
                top = new Color(22, 68, 120);
                bottom = new Color(4, 16, 45);
            }
            default -> {
                top = new Color(48, 48, 62);
                bottom = TawkitTheme.BLACK;
            }
        }
        return new GradientPaint(0, 0, top, 0, (float) ctx.height(), bottom);
    }

    private BufferedImage bundled() {
        if (bundledImage == null) {
            try (var in = getClass().getResourceAsStream(BUNDLED_RESOURCE)) {
                if (in != null) bundledImage = ImageIO.read(in);
                else com.tawkit.infrastructure.Log.warn("Bundled background missing: " + BUNDLED_RESOURCE);
            } catch (Exception e) {
                com.tawkit.infrastructure.Log.warn("Bundled background unreadable ("
                        + e.getMessage() + ")");
            }
        }
        return bundledImage;
    }

    private Color overlayFor(String theme) {
        return switch (theme) {
            case "green" -> new Color(0, 20, 8, 175);
            case "blue" -> new Color(0, 10, 30, 175);
            default -> new Color(0, 0, 0, 185); // dark cinematic overlay (reference-matched)
        };
    }
}
