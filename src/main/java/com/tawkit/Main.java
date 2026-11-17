package com.tawkit;

import com.tawkit.controller.AppController;
import com.tawkit.view.MainFrame;
import com.tawkit.view.MonthlyTimesDialog;
import com.tawkit.view.SettingsDialog;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Entry point: creates the window, wires the controller, shows the kiosk.
 */
public class Main {

    public static void main(String[] args) {
        com.tawkit.infrastructure.Log.info("Tawkit starting (build "
                + com.tawkit.Main.class.getPackage() + ")"); // eager log init + startup marker
        // One instance per machine — two fullscreen kiosks would fight (change #12)
        com.tawkit.infrastructure.SingleInstanceLock instanceLock =
                com.tawkit.infrastructure.SingleInstanceLock.acquire();
        if (instanceLock == null) {
            com.tawkit.infrastructure.Log.error("Tawkit is already running — exiting.");
            javax.swing.JOptionPane.showMessageDialog(null,
                    "Tawkit is already running.", "Tawkit",
                    javax.swing.JOptionPane.WARNING_MESSAGE);
            return;
        }

        com.tawkit.view.UiScale.apply(); // high-DPI dialogs (change #16)

        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                com.tawkit.infrastructure.Log.warn("Look-and-feel not available: " + e.getMessage());
            }

            MainFrame frame = new MainFrame();
            AppController controller = new AppController(frame);

            frame.setSettingsOpener(() -> {
                // Live settings passed in: Apply/Save push changes immediately
                SettingsDialog dialog = new SettingsDialog(frame, controller.getSettings());
                dialog.loadFromSettings(controller.getSettings());
                dialog.setOnApply(controller::settingsChanged);
                dialog.setVisible(true);
                // Changes are applied through onApply; no post-dialog work needed
            });

            // Monthly prayer-times dialog (#1)
            frame.setMonthlyOpener(() -> {
                MonthlyTimesDialog monthly = new MonthlyTimesDialog(frame, controller.getSettings());
                monthly.setVisible(true);
            });

            controller.start();
            frame.setVisible(true);
        });
    }
}
