package com.tawkit.view;

import com.tawkit.model.prayer.AsrMethod;
import com.tawkit.model.prayer.CalculationMethod;
import com.tawkit.model.prayer.IqamaSettings;
import com.tawkit.model.prayer.PrayerType;
import com.tawkit.model.settings.AppSettings;
import com.tawkit.service.city.CityService;
import com.tawkit.service.settings.SettingsService;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * Full settings dialog, Tawkit-style: location with city picker, prayer
 * calculation, iqama (offset or fixed), audio, display, content, reminders
 * and settings backup (export/import).
 */
public class SettingsDialog extends JDialog {

    private static final PrayerType[] IQAMA_PRAYERS = {
            PrayerType.FAJR, PrayerType.DHUHR, PrayerType.ASR, PrayerType.MAGHRIB, PrayerType.ISHA
    };

    private final CityService cityService = new CityService();

    // Location
    private final JTextField mosqueField = new JTextField(20);
    private final JComboBox<String> countryCombo = new JComboBox<>();
    private final JComboBox<String> cityCombo = new JComboBox<>();
    private final JTextField latField = new JTextField(8);
    private final JTextField lonField = new JTextField(8);
    private final JTextField timezoneField = new JTextField(5);

    // Prayer
    private final JComboBox<CalculationMethod> methodCombo = new JComboBox<>(CalculationMethod.values());
    private final JComboBox<AsrMethod> asrCombo = new JComboBox<>(AsrMethod.values());
    private final Map<PrayerType, JSpinner> adjustSpinners = new LinkedHashMap<>();

    // Iqama
    private final Map<PrayerType, JSpinner> iqamaOffsets = new LinkedHashMap<>();
    private final Map<PrayerType, JCheckBox> fixedChecks = new LinkedHashMap<>();
    private final Map<PrayerType, JTextField> fixedFields = new LinkedHashMap<>();

    // Audio
    private final JCheckBox adhanCheck = new JCheckBox("Play adhan at prayer times", true);
    private final JSlider volumeSlider = new JSlider(0, 100, 100);
    private final JCheckBox preFajrAlertCheck = new JCheckBox("Alert before Fajr", true);
    private final JSpinner preFajrMinutes = new JSpinner(new SpinnerNumberModel(10, 1, 60, 1));
    /** Custom adhan WAV per prayer (#6) — empty text uses the bundled sound. */
    private final Map<PrayerType, JTextField> adhanFileFields = new LinkedHashMap<>();

    // Jumu'a (#5)
    private final JCheckBox jumuaCheck = new JCheckBox("Jumu'a mode (khutba time replaces Dhuhr iqama on Fridays)", true);
    private final JTextField jumuaKhutbaField = new JTextField(6);

    // Display
    private final JCheckBox dimCheck = new JCheckBox("Dim screen during prayer", true);
    private final JSpinner dimMinutes = new JSpinner(new SpinnerNumberModel(10, 0, 60, 1));
    private final JCheckBox clock24Check = new JCheckBox("24-hour clock", true);
    private final JCheckBox secondsCheck = new JCheckBox("Show seconds", true);
    private final JSpinner hijriOffset = new JSpinner(new SpinnerNumberModel(0, -3, 3, 1));
    private final JComboBox<String> themeCombo = new JComboBox<>(
            new String[]{"night", "green", "blue", "custom"});
    private final JTextField customBgField = new JTextField(18);
    private final JButton customBgBrowse = new JButton("…");
    private final JComboBox<String> languageCombo = new JComboBox<>(
            new String[]{"en", "ar", "fr"});
    private final JComboBox<String> unitCombo = new JComboBox<>(new String[]{"°C", "°F"});

    // Content
    private final JSpinner rotationSpinner = new JSpinner(new SpinnerNumberModel(18, 5, 120, 1));
    private final JCheckBox slidesCheck = new JCheckBox("Show user slides between prayers", true);
    private final JCheckBox weatherCheck = new JCheckBox("Weather forecast (internet)", true);
    private final JTextArea announcementsArea = new JTextArea(5, 26);

    // Reminders
    private final DefaultListModel<String> reminderModel = new DefaultListModel<>();

