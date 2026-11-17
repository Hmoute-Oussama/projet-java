package com.tawkit.view;

import javax.swing.UIManager;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Toolkit;

/**
 * High-DPI font scaling for dialogs and menus. The kiosk dashboard already
 * scales with screen size (its elements use vw/vh fractions), but Swing's
 * default dialog fonts do not — on a 4K mosque TV the settings dialog would
 * be unreadably small. This applies a factor of 1.0x–2.0x to the standard
 * UI fonts, derived from the screen height (1080p → 1.0x, 4K → 2.0x).
 */
public final class UiScale {

    private static final String[] SCALED_KEYS = {
            "Label.font", "Button.font", "ToggleButton.font", "CheckBox.font",
            "RadioButton.font", "ComboBox.font", "TextField.font", "TextArea.font",
            "EditorPane.font", "List.font", "Table.font", "TableHeader.font",
            "TitledBorder.font", "Menu.font", "MenuItem.font", "PopupMenu.font",
            "TabbedPane.font", "Spinner.font", "OptionPane.font", "ToolTip.font"
    };

    private UiScale() {
    }

    /** Applies the font scaling based on the current screen size. */
    public static void apply() {
        double factor = factor();
        if (factor <= 1.001) return;
        for (String key : SCALED_KEYS) {
            Font font = UIManager.getFont(key);
            if (font != null) {
                UIManager.put(key, font.deriveFont((float) (font.getSize() * factor)));
            }
        }
        com.tawkit.infrastructure.Log.info("UI scale " + factor + "x applied (high-DPI screen)");
    }

    /**
     * @return scale factor in 0.5 steps, clamped to [1.0, 2.0]:
     *         1080p → 1.0, 1440p → 1.5, 4K → 2.0.
     */
    public static double factor() {
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        double raw = screen.getHeight() / 1080.0;
        double stepped = Math.round(raw * 2.0) / 2.0;
        return Math.max(1.0, Math.min(2.0, stepped));
    }
}
