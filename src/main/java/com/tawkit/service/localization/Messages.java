package com.tawkit.service.localization;

import java.util.ListResourceBundle;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Small i18n layer. Bundles live in <code>/i18n/messages_XX.properties</code>.
 * Arabic switches the UI to RTL.
 */
public final class Messages {

    private static final String BASE = "i18n.messages";
    private static volatile Locale current = Locale.ENGLISH;

    private Messages() {
    }

    public static void setLanguage(String code) {
        current = switch (code == null ? "en" : code.toLowerCase()) {
            case "ar" -> new Locale("ar");
            case "fr" -> new Locale("fr");
            default -> Locale.ENGLISH;
        };
    }

    public static Locale getLocale() {
        return current;
    }

    public static boolean isRTL() {
        return "ar".equals(current.getLanguage());
    }

    /** In-code fallbacks so a damaged bundle never shows raw keys (change #3). */
    private static final java.util.Map<String, String> FALLBACKS = java.util.Map.of(
            "app.title", "Tawkit - Prayer Times"
    );

    public static String get(String key) {
        try {
            return ResourceBundle.getBundle(BASE, current).getString(key);
        } catch (MissingResourceException e) {
            try {
                return ResourceBundle.getBundle(BASE, Locale.ENGLISH).getString(key);
            } catch (MissingResourceException e2) {
                return FALLBACKS.getOrDefault(key, key);
            } catch (Exception e3) {
                return FALLBACKS.getOrDefault(key, key); // bundle files entirely missing
            }
        } catch (Exception e) {
            // Never let a broken resource crash the kiosk (change #3)
            return FALLBACKS.getOrDefault(key, key);
        }
    }

    /** Localized prayer name. */
    public static String prayerName(com.tawkit.model.prayer.PrayerType type) {
        return get("prayer." + type.name());
    }

    /** Fallback bundle used when no properties files are present. */
    public static class Defaults extends ListResourceBundle {
        @Override
        protected Object[][] getContents() {
            return new Object[][]{};
        }
    }
}