    private boolean saved = false;
    /** Live dashboard preview (#15) — a real TawkitScreen at miniature size. */
    private final TawkitScreen previewScreen = new TawkitScreen();
    private boolean previewUpdating = false;
    /**
     * The live settings object this dialog edits (applied immediately on
     * "Apply" / "Save" so the app reflects changes without restart).
     */
    private AppSettings liveSettings;
    /** Fired after changes were pushed into the live settings. */
    private Runnable onApply;

    public SettingsDialog(JFrame parent) {
        this(parent, null);
    }

    /**
     * Preferred constructor: pass the live {@link AppSettings} so
     * <b>Apply</b> pushes changes straight into the running app via
     * {@link #setOnApply(Runnable)}.
     */
    public SettingsDialog(JFrame parent, AppSettings settings) {
        super(parent, "Settings", true);
        this.liveSettings = settings;
        setIconImage(Icons.appIcon());
        buildUI();
        loadCityCountries();
        setSize(620, 700);
        setLocationRelativeTo(parent);
        // ESC closes the dialog like Cancel
        javax.swing.InputMap im = getRootPane().getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
        javax.swing.ActionMap am = getRootPane().getActionMap();
        im.put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), "closeDialog");
        am.put("closeDialog", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                dispose();
            }
        });
        // Stop preview timers when the dialog is closed, so nothing repaints a ghost window
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                previewScreen.stop();
            }
        });
    }

    private void buildUI() {
        setLayout(new BorderLayout(8, 8));

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Location", buildLocationTab());
        tabs.addTab("Prayer", buildPrayerTab());
        tabs.addTab("Iqama", buildIqamaTab());
        tabs.addTab("Audio", buildAudioTab());
        tabs.addTab("Display", buildDisplayTab());
        tabs.addTab("Content", buildContentTab());
        tabs.addTab("Reminders", buildRemindersTab());
        tabs.addTab("Backup", buildBackupTab());
        // Scrollable so every control stays reachable on small/4K screens
        JScrollPane tabsScroll = new JScrollPane(tabs);
        tabsScroll.setBorder(null);
        tabsScroll.getVerticalScrollBar().setUnitIncrement(16);
        add(tabsScroll, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton applyButton = new JButton("Apply");
        applyButton.setToolTipText("Apply changes to the app without closing");
        applyButton.addActionListener(e -> applyNow());
        JButton saveButton = new JButton("Save");
        JButton cancelButton = new JButton("Cancel");
        saveButton.addActionListener(e -> {
            saved = true;
            applyNow();
            dispose();
        });
        cancelButton.addActionListener(e -> dispose());
        buttonPanel.add(saveButton);
        buttonPanel.add(applyButton);
        buttonPanel.add(cancelButton);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    /** Pushes the dialog contents into the live settings and notifies the app. */
    private void applyNow() {
        if (liveSettings == null) return;
        applyToSettings(liveSettings);
        if (onApply != null) onApply.run();
        refreshPreview(); // reflect theme/bg immediately in the mini dashboard
    }

    /** Registers the callback that applies changes to the running app. */
    public void setOnApply(Runnable onApply) {
        this.onApply = onApply;
    }

    private void loadCityCountries() {
        countryCombo.removeAllItems();
        for (String country : cityService.getCountries()) {
            countryCombo.addItem(country);
        }
        countryCombo.addActionListener(e -> refreshCityCombo());
    }

    private void refreshCityCombo() {
        cityCombo.removeAllItems();
        String country = (String) countryCombo.getSelectedItem();
        if (country == null) return;
        for (String city : cityService.getCitiesOf(country)) {
            cityCombo.addItem(city);
        }
    }

    // ---------------------------------------------------------------- tabs

    private JPanel buildLocationTab() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc = grid();

        gbc.gridy = 0;
        panel.add(new JLabel("Mosque / location name:"), gbc);
        gbc.gridx = 1;
        panel.add(mosqueField, gbc);

        gbc.gridy = 1; gbc.gridx = 0;
        panel.add(new JLabel("Country:"), gbc);
        gbc.gridx = 1;
        panel.add(countryCombo, gbc);

        gbc.gridy = 2; gbc.gridx = 0;
        panel.add(new JLabel("City:"), gbc);
        gbc.gridx = 1;
        panel.add(cityCombo, gbc);

        gbc.gridy = 3; gbc.gridx = 0;
        panel.add(new JLabel("Latitude:"), gbc);
        gbc.gridx = 1;
        panel.add(latField, gbc);

        gbc.gridy = 4; gbc.gridx = 0;
        panel.add(new JLabel("Longitude:"), gbc);
        gbc.gridx = 1;
        panel.add(lonField, gbc);

        gbc.gridy = 5; gbc.gridx = 0;
        panel.add(new JLabel("Timezone (UTC offset):"), gbc);
        gbc.gridx = 1;
        panel.add(timezoneField, gbc);

        gbc.gridy = 6; gbc.gridx = 0; gbc.gridwidth = 2;
        JButton applyCity = new JButton("Apply selected city");
        applyCity.addActionListener(e -> applySelectedCity());
        panel.add(applyCity, gbc);

        gbc.gridy = 7; gbc.gridwidth = 2;
        panel.add(new JLabel("<html><i>Pick a country and city, then press apply — "
                + "or type coordinates manually.</i></html>"), gbc);
        return panel;
    }

    private void applySelectedCity() {
        String country = (String) countryCombo.getSelectedItem();
        String city = (String) cityCombo.getSelectedItem();
        if (country == null || city == null) return;
        CityService.City found = cityService.find(country, city);
        if (found != null) {
            latField.setText(String.valueOf(found.latitude()));
            lonField.setText(String.valueOf(found.longitude()));
            timezoneField.setText(String.valueOf(found.timezone()));
        }
    }

    private JPanel buildPrayerTab() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc = grid();

        gbc.gridy = 0;
        panel.add(new JLabel("Calculation method:"), gbc);
        gbc.gridx = 1;
        panel.add(methodCombo, gbc);
        methodCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof CalculationMethod m) setText(m.getDisplayName());
                return this;
            }
        });

        gbc.gridy = 1; gbc.gridx = 0;
        panel.add(new JLabel("Asr method:"), gbc);
        gbc.gridx = 1;
        panel.add(asrCombo, gbc);
        asrCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof AsrMethod m) {
                    setText(m == AsrMethod.HANAFI ? "Hanafi (shadow x2)" : "Standard (shadow x1)");
                }
                return this;
            }
        });

        gbc.gridy = 2; gbc.gridx = 0; gbc.gridwidth = 2;
        panel.add(new JLabel("Manual adjustments (minutes, ±30):"), gbc);
        gbc.gridwidth = 1;

        int row = 3;
        for (PrayerType type : PrayerType.values()) {
            JSpinner spinner = new JSpinner(new SpinnerNumberModel(0, -30, 30, 1));
            adjustSpinners.put(type, spinner);
            gbc.gridy = row; gbc.gridx = 0;
            panel.add(new JLabel(type.getDisplayName() + ":"), gbc);
            gbc.gridx = 1;
            panel.add(spinner, gbc);
            row++;
        }

        // ---- Jumu'a mode (#5) ----
        gbc.gridy = row + 1; gbc.gridx = 0; gbc.gridwidth = 2;
        panel.add(jumuaCheck, gbc);
        gbc.gridwidth = 1;
        gbc.gridy = row + 2; gbc.gridx = 0;
        panel.add(new JLabel("Khutba time (Friday):"), gbc);
        gbc.gridx = 1;
        JPanel khutbaRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        khutbaRow.add(jumuaKhutbaField);
        khutbaRow.add(new JLabel("HH:MM"));
        panel.add(khutbaRow, gbc);
        return panel;
    }

    private JPanel buildIqamaTab() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc = grid();

        gbc.gridy = 0; gbc.gridx = 0; gbc.gridwidth = 3;
        panel.add(new JLabel("Iqama: minutes after adhan, or a fixed clock time (HH:MM)."), gbc);
        gbc.gridwidth = 1;

        int row = 1;
        for (PrayerType type : IQAMA_PRAYERS) {
            JSpinner spinner = new JSpinner(new SpinnerNumberModel(15, 1, 120, 1));
            iqamaOffsets.put(type, spinner);

            JCheckBox fixedCheck = new JCheckBox();
            fixedChecks.put(type, fixedCheck);
            JTextField fixedField = new JTextField(6);
            fixedField.setEnabled(false);
            fixedFields.put(type, fixedField);
            fixedCheck.addActionListener(e -> fixedField.setEnabled(fixedCheck.isSelected()));

            gbc.gridy = row; gbc.gridx = 0;
            panel.add(new JLabel(type.getDisplayName() + ":"), gbc);
            gbc.gridx = 1;
            panel.add(spinner, gbc);
            gbc.gridx = 2;
            JPanel fixedPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            fixedPanel.add(fixedCheck);
            fixedPanel.add(fixedField);
            panel.add(fixedPanel, gbc);
            row++;
        }
        return panel;
    }

    private JPanel buildAudioTab() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc = grid();

        gbc.gridy = 0; gbc.gridx = 0; gbc.gridwidth = 2;
        panel.add(adhanCheck, gbc);
        gbc.gridwidth = 1;

        gbc.gridy = 1; gbc.gridx = 0;
        panel.add(new JLabel("Volume:"), gbc);
        gbc.gridx = 1;
        panel.add(volumeSlider, gbc);

        gbc.gridy = 2; gbc.gridx = 0; gbc.gridwidth = 2;
        panel.add(preFajrAlertCheck, gbc);
        gbc.gridwidth = 1;

        gbc.gridy = 3; gbc.gridx = 0;
        panel.add(new JLabel("Minutes before Fajr:"), gbc);
        gbc.gridx = 1;
        panel.add(preFajrMinutes, gbc);

        // Live test so the slider is verifiable immediately
        gbc.gridy = 4; gbc.gridx = 0;
        panel.add(new JLabel(""), gbc);
        gbc.gridx = 1;
        JButton testSound = new JButton("🔊 Test sound");
        testSound.addActionListener(e -> {
            double vol = volumeSlider.getValue() / 100.0;
            Thread player = new Thread(() -> {
                try {
                    com.tawkit.infrastructure.audio.ToneGenerator.playSequence(
                            com.tawkit.infrastructure.audio.ToneGenerator.alertBeeps(), vol);
                } catch (Exception ex) {
                    com.tawkit.infrastructure.Log.warn("Sound test failed ("
                            + ex.getMessage() + ")");
                }
            }, "tawkit-soundtest");
            player.setDaemon(true);
            player.start();
        });
        JPanel testRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        testRow.add(testSound);
        panel.add(testRow, gbc);

        gbc.gridy = 5; gbc.gridx = 0; gbc.gridwidth = 2;
        panel.add(new JLabel("<html><i>Adhan plays bundled WAV files from "
                + "<b>assets/audio/</b> — or a chime when none exist.</i></html>"), gbc);

        // ---- Per-prayer custom adhan files (#6) ----
        gbc.gridy = 5; gbc.gridx = 0; gbc.gridwidth = 2;
        panel.add(new JLabel("Custom adhan WAV per prayer (blank = bundled):"), gbc);
        gbc.gridwidth = 1;
        int fileRow = 6;
        for (PrayerType type : IQAMA_PRAYERS) {
            JTextField field = new JTextField(14);
            adhanFileFields.put(type, field);
            JButton browse = new JButton("…");
            browse.addActionListener(e -> chooseAdhanFile(field));

            gbc.gridy = fileRow; gbc.gridx = 0;
            panel.add(new JLabel(type.getDisplayName() + ":"), gbc);
            gbc.gridx = 1;
            JPanel row = new JPanel(new BorderLayout(4, 0));
            row.add(field, BorderLayout.CENTER);
            row.add(browse, BorderLayout.EAST);
            panel.add(row, gbc);
            fileRow++;
        }
        return panel;
    }

    private void chooseAdhanFile(JTextField target) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("WAV audio", "wav"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            target.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private JPanel buildDisplayTab() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc = grid();

        gbc.gridy = 0;
        panel.add(new JLabel("Language:"), gbc);
        gbc.gridx = 1;
        panel.add(languageCombo, gbc);

        gbc.gridy = 1; gbc.gridx = 0; gbc.gridwidth = 2;
        panel.add(clock24Check, gbc);
        panel.add(secondsCheck, gbc);

        gbc.gridy = 2; gbc.gridx = 0; gbc.gridwidth = 2;
        panel.add(dimCheck, gbc);
        gbc.gridwidth = 1;

        gbc.gridy = 3; gbc.gridx = 0;
        panel.add(new JLabel("Dim duration (min after iqama):"), gbc);
        gbc.gridx = 1;
        panel.add(dimMinutes, gbc);

        gbc.gridy = 4; gbc.gridx = 0;
        panel.add(new JLabel("Hijri day offset (±3):"), gbc);
        gbc.gridx = 1;
        panel.add(hijriOffset, gbc);

        gbc.gridy = 5; gbc.gridx = 0;
        panel.add(new JLabel("Background theme:"), gbc);
        gbc.gridx = 1;
        panel.add(themeCombo, gbc);

        gbc.gridy = 7; gbc.gridx = 0;
        panel.add(new JLabel("Temperature unit:"), gbc);
        gbc.gridx = 1;
        panel.add(unitCombo, gbc);

        gbc.gridy = 6; gbc.gridx = 0;
        panel.add(new JLabel("Custom background image:"), gbc);
        gbc.gridx = 1;
        JPanel bgRow = new JPanel(new BorderLayout(4, 0));
        bgRow.add(customBgField, BorderLayout.CENTER);
        customBgBrowse.addActionListener(e -> chooseBackgroundImage());
        bgRow.add(customBgBrowse, BorderLayout.EAST);
        panel.add(bgRow, gbc);

        // ---- Live dashboard preview (#15) ----
        gbc.gridy = 8; gbc.gridx = 0; gbc.gridwidth = 2;
        panel.add(new JLabel("Live preview (theme, background, mosque name):"), gbc);
        gbc.gridwidth = 1;
        gbc.gridy = 9; gbc.gridx = 0; gbc.gridwidth = 2;
        previewScreen.setPreferredSize(new Dimension(340, 192));
        previewScreen.setMosqueName(mosqueField.getText());
        previewScreen.applyTheme((String) themeCombo.getSelectedItem());
        seedPreviewWithPrayerData(); // realistic dashboard content
        themeCombo.addActionListener(e -> previewScreen.applyTheme(
                (String) themeCombo.getSelectedItem()));
        customBgField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { refreshPreview(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { refreshPreview(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { refreshPreview(); }
        });
        mosqueField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) {
                previewScreen.setMosqueName(mosqueField.getText());
            }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) {
                previewScreen.setMosqueName(mosqueField.getText());
            }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) {
                previewScreen.setMosqueName(mosqueField.getText());
            }
        });
        panel.add(previewScreen, gbc);
        return panel;
    }

    /**
     * Fills the mini dashboard with today's real times (computed from the
     * live settings) so the preview mirrors what the kiosk actually shows.
     */
    private void seedPreviewWithPrayerData() {
        try {
            AppSettings s = liveSettings != null ? liveSettings : new AppSettings();
            var loc = s.getLocation();
            double lat = loc != null ? loc.getLatitude() : 33.5731;
            double lon = loc != null ? loc.getLongitude() : -7.5898;
            double tz = com.tawkit.service.prayer.PrayerScheduler.resolveTimezone(loc);
            var raw = new com.tawkit.service.prayer.PrayerTimeCalculator().compute(
                    java.time.LocalDate.now(), lat, lon, tz,
                    s.getCalculationMethod(), s.getAsrMethod());
            var day = new com.tawkit.model.prayer.DailyPrayerTimes(java.time.LocalDate.now());
            for (PrayerType type : PrayerType.values()) {
                if (raw.get(type) != null) {
                    day.put(type, new com.tawkit.model.prayer.PrayerEntry(
                            type, raw.get(type), null));
                }
            }
            previewScreen.setPrayerTimes(day);
            previewScreen.setClockMode(s.isUse24HourClock(), s.isShowSeconds());
            var h = new com.tawkit.service.prayer.HijriCalendarService()
                    .toHijri(java.time.LocalDate.now());
            previewScreen.setDate(java.time.LocalDate.now(),
                    h.getDay(), h.getMonth(), h.getYear());
            previewScreen.setCountdown("--:--", "Next Prayer", 0.4);
        } catch (Exception e) {
            com.tawkit.infrastructure.Log.warn("Preview seeding failed: " + e.getMessage());
        }
    }

    /** Applies the custom background (or theme) to the mini preview (#15). */
    private void refreshPreview() {
        if (previewUpdating) return;
        previewUpdating = true;
        try {
            String path = customBgField.getText().trim();
            if ("custom".equals(themeCombo.getSelectedItem()) && !path.isEmpty()) {
                previewScreen.applyCustomImage(path);
            } else {
                previewScreen.applyTheme((String) themeCombo.getSelectedItem());
            }
        } finally {
            previewUpdating = false;
        }
    }

    private void chooseBackgroundImage() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Images", "jpg", "jpeg", "png"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            customBgField.setText(chooser.getSelectedFile().getAbsolutePath());
            themeCombo.setSelectedItem("custom");
        }
    }

    private JPanel buildContentTab() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc = grid();

        gbc.gridy = 0; gbc.gridx = 0; gbc.gridwidth = 2;
        panel.add(slidesCheck, gbc);
        panel.add(weatherCheck, gbc);
        gbc.gridwidth = 1;

        gbc.gridy = 1; gbc.gridx = 0;
        panel.add(new JLabel("Content rotation (seconds):"), gbc);
        gbc.gridx = 1;
        panel.add(rotationSpinner, gbc);

        gbc.gridy = 2; gbc.gridx = 0; gbc.gridwidth = 2;
        panel.add(new JLabel("Announcements (one per line, shown in the ticker):"), gbc);
        gbc.gridwidth = 1;
        gbc.gridy = 3; gbc.gridx = 0; gbc.gridwidth = 2;
        announcementsArea.setLineWrap(true);
        panel.add(new JScrollPane(announcementsArea), gbc);
        return panel;
    }

    private JPanel buildRemindersTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        panel.add(new JLabel("<html><b>Sound alerts</b> — syntax: "
                + "<code>15.BEFORE.FAJR.(message)</code>, <code>10.AFTER.MAGHRIB.(msg)</code>, "
                + "<code>NISF.(msg)</code> midnight, <code>TULT.(msg)</code> last third of night.<br>"
                + "Prayers: FAJR, SHRQ, DOHR, ASSR, MGRB, ISHA</html>"), BorderLayout.NORTH);

        JList<String> list = new JList<>(reminderModel);
        panel.add(new JScrollPane(list), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JTextField newRule = new JTextField(24);
        JButton add = new JButton("Add");
        add.addActionListener(e -> {
            String rule = newRule.getText().trim();
            if (!rule.isBlank()) {
                reminderModel.addElement(rule);
                newRule.setText("");
            }
        });
        JButton remove = new JButton("Remove selected");
        remove.addActionListener(e -> {
            int idx = list.getSelectedIndex();
            if (idx >= 0) reminderModel.remove(idx);
        });
        buttons.add(newRule);
        buttons.add(add);
        buttons.add(remove);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildBackupTab() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc = grid();

        gbc.gridy = 0; gbc.gridx = 0; gbc.gridwidth = 2;
        panel.add(new JLabel("Copy your settings to another screen:"), gbc);
        gbc.gridwidth = 1;

        gbc.gridy = 1; gbc.gridx = 0;
        JButton exportBtn = new JButton("⬆ Export settings…");
        exportBtn.addActionListener(e -> exportSettings());
        panel.add(exportBtn, gbc);

        gbc.gridy = 2; gbc.gridx = 0;
        JButton importBtn = new JButton("⬇ Import settings…");
        importBtn.addActionListener(e -> importSettings());
        panel.add(importBtn, gbc);

        gbc.gridy = 3; gbc.gridx = 0; gbc.gridwidth = 2;
        panel.add(new JLabel("<html><i>The file is saved / loaded as "
                + "settings.properties (UTF-8).</i></html>"), gbc);
        return panel;
    }

    private void exportSettings() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("tawkit-settings.properties"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            AppSettings temp = new AppSettings();
            applyToSettings(temp);
            new SettingsService().exportTo(Path.of(chooser.getSelectedFile().getAbsolutePath()), temp);
            JOptionPane.showMessageDialog(this, "Settings exported.", "Export",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage(),
                    "Export", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void importSettings() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Settings (*.properties)", "properties"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            AppSettings imported = new SettingsService()
                    .importFrom(Path.of(chooser.getSelectedFile().getAbsolutePath()));
            loadFromSettings(imported);
            JOptionPane.showMessageDialog(this, "Settings loaded. Press Save to apply.",
                    "Import", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Import failed: " + ex.getMessage(),
                    "Import", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static GridBagConstraints grid() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        return gbc;
    }

    // ---------------------------------------------------------- load/apply

    public void loadFromSettings(AppSettings settings) {
        if (settings == null) return;

        mosqueField.setText(settings.getMosqueName());
        var loc = settings.getLocation();
        if (loc != null) {
            if (loc.getCountry() != null) countryCombo.setSelectedItem(loc.getCountry());
            refreshCityCombo();
            if (loc.getName() != null) cityCombo.setSelectedItem(loc.getName());
            latField.setText(String.valueOf(loc.getLatitude()));
            lonField.setText(String.valueOf(loc.getLongitude()));
            timezoneField.setText(String.valueOf(loc.getTimezone()));
        }
        methodCombo.setSelectedItem(settings.getCalculationMethod());
        asrCombo.setSelectedItem(settings.getAsrMethod());

        for (PrayerType type : PrayerType.values()) {
            adjustSpinners.get(type).setValue(settings.getAdjust(type));
        }

        IqamaSettings iqama = settings.getIqamaSettings();
        for (PrayerType type : IQAMA_PRAYERS) {
            boolean fixed = iqama.getMode(type) == IqamaSettings.IqamaMode.FIXED
                    && iqama.getFixedTime(type) != null;
            fixedChecks.get(type).setSelected(fixed);
            fixedFields.get(type).setEnabled(fixed);
            fixedFields.get(type).setText(fixed ? iqama.getFixedTime(type).toString() : "");
            if (fixed) {
                iqamaOffsets.get(type).setValue(iqama.getOffsetMinutes(type));
            } else {
                iqamaOffsets.get(type).setValue(iqama.getOffsetMinutes(type));
            }
        }

        adhanCheck.setSelected(settings.isAdhanEnabled());
        volumeSlider.setValue((int) Math.round(settings.getAudioVolume() * 100));
        preFajrAlertCheck.setSelected(settings.isPreFajrAlertEnabled());
        preFajrMinutes.setValue(settings.getPreFajrAlertMinutes());
        for (PrayerType type : IQAMA_PRAYERS) {
            adhanFileFields.get(type).setText(settings.getAdhanSoundFile(type)); // #6
        }

        jumuaCheck.setSelected(settings.isJumuaEnabled()); // #5
        jumuaKhutbaField.setText(settings.getJumuaKhutbaTime() != null
                ? settings.getJumuaKhutbaTime().toString() : "12:45");

        languageCombo.setSelectedItem(settings.getLanguage());
        clock24Check.setSelected(settings.isUse24HourClock());
        secondsCheck.setSelected(settings.isShowSeconds());
        dimCheck.setSelected(settings.isScreenDimDuringPrayer());
        dimMinutes.setValue(settings.getPrayerDimMinutes());
        hijriOffset.setValue(settings.getHijriOffsetDays());
        themeCombo.setSelectedItem(settings.getBackgroundTheme());
        customBgField.setText(settings.getCustomBackgroundPath());
        unitCombo.setSelectedItem(settings.getTemperatureUnit() == 'F' ? "°F" : "°C");

        slidesCheck.setSelected(settings.isSlidesEnabled());
        weatherCheck.setSelected(settings.isWeatherEnabled());
        rotationSpinner.setValue(settings.getContentRotationSeconds());
        announcementsArea.setText(String.join("\n", settings.getAnnouncements()));

        reminderModel.clear();
        for (String rule : settings.getReminders()) {
            reminderModel.addElement(rule);
        }
    }

    public void applyToSettings(AppSettings settings) {
        if (settings == null) return;

        settings.setMosqueName(mosqueField.getText().trim());
        try {
            double lat = latField.getText().isBlank() ? 0 : Double.parseDouble(latField.getText().trim());
            double lon = lonField.getText().isBlank() ? 0 : Double.parseDouble(lonField.getText().trim());
            double tz = timezoneField.getText().isBlank() ? 0 : Double.parseDouble(timezoneField.getText().trim());
            settings.setLocation(new com.tawkit.model.prayer.Location(
                    (String) cityCombo.getSelectedItem(),
                    (String) countryCombo.getSelectedItem(),
                    lat, lon, tz));
        } catch (NumberFormatException ignored) {
        }

        settings.setCalculationMethod((CalculationMethod) methodCombo.getSelectedItem());
        settings.setAsrMethod((AsrMethod) asrCombo.getSelectedItem());
        for (PrayerType type : PrayerType.values()) {
            settings.setAdjust(type, (Integer) adjustSpinners.get(type).getValue());
        }

        IqamaSettings iqama = settings.getIqamaSettings();
        for (PrayerType type : IQAMA_PRAYERS) {
            int offset = (Integer) iqamaOffsets.get(type).getValue();
            if (fixedChecks.get(type).isSelected()) {
                try {
                    String text = fixedFields.get(type).getText().trim();
                    if (!text.isBlank()) {
                        iqama.setFixed(type, LocalTime.parse(text));
                        continue;
                    }
                } catch (DateTimeParseException ignored) {
                }
            }
            iqama.setOffset(type, offset);
        }

        settings.setAdhanEnabled(adhanCheck.isSelected());
        settings.setAudioVolume(volumeSlider.getValue() / 100.0);
        settings.setPreFajrAlertEnabled(preFajrAlertCheck.isSelected());
        settings.setPreFajrAlertMinutes((Integer) preFajrMinutes.getValue());
        for (PrayerType type : IQAMA_PRAYERS) {
            settings.setAdhanSoundFile(type, adhanFileFields.get(type).getText().trim()); // #6
        }

        settings.setJumuaEnabled(jumuaCheck.isSelected()); // #5
        try {
            String kt = jumuaKhutbaField.getText().trim();
            if (!kt.isBlank()) settings.setJumuaKhutbaTime(LocalTime.parse(kt));
        } catch (DateTimeParseException ignored) {
        }

        settings.setLanguage((String) languageCombo.getSelectedItem());
        settings.setUse24HourClock(clock24Check.isSelected());
        settings.setShowSeconds(secondsCheck.isSelected());
        settings.setScreenDimDuringPrayer(dimCheck.isSelected());
        settings.setPrayerDimMinutes((Integer) dimMinutes.getValue());
        settings.setHijriOffsetDays((Integer) hijriOffset.getValue());
        settings.setBackgroundTheme((String) themeCombo.getSelectedItem());
        settings.setCustomBackgroundPath(customBgField.getText().trim());
        settings.setTemperatureUnit("°F".equals(unitCombo.getSelectedItem()) ? 'F' : 'C');

        settings.setSlidesEnabled(slidesCheck.isSelected());
        settings.setWeatherEnabled(weatherCheck.isSelected());
        settings.setContentRotationSeconds((Integer) rotationSpinner.getValue());

        List<String> announcements = new ArrayList<>();
        for (String line : announcementsArea.getText().split("\\n")) {
            if (!line.isBlank()) announcements.add(line.trim());
        }
        settings.setAnnouncements(announcements);

        List<String> reminders = new ArrayList<>();
        for (int i = 0; i < reminderModel.size(); i++) {
            reminders.add(reminderModel.get(i));
        }
        settings.setReminders(reminders);
    }

    public boolean isSaved() {
        return saved;
    }
}
