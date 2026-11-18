package com.tawkit.view;

import com.tawkit.model.prayer.AsrMethod;
import com.tawkit.model.prayer.CalculationMethod;
import com.tawkit.model.prayer.IqamaSettings;
import com.tawkit.model.prayer.Location;
import com.tawkit.model.prayer.PrayerType;
import com.tawkit.model.settings.AppSettings;
import com.tawkit.service.city.CityService;
import com.tawkit.service.city.CountryCodes;
import com.tawkit.service.location.PreciseLocationService;
import com.tawkit.service.settings.SettingsService;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicTextFieldUI;
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

    private static final Color SETTINGS_BG = new Color(0x0E, 0x0F, 0x10);
    private static final Color SETTINGS_SURFACE = new Color(0x18, 0x19, 0x1A);
    private static final Color SETTINGS_FIELD = new Color(0x21, 0x22, 0x22);
    private static final Color SETTINGS_ACCENT = TawkitTheme.GOLDEN_BEIGE;
    private static final Color SETTINGS_TEXT = TawkitTheme.WARM_WHITE;
    private static final Color SETTINGS_MUTED = new Color(0xA8, 0xA8, 0xA8);
    private static final Color SETTINGS_LINE = new Color(0x35, 0x35, 0x33);

    private static final PrayerType[] IQAMA_PRAYERS = {
            PrayerType.FAJR, PrayerType.DHUHR, PrayerType.ASR, PrayerType.MAGHRIB, PrayerType.ISHA
    };

    private final CityService cityService = new CityService();
    /** Prevents intermediate JComboBox events while rebuilding the city list. */
    private boolean refreshingCities = false;

    // Location
    private final JTextField mosqueField = new JTextField(20);
    private final JComboBox<String> countryCombo = new JComboBox<>();
    private final JComboBox<String> cityCombo = new JComboBox<>();
    private final JTextField latField = new JTextField(8);
    private final JTextField lonField = new JTextField(8);
    private final JTextField timezoneField = new JTextField(5);
    private final JButton useMyLocationButton = new JButton("⌖ Use my location");
    private final JLabel locationStatus = new JLabel(" ");
    private final PreciseLocationService locationService = new PreciseLocationService();
    /**
     * Exact location returned by "Use my location" — carries the real city
     * name and ISO country code that the city combo cannot represent.
     */
    private Location detectedLocation;
    /** True while the dialog itself rebuilds the country/city combos. */
    private boolean suppressCityEvents = false;

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
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        double scale = UiScale.factor();
        int w = (int) Math.min(680 * scale, screen.width * 0.92);
        int h = (int) Math.min(760 * scale, screen.height * 0.92);
        setSize(w, h);
        setMinimumSize(new Dimension(Math.min(520, screen.width), Math.min(480, screen.height)));
        setResizable(true);
        setLocationRelativeTo(null);
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
        getContentPane().setBackground(SETTINGS_BG);
        setLayout(new BorderLayout(0, 0));

        add(buildSettingsHeader(), BorderLayout.NORTH);

        JPanel workspace = new JPanel(new BorderLayout(10, 0));
        workspace.setOpaque(false);
        workspace.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel nav = new JPanel();
        nav.setOpaque(true);
        nav.setBackground(new Color(0x13, 0x14, 0x16));
        nav.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(SETTINGS_LINE, 1, true),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)));
        nav.setLayout(new BoxLayout(nav, BoxLayout.Y_AXIS));

        CardLayout cards = new CardLayout();
        JPanel content = new JPanel(cards);
        content.setOpaque(true);
        content.setBackground(new Color(0x16, 0x17, 0x19));
        content.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(SETTINGS_LINE, 1, true),
                BorderFactory.createEmptyBorder(4, 4, 4, 4)));

        Map<String, JPanel> sections = new LinkedHashMap<>();
        sections.put("Location", buildLocationTab());
        sections.put("Prayer", buildPrayerTab());
        sections.put("Iqama", buildIqamaTab());
        sections.put("Audio", buildAudioTab());
        sections.put("Display", buildDisplayTab());
        sections.put("Content", buildContentTab());
        sections.put("Reminders", buildRemindersTab());
        sections.put("Backup", buildBackupTab());

        List<JButton> navButtons = new ArrayList<>();
        for (Map.Entry<String, JPanel> entry : sections.entrySet()) {
            JScrollPane scroll = buildScrollableTab(entry.getValue());
            content.add(scroll, entry.getKey());

            JButton navButton = new JButton(entry.getKey());
            navButton.putClientProperty("tawkitStyled", Boolean.TRUE);
            navButton.setAlignmentX(Component.LEFT_ALIGNMENT);
            navButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
            navButton.setHorizontalAlignment(SwingConstants.LEFT);
            navButton.setOpaque(true);
            navButton.setContentAreaFilled(true);
            navButton.setBorderPainted(false);
            navButton.setFocusPainted(false);
            navButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            navButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
            navButton.setForeground(SETTINGS_MUTED);
            navButton.setBackground(new Color(0x13, 0x14, 0x16));
            navButton.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
            navButton.addActionListener(e -> {
                cards.show(content, entry.getKey());
                for (JButton button : navButtons) {
                    boolean selected = button == navButton;
                    button.setForeground(selected ? TawkitTheme.BLACK : SETTINGS_MUTED);
                    button.setBackground(selected ? SETTINGS_ACCENT : new Color(0x13, 0x14, 0x16));
                }
            });
            navButtons.add(navButton);
            nav.add(navButton);
            if (navButtons.size() < sections.size()) {
                nav.add(Box.createVerticalStrut(4));
            }
        }

        if (!navButtons.isEmpty()) {
            JButton first = navButtons.get(0);
            first.setForeground(TawkitTheme.BLACK);
            first.setBackground(SETTINGS_ACCENT);
        }
        nav.setPreferredSize(new Dimension(155, 0));
        workspace.add(nav, BorderLayout.WEST);
        workspace.add(content, BorderLayout.CENTER);
        add(workspace, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new BorderLayout());
        buttonPanel.setBackground(SETTINGS_SURFACE);
        buttonPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, SETTINGS_LINE),
                BorderFactory.createEmptyBorder(11, 16, 11, 16)));

        JLabel status = new JLabel("Save to keep  •  Apply to update now");
        status.setForeground(SETTINGS_MUTED);
        status.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        buttonPanel.add(status, BorderLayout.WEST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        JButton cancelButton = styleButton(new JButton("Cancel"), false);
        JButton applyButton = styleButton(new JButton("Apply"), false);
        applyButton.setToolTipText("Apply changes to the app without closing");
        applyButton.addActionListener(e -> applyNow());
        JButton saveButton = styleButton(new JButton("Save"), true);
        saveButton.addActionListener(e -> {
            saved = true;
            applyNow();
            dispose();
        });
        cancelButton.addActionListener(e -> dispose());
        actions.add(cancelButton);
        actions.add(applyButton);
        actions.add(saveButton);
        buttonPanel.add(actions, BorderLayout.EAST);
        add(buttonPanel, BorderLayout.SOUTH);

        SwingUtilities.invokeLater(this::styleSettingsComponents);
    }

    private JPanel buildSettingsHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(SETTINGS_SURFACE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, SETTINGS_LINE),
                BorderFactory.createEmptyBorder(14, 18, 14, 18)));

        JLabel mark = new JLabel("TAWKIT");
        mark.setForeground(SETTINGS_ACCENT);
        mark.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        mark.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(SETTINGS_ACCENT.getRed(),
                        SETTINGS_ACCENT.getGreen(), SETTINGS_ACCENT.getBlue(), 120), 1, true),
                BorderFactory.createEmptyBorder(5, 8, 5, 8)));
        header.add(mark, BorderLayout.WEST);

        JPanel title = new JPanel();
        title.setOpaque(false);
        title.setLayout(new BoxLayout(title, BoxLayout.Y_AXIS));
        JLabel heading = new JLabel("Settings");
        heading.setForeground(SETTINGS_TEXT);
        heading.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel subtitle = new JLabel("Configure Tawkit for this screen");
        subtitle.setForeground(SETTINGS_MUTED);
        subtitle.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        title.add(heading);
        title.add(Box.createVerticalStrut(2));
        title.add(subtitle);
        header.add(title, BorderLayout.CENTER);
        return header;
    }

    private JScrollPane buildScrollableTab(JPanel content) {
        styleTabPanel(content);
        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(SETTINGS_BG);
        scroll.setBackground(SETTINGS_BG);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        scroll.getVerticalScrollBar().setBlockIncrement(120);
        return scroll;
    }

    private void styleTabPanel(JPanel panel) {
        panel.setOpaque(true);
        panel.setBackground(SETTINGS_BG);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(8, 8, 8, 8),
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(0x27, 0x29, 0x2C), 1, true),
                        BorderFactory.createEmptyBorder(16, 18, 16, 18))));
    }

    private JButton styleButton(JButton button, boolean primary) {
        button.putClientProperty("tawkitStyled", Boolean.TRUE);
        button.setUI(new BasicButtonUI());
        button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(true);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(primary ? SETTINGS_ACCENT : SETTINGS_LINE, 1, true),
                BorderFactory.createEmptyBorder(7, 14, 7, 14)));
        button.setOpaque(true);
        button.setBackground(primary ? SETTINGS_ACCENT : SETTINGS_FIELD);
        button.setForeground(primary ? TawkitTheme.BLACK : SETTINGS_TEXT);
        return button;
    }

    private void styleSettingsComponents() {
        styleSettingsComponent(this);
        revalidate();
        repaint();
    }

    private void styleSettingsComponent(Component component) {
        if (component instanceof JButton button) {
            if (button.getClientProperty("tawkitStyled") != Boolean.TRUE) {
                button.setUI(new BasicButtonUI());
                button.setOpaque(true);
                button.setForeground(SETTINGS_TEXT);
                button.setBackground(SETTINGS_FIELD);
                button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
                button.setFocusPainted(false);
                button.setBorderPainted(false);
                button.setContentAreaFilled(true);
                button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                button.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(SETTINGS_LINE, 1, true),
                        BorderFactory.createEmptyBorder(6, 10, 6, 10)));
            }
        } else if (component instanceof JTextField field) {
            field.setUI(new BasicTextFieldUI());
            field.setOpaque(true);
            field.setBackground(SETTINGS_FIELD);
            field.setForeground(SETTINGS_TEXT);
            field.setCaretColor(SETTINGS_ACCENT);
            field.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(SETTINGS_LINE, 1, true),
                    BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        } else if (component instanceof JTextArea area) {
            area.setOpaque(true);
            area.setBackground(SETTINGS_FIELD);
            area.setForeground(SETTINGS_TEXT);
            area.setCaretColor(SETTINGS_ACCENT);
            area.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        } else if (component instanceof JComboBox<?> combo) {
            combo.setUI(new BasicComboBoxUI());
            combo.setOpaque(true);
            combo.setBackground(SETTINGS_FIELD);
            combo.setForeground(SETTINGS_TEXT);
            combo.setBorder(BorderFactory.createLineBorder(SETTINGS_LINE, 1, true));
        } else if (component instanceof JSpinner spinner) {
            spinner.setOpaque(true);
            spinner.setBackground(SETTINGS_FIELD);
            spinner.setBorder(BorderFactory.createLineBorder(SETTINGS_LINE, 1, true));
            JComponent editor = spinner.getEditor();
            editor.setOpaque(true);
            editor.setBackground(SETTINGS_FIELD);
            if (editor instanceof JSpinner.DefaultEditor spinnerEditor) {
                spinnerEditor.getTextField().setForeground(SETTINGS_TEXT);
                spinnerEditor.getTextField().setCaretColor(SETTINGS_ACCENT);
                spinnerEditor.getTextField().setOpaque(true);
                spinnerEditor.getTextField().setBackground(SETTINGS_FIELD);
            }
        } else if (component instanceof JCheckBox check) {
            check.setOpaque(false);
            check.setForeground(SETTINGS_TEXT);
        } else if (component instanceof JSlider slider) {
            slider.setOpaque(false);
            slider.setForeground(SETTINGS_ACCENT);
        } else if (component instanceof JLabel label) {
            label.setForeground(SETTINGS_TEXT);
            label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        } else if (component instanceof JList<?> list) {
            list.setBackground(SETTINGS_FIELD);
            list.setForeground(SETTINGS_TEXT);
            list.setSelectionBackground(new Color(0x44, 0x3A, 0x28));
            list.setSelectionForeground(SETTINGS_ACCENT);
        } else if (component instanceof JScrollPane scroll) {
            scroll.setBackground(SETTINGS_BG);
            scroll.getViewport().setBackground(SETTINGS_BG);
        } else if (component instanceof JPanel panel) {
            Color bg = panel.getBackground();
            boolean preserve =
                    bg.equals(SETTINGS_SURFACE)
                    || bg.equals(SETTINGS_BG)
                    || bg.equals(new Color(0x13, 0x14, 0x16))
                    || bg.equals(new Color(0x16, 0x17, 0x19));
            if (!preserve) {
                panel.setOpaque(false);
            }
        }

        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                styleSettingsComponent(child);
            }
        }
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
        // Register listeners before loading values so the first country is
        // initialized through the same path as every later country change.
        countryCombo.addActionListener(e -> {
            if (suppressCityEvents) return;
            detectedLocation = null; // user picked another country
            refreshCityCombo();
        });
        cityCombo.addActionListener(e -> {
            if (refreshingCities || suppressCityEvents) return;
            detectedLocation = null; // user picked another city
            updateLocationFromCity();
        });

        countryCombo.removeAllItems();
        for (String country : cityService.getCountries()) {
            countryCombo.addItem(country);
        }
        refreshCityCombo();
    }

    /** Rebuilds the city list and selects the first city of the new country. */
    private void refreshCityCombo() {
        String country = (String) countryCombo.getSelectedItem();
        refreshingCities = true;
        try {
            cityCombo.removeAllItems();
            if (country != null) {
                for (String city : cityService.getCitiesOf(country)) {
                    cityCombo.addItem(city);
                }
            }
        } finally {
            refreshingCities = false;
        }

        // JComboBox selects the first inserted city. Always refresh the
        // coordinates explicitly so values from the previous country cannot
        // remain in the fields.
        updateLocationFromCity();
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
        JPanel cityActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        cityActions.add(useMyLocationButton);
        JButton applyCity = new JButton("Apply selected city");
        applyCity.addActionListener(e -> applySelectedCity());
        cityActions.add(applyCity);
        panel.add(cityActions, gbc);

        gbc.gridy = 7; gbc.gridx = 0; gbc.gridwidth = 2;
        locationStatus.setFont(locationStatus.getFont().deriveFont(
                locationStatus.getFont().getSize2D() - 1f));
        locationStatus.setForeground(new Color(0x6B, 0x6B, 0x6B));
        panel.add(locationStatus, gbc);

        gbc.gridy = 8; gbc.gridwidth = 2;
        panel.add(new JLabel("<html><i>“Use my location” takes the exact position reported by "
                + "this computer.<br>For best accuracy, turn on Windows location services "
                + "(Privacy &amp; security → Location).<br>You can also pick a city or type "
                + "coordinates manually.</i></html>"), gbc);

        useMyLocationButton.setToolTipText(
                "Detect this computer's exact position and apply it immediately");
        useMyLocationButton.addActionListener(e -> detectMyLocation());

        // Manual edits invalidate a previously detected fix (its name/country
        // no longer match the coordinates the user typed).
        javax.swing.event.DocumentListener clearDetected =
                new javax.swing.event.DocumentListener() {
                    @Override public void insertUpdate(javax.swing.event.DocumentEvent e) {
                        detectedLocation = null;
                    }

                    @Override public void removeUpdate(javax.swing.event.DocumentEvent e) {
                        detectedLocation = null;
                    }

                    @Override public void changedUpdate(javax.swing.event.DocumentEvent e) {
                        detectedLocation = null;
                    }
                };
        latField.getDocument().addDocumentListener(clearDetected);
        lonField.getDocument().addDocumentListener(clearDetected);
        timezoneField.getDocument().addDocumentListener(clearDetected);

        return panel;
    }

    private void applySelectedCity() {
        String country = (String) countryCombo.getSelectedItem();
        String city = (String) cityCombo.getSelectedItem();
        CityService.City found = (country == null || city == null)
                ? null : cityService.find(country, city);
        if (found != null) {
            // Built-in city: use its reference coordinates.
            latField.setText(Double.toString(found.latitude()));
            lonField.setText(Double.toString(found.longitude()));
            timezoneField.setText(Double.toString(found.timezone()));
            detectedLocation = null;
        }
        // A detected/custom place is not in the database: keep its exact
        // coordinates instead of clearing them.
        applyNow();
        locationStatus.setForeground(new Color(0x3B, 0x6E, 0x3B));
        locationStatus.setText("📍 " + city + ", " + country + " applied.");
    }

    /** Exact position of this computer, applied straight to the live settings. */
    private void detectMyLocation() {
        useMyLocationButton.setEnabled(false);
        locationStatus.setForeground(new Color(0x6B, 0x6B, 0x6B));
        locationStatus.setText("Detecting your location…");
        locationService.locateAsync(new PreciseLocationService.Callback() {
            @Override
            public void onLocation(PreciseLocationService.Fix fix) {
                useMyLocationButton.setEnabled(true);
                applyDetectedLocation(fix);
            }

            @Override
            public void onFailure(String message, boolean permissionBlocked) {
                useMyLocationButton.setEnabled(true);
                locationStatus.setForeground(new Color(0xB0, 0x3A, 0x2E));
                locationStatus.setText(permissionBlocked
                        ? "Windows is blocking location access for desktop apps."
                        : "Could not detect your location: " + message);
                showLocationHelp(message, permissionBlocked);
            }
        });
    }

    private void applyDetectedLocation(PreciseLocationService.Fix fix) {
        Location loc = fix.location();

        // Show a non-built-in place (e.g. "Agdal") in the pickers without
        // letting their listeners overwrite the exact coordinates.
        suppressCityEvents = true;
        try {
            if (loc.getCountry() != null && !loc.getCountry().isBlank()) {
                if (!containsItem(countryCombo, loc.getCountry())) {
                    countryCombo.addItem(loc.getCountry());
                }
                countryCombo.setSelectedItem(loc.getCountry());
                rebuildCityCombo(loc.getCountry(), loc.getName());
            }
        } finally {
            suppressCityEvents = false;
        }

        latField.setText(String.valueOf(loc.getLatitude()));
        lonField.setText(String.valueOf(loc.getLongitude()));
        timezoneField.setText(String.valueOf(loc.getTimezone()));
        // Set after the field updates above (their listeners clear it).
        detectedLocation = loc;

        locationStatus.setForeground(fix.permissionBlocked()
                ? new Color(0xB0, 0x6A, 0x1E) : new Color(0x3B, 0x6E, 0x3B));
        locationStatus.setText("📍 " + loc.getDisplayName() + " — "
                + formatCoordinate(loc.getLatitude()) + ", "
                + formatCoordinate(loc.getLongitude())
                + " (" + fix.accuracyText() + ") — applied.");

        applyNow(); // location is live immediately, no Save needed

        // We got a usable position, but it is the network approximation
        // because the OS is blocking the exact one: say so and offer the fix.
        if (fix.permissionBlocked()) {
            showLocationHelp(null, true);
        }
    }

    /**
     * Explains why no exact position could be obtained, with a shortcut to
     * the Windows privacy page that controls desktop-app location access.
     *
     * @param message          network error message, or {@code null} when an
     *                         approximate position was applied instead
     * @param permissionBlocked OS refused location access
     */
    private void showLocationHelp(String message, boolean permissionBlocked) {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String reason = permissionBlocked
                ? "Windows is blocking location access.\n\n"
                : "Could not detect your location automatically:\n" + message + "\n\n";
        String text = permissionBlocked
                ? (message == null
                        ? "Only an approximate position could be used because "
                                + "Windows location access is off.\n\n"
                        : "")
                        + "For the exact position, turn on "
                        + "Settings → Privacy & security → Location:\n"
                        + "  • Location services\n"
                        + "  • Let desktop apps access your location\n\n"
                        + "Then press “Use my location” again."
                : reason + "Pick a city below or type the coordinates manually.";
        String[] options = (windows && permissionBlocked)
                ? new String[]{"Open Windows settings", "Close"}
                : new String[]{"OK"};
        int choice = JOptionPane.showOptionDialog(this, text,
                permissionBlocked ? "Exact location needs Windows permission"
                        : "Location unavailable",
                JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
                null, options, options[0]);
        if (windows && permissionBlocked && choice == 0) {
            try {
                new ProcessBuilder("cmd", "/c", "start", "",
                        "ms-settings:privacy-location").start();
            } catch (java.io.IOException e) {
                com.tawkit.infrastructure.Log.warn(
                        "Cannot open Windows location settings (" + e.getMessage() + ")");
            }
        }
    }

    private static String formatCoordinate(double value) {
        return String.format(java.util.Locale.ROOT, "%.5f", value);
    }

    private static boolean containsItem(JComboBox<String> combo, String value) {
        for (int i = 0; i < combo.getItemCount(); i++) {
            if (value.equalsIgnoreCase(combo.getItemAt(i))) return true;
        }
        return false;
    }

    /** Rebuilds the city list and selects {@code city}, even if unknown. */
    private void rebuildCityCombo(String country, String city) {
        cityCombo.removeAllItems();
        for (String name : cityService.getCitiesOf(country)) {
            cityCombo.addItem(name);
        }
        if (city != null && !city.isBlank() && !containsItem(cityCombo, city)) {
            cityCombo.addItem(city);
        }
        if (city != null && !city.isBlank()) {
            cityCombo.setSelectedItem(city);
        }
    }

    private void updateLocationFromCity() {
        String country = (String) countryCombo.getSelectedItem();
        String city = (String) cityCombo.getSelectedItem();
        CityService.City found = (country == null || city == null)
                ? null : cityService.find(country, city);

        if (found == null) {
            // Do not keep coordinates/timezone belonging to the old country.
            latField.setText("");
            lonField.setText("");
            timezoneField.setText("");
            return;
        }

        latField.setText(Double.toString(found.latitude()));
        lonField.setText(Double.toString(found.longitude()));
        timezoneField.setText(Double.toString(found.timezone()));
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
            // Keep exact/custom places (e.g. detected by "Use my location")
            // selectable even when they are not in the built-in city list.
            suppressCityEvents = true;
            try {
                if (loc.getCountry() != null && !loc.getCountry().isBlank()) {
                    if (!containsItem(countryCombo, loc.getCountry())) {
                        countryCombo.addItem(loc.getCountry());
                    }
                    countryCombo.setSelectedItem(loc.getCountry());
                    rebuildCityCombo(loc.getCountry(), loc.getName());
                }
            } finally {
                suppressCityEvents = false;
            }
            latField.setText(String.valueOf(loc.getLatitude()));
            lonField.setText(String.valueOf(loc.getLongitude()));
            timezoneField.setText(String.valueOf(loc.getTimezone()));
            detectedLocation = loc;
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
            String city = (String) cityCombo.getSelectedItem();
            String country = (String) countryCombo.getSelectedItem();

            // A detected fix keeps its real place name and ISO country code
            // (needed for the flag) as long as the user did not move the
            // coordinates manually.
            Location detected = detectedLocation;
            boolean stillDetected = detected != null
                    && Math.abs(detected.getLatitude() - lat) < 1e-6
                    && Math.abs(detected.getLongitude() - lon) < 1e-6;

            Location location;
            if (stillDetected) {
                location = new Location(
                        city == null || city.isBlank() ? detected.getName() : city,
                        country == null || country.isBlank() ? detected.getCountry() : country,
                        lat, lon, tz);
                location.setCountryCode(detected.getCountryCode());
            } else {
                location = new Location(city, country, lat, lon, tz);
                location.setCountryCode(CountryCodes.fromCountryName(country));
            }
            settings.setLocation(location);
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
