package com.tawkit.view;

import com.tawkit.model.prayer.AsrMethod;
import com.tawkit.model.prayer.CalculationMethod;
import com.tawkit.model.prayer.Location;
import com.tawkit.model.prayer.PrayerType;
import com.tawkit.model.settings.AppSettings;
import com.tawkit.service.format.TimeText;
import com.tawkit.service.prayer.HijriCalendarService;
import com.tawkit.service.prayer.PrayerScheduler;
import com.tawkit.service.prayer.PrayerTimeCalculator;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.time.LocalDate;import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Monthly prayer-times table (#1): every day of a month with the five
 * prayers + sunrise, Hijri day column, and a Print button so the mosque can
 * pin the month on the wall. Computed offline with the same engine as the
 * dashboard, including the manual per-prayer adjustments.
 */
public class MonthlyTimesDialog extends JDialog {

    private final AppSettings settings;
    private final PrayerTimeCalculator calculator = new PrayerTimeCalculator();
    private final HijriCalendarService hijri = new HijriCalendarService();

    private final JComboBox<Integer> yearCombo = new JComboBox<>();
    private final JComboBox<String> monthCombo = new JComboBox<>();
    private final javax.swing.table.DefaultTableModel model =
            new javax.swing.table.DefaultTableModel() {
                @Override
                public boolean isCellEditable(int row, int col) {
                    return false;
                }
            };
    private final JTable table = new JTable(model);

    public MonthlyTimesDialog(JFrame parent, AppSettings settings) {
        super(parent, "Monthly prayer times", false);
        this.settings = settings;
        setIconImage(Icons.appIcon());
        buildUI();
        setLocationRelativeTo(parent);
    }

    private void buildUI() {
        setLayout(new BorderLayout(8, 8));

        // ---- top bar: month/year pickers + print ----
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        for (int y = LocalDate.now().getYear() - 2; y <= LocalDate.now().getYear() + 3; y++) {
            yearCombo.addItem(y);
        }
        yearCombo.setSelectedItem(LocalDate.now().getYear());
        String[] months = {"January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"};
        for (String m : months) monthCombo.addItem(m);
        monthCombo.setSelectedIndex(LocalDate.now().getMonthValue() - 1);
        top.add(new javax.swing.JLabel("Month:"));
        top.add(monthCombo);
        top.add(new javax.swing.JLabel("Year:"));
        top.add(yearCombo);

        JButton print = new JButton("🖨 Print");
        print.addActionListener(e -> printTable());
        top.add(print);

        JButton today = new JButton("This month");
        today.addActionListener(e -> {
            yearCombo.setSelectedItem(LocalDate.now().getYear());
            monthCombo.setSelectedIndex(LocalDate.now().getMonthValue() - 1);
        });
        top.add(today);

        java.awt.event.ActionListener change = e -> rebuild();
        monthCombo.addActionListener(change);
        yearCombo.addActionListener(change);
        add(top, BorderLayout.NORTH);

        // ---- table ----
        String[] headers = {"Day", "Hijri", "Weekday",
                "Fajr", "Shuruq", "Dhuhr", "Asr", "Maghrib", "Isha"};
        model.setColumnIdentifiers(headers);
        table.setRowHeight(22);
        table.setFont(new Font("SansSerif", Font.PLAIN, 13));
        table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 13));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value,
                    boolean sel, boolean foc, int row, int col) {
                Component c = super.getTableCellRendererComponent(t, value, sel, foc, row, col);
                setAlignmentOf(c, col);
                if (!sel && row % 2 == 1) {
                    c.setBackground(new Color(0xF4, 0xF1, 0xE6));
                }
                return c;
            }

            private void setAlignmentOf(Component c, int col) {
                if (c instanceof javax.swing.JLabel label) {
                    label.setHorizontalAlignment(col <= 2 ? javax.swing.SwingConstants.LEFT
                            : javax.swing.SwingConstants.CENTER);
                }
            }
        });
        add(new JScrollPane(table), BorderLayout.CENTER);

        var loc = settings.getLocation();
        if (loc != null) {
            javax.swing.JLabel info = new javax.swing.JLabel("  " + loc.getDisplayName()
                    + "  —  " + settings.getCalculationMethod().getDisplayName());
            add(info, BorderLayout.SOUTH);
        }

        setSize(760, 640);
        rebuild();
    }

    /** Recomputes the whole visible month (fast: pure math, <1ms per day). */
    private void rebuild() {
        Integer year = (Integer) yearCombo.getSelectedItem();
        int month = monthCombo.getSelectedIndex() + 1;
        if (year == null) return;

        Location loc = settings.getLocation();
        double lat = loc != null ? loc.getLatitude() : 33.5731;
        double lon = loc != null ? loc.getLongitude() : -7.5898;
        double tz = PrayerSchedulerBridge.resolveTimezone(settings);
        CalculationMethod method = settings.getCalculationMethod();
        AsrMethod asr = settings.getAsrMethod();
        boolean use24 = settings.isUse24HourClock();

        LocalDate first = LocalDate.of(year, month, 1);
        int days = first.lengthOfMonth();
        model.setRowCount(0);

        String[] weekdays = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        for (int d = 1; d <= days; d++) {
            LocalDate date = first.plusDays(d - 1);
            Map<PrayerType, LocalTime> times =
                    calculator.compute(date, lat, lon, tz, method, asr);
            applyAdjust(times);

            HijriCalendarService hsvc = new HijriCalendarService();
            hsvc.setOffsetDays(settings.getHijriOffsetDays());
            var h = hsvc.toHijri(date);

            // Jumu'a (#5): Friday's Dhuhr cell shows the khutba time
            String dhuhrCell;
            if (settings.isJumuaEnabled()
                    && date.getDayOfWeek() == java.time.DayOfWeek.FRIDAY
                    && settings.getJumuaKhutbaTime() != null) {
                dhuhrCell = fmt(settings.getJumuaKhutbaTime(), use24) + "  Jumu'a";
            } else {
                dhuhrCell = fmt(times.get(PrayerType.DHUHR), use24);
            }

            model.addRow(new Object[]{
                    date,
                    h.getDay() + " " + h.getMonthName().substring(0, Math.min(3, h.getMonthName().length())),
                    weekdays[date.getDayOfWeek().getValue() - 1],
                    fmt(times.get(PrayerType.FAJR), use24),
                    fmt(times.get(PrayerType.SHURUQ), use24),
                    dhuhrCell,
                    fmt(times.get(PrayerType.ASR), use24),
                    fmt(times.get(PrayerType.MAGHRIB), use24),
                    fmt(times.get(PrayerType.ISHA), use24)
            });
        }
        highlightToday();
    }

    /** Applies the same ±minutes adjustments as the daily scheduler. */
    private void applyAdjust(Map<PrayerType, LocalTime> times) {
        for (PrayerType type : PrayerType.values()) {
            LocalTime t = times.get(type);
            int adjust = settings.getAdjust(type);
            if (t != null && adjust != 0) {
                times.put(type, t.plusMinutes(adjust));
            }
        }
    }

    private void highlightToday() {
        LocalDate today = LocalDate.now();
        for (int i = 0; i < model.getRowCount(); i++) {
            if (today.equals(model.getValueAt(i, 0))) {
                table.setRowSelectionInterval(i, i);
                table.scrollRectToVisible(table.getCellRect(i, 0, true));
                break;
            }
        }
    }

    private static String fmt(LocalTime t, boolean use24) {
        if (t == null) return "--:--";
        return use24
                ? t.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                : TimeText.prayerTime(t, false);
    }

    /** Uses the built-in JTable print API — headers and pagination included. */
    private void printTable() {
        try {
            Integer year = (Integer) yearCombo.getSelectedItem();
            table.print(JTable.PrintMode.FIT_WIDTH,
                    new java.text.MessageFormat("Tawkit — Prayer times "
                            + monthCombo.getSelectedItem() + " " + year),
                    new java.text.MessageFormat("Page {0}"));
        } catch (Exception e) {
            com.tawkit.infrastructure.Log.warn("Monthly print failed (" + e.getMessage() + ")");
            javax.swing.JOptionPane.showMessageDialog(this,
                    "Printing failed: " + e.getMessage(), "Print",
                    javax.swing.JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Bridging helper so the dialog can reuse the scheduler's timezone logic. */
    private static final class PrayerSchedulerBridge {
        static double resolveTimezone(AppSettings s) {
            return PrayerScheduler.resolveTimezone(s.getLocation());
        }
    }
}
