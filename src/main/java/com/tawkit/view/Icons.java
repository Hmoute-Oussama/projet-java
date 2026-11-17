package com.tawkit.view;

import javax.imageio.ImageIO;
import java.awt.Image;
import java.io.InputStream;

/**
 * Loads bundled image resources with graceful fallbacks: a missing or
 * unreadable image never crashes the app, it just returns {@code null}.
 */
public final class Icons {

    /** Bundled application emblem shown in the window corner and taskbar. */
    public static final String APP_ICON = "/assets/icon.png";

    private Icons() {
    }

    /** @return the app icon, or {@code null} when the resource is missing. */
    public static Image appIcon() {
        return load(APP_ICON);
    }

    /** @return the image at the classpath resource, or {@code null}. */
    public static Image load(String resource) {
        try (InputStream in = Icons.class.getResourceAsStream(resource)) {
            if (in == null) return null;
            return ImageIO.read(in);
        } catch (Exception e) {
            com.tawkit.infrastructure.Log.warn("Icon not loaded: " + resource
                    + " (" + e.getMessage() + ")");
            return null;
        }
    }
}
