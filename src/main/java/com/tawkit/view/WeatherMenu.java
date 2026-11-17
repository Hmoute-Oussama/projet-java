package com.tawkit.view;

import com.tawkit.view.render.TextDraw;

import javax.swing.BorderFactory;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;

/**
 * The hamburger menu of the weather/location widget (task §6): a compact,
 * dark popup in the dashboard's visual theme.
 *
 * <pre>
 *   Location: Rabat, Morocco
 *   ⌖ Use my location
 *   🔎 Search city…
 *   ────────────────────
 *   Temperature: °C ⇄ °F
 *   ↻ Refresh weather
 *   Updated 18:42        (or "Not updated yet")
 *   ────────────────────
 *   ⚙ Settings…  📅 Monthly…  ⛶ Full screen  Quit
 * </pre>
 *
 * Closes on outside click and Escape (native popup behavior). The popup is
 * heavyweight, so a true fade animation is not reliable on all platforms —
 * opening is instant by design, which also feels snappier on kiosk hardware.
 */
public final class WeatherMenu extends JPopupMenu {

    /** Actions the menu triggers — wired by the controller / MainFrame. */
    public interface Actions {
        void useMyLocation();
        void searchCity();
        void refreshWeather();
        void toggleTemperatureUnit();
        void openSettings();
        void openMonthly();
        void toggleFullScreen();
        void quit();
    }

    private static final Color BG = TawkitTheme.CHARCOAL;
    private static final Color FG = TawkitTheme.WARM_WHITE;
    private static final Color ACCENT = TawkitTheme.GOLDEN_BEIGE;
    private static final Color MUTED = TextDraw.withAlpha(TawkitTheme.GRAY, 0.85f);

    /**
     * @param locationLabel  "Rabat, Morocco" or "No location set"
     * @param unit           'C' or 'F' (current unit)
     * @param updatedLabel   "Updated 18:42" or "Not updated yet"
     */
    public WeatherMenu(String locationLabel, char unit, String updatedLabel, Actions actions) {
        setBorder(BorderFactory.createLineBorder(TextDraw.withAlpha(ACCENT, 0.35f)));
        setBackground(BG);
        setOpaque(true);

        Font base = baseFont(13f);
        Font small = baseFont(11f);

        add(header("Location: " + locationLabel, base));
        add(item("⌖  Use my location", base, actions::useMyLocation));
        add(item("🔎  Search city…", base, actions::searchCity));
        addSeparator();
        add(header("Temperature: " + (unit == 'F' ? "°F" : "°C")
                + "  (click to switch)", base, actions::toggleTemperatureUnit));
        add(item("↻  Refresh weather", base, actions::refreshWeather));
        JMenuItem updated = header(updatedLabel, small);
        add(updated);
        addSeparator();
        add(item("⚙  Settings…", base, actions::openSettings));
        add(item("📅  Monthly prayer times…", base, actions::openMonthly));
        add(item("⛶  Full screen (F11)", base, actions::toggleFullScreen));
        add(item("✕  Quit (Ctrl+Q)", base, actions::quit));
    }

    private JMenuItem item(String text, Font font, Runnable action) {
        JMenuItem mi = new JMenuItem(text);
        mi.setFont(font);
        mi.setForeground(FG);
        mi.setBackground(BG);
        mi.setOpaque(true);
        mi.setBorderPainted(false);
        mi.setHorizontalAlignment(SwingConstants.LEFT);
        // Gold highlight on hover/selection — matches the dashboard accent
        mi.addChangeListener(e -> {
            boolean hot = mi.isSelected() || mi.getModel().isRollover();
            mi.setBackground(hot ? new Color(0x24, 0x20, 0x16) : BG);
            mi.setForeground(hot ? ACCENT : FG);
        });
        mi.addActionListener(e -> action.run());
        return mi;
    }

    /** Non-clickable label line (also used for the unit switcher, clickable). */
    private JMenuItem header(String text, Font font) {
        return header(text, font, null);
    }

    private JMenuItem header(String text, Font font, Runnable onClick) {
        JMenuItem mi = new JMenuItem(text);
        mi.setFont(font);
        mi.setForeground(MUTED);
        mi.setBackground(BG);
        mi.setOpaque(true);
        mi.setBorderPainted(false);
        mi.setHorizontalAlignment(SwingConstants.LEFT);
        mi.setEnabled(onClick != null);
        if (onClick != null) {
            mi.addChangeListener(e -> {
                boolean hot = mi.getModel().isRollover();
                mi.setForeground(hot ? ACCENT : MUTED);
            });
            mi.addActionListener(e -> onClick.run());
        }
        return mi;
    }

    private static Font baseFont(float size) {
        Font ui = UIManager.getFont("MenuItem.font");
        if (ui != null) return ui.deriveFont(size);
        return new Font(Font.SANS_SERIF, Font.PLAIN, (int) size);
    }
}
