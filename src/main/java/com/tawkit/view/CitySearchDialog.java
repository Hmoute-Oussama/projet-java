package com.tawkit.view;

import com.tawkit.service.location.GeocodingService;
import com.tawkit.service.location.GeocodingService.GeoPlace;
import com.tawkit.view.render.TextDraw;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FlowLayout;
import java.util.List;
import java.util.function.Consumer;

/**
 * Compact city search dialog (task §7): type a city, live results from the
 * Open-Meteo geocoding API, pick one → the widget's temperature, icon,
 * coordinates and flag all update. Themed like the dashboard.
 *
 * Results appear while typing (400 ms debounce) and searches run on a
 * background thread so the UI never blocks.
 */
public final class CitySearchDialog extends JDialog {

    private static final Color BG = com.tawkit.view.TawkitTheme.CHARCOAL;
    private static final Color FG = com.tawkit.view.TawkitTheme.WARM_WHITE;
    private static final Color ACCENT = com.tawkit.view.TawkitTheme.GOLDEN_BEIGE;
    private static final Color MUTED = TextDraw.withAlpha(com.tawkit.view.TawkitTheme.GRAY, 0.9f);

    private final GeocodingService geocoding = new GeocodingService();
    private final JTextField queryField = new JTextField(20);
    private final DefaultListModel<GeoPlace> model = new DefaultListModel<>();
    private final JList<GeoPlace> resultList = new JList<>(model);
    private final javax.swing.JLabel statusLabel = new javax.swing.JLabel("Type a city name…");
    private final javax.swing.Timer debounce;
    private int searchSeq = 0; // ignore stale search responses

    /** Called with the selected place, on the EDT. */
    public CitySearchDialog(JFrame parent, Consumer<GeoPlace> onPick) {
        super(parent, "Search city", true);
        setIconImage(Icons.appIcon());
        getContentPane().setBackground(BG);
        setLayout(new BorderLayout(8, 8));

        // ---- top: search field ----
        JPanel top = new JPanel(new BorderLayout(6, 6));
        top.setOpaque(false);
        top.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 10, 4, 10));
        queryField.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
        queryField.setBackground(new Color(0x1C, 0x1C, 0x1C));
        queryField.setForeground(FG);
        queryField.setCaretColor(ACCENT);
        top.add(queryField, BorderLayout.CENTER);
        statusLabel.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 12));
        statusLabel.setForeground(MUTED);
        top.add(statusLabel, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);

        // ---- middle: results ----
        resultList.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        resultList.setBackground(new Color(0x16, 0x16, 0x16));
        resultList.setForeground(FG);
        resultList.setSelectionBackground(new Color(0x2A, 0x24, 0x16));
        resultList.setSelectionForeground(ACCENT);
        resultList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultList.setCellRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list,
                    Object value, int index, boolean selected, boolean focused) {
                java.awt.Component c = super.getListCellRendererComponent(list, value, index, selected, focused);
                if (c instanceof javax.swing.JLabel label && value instanceof GeoPlace place) {
                    label.setText(place.displayLabel());
                }
                return c;
            }
        });
        JScrollPane scroll = new JScrollPane(resultList);
        scroll.getViewport().setBackground(new Color(0x16, 0x16, 0x16));
        scroll.setPreferredSize(new Dimension(380, 240));
        add(scroll, BorderLayout.CENTER);

        // ---- bottom: buttons ----
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.setOpaque(false);
        JButton useButton = new JButton("Use this city");
        JButton cancelButton = new JButton("Cancel");
        useButton.addActionListener(e -> pickSelected(onPick));
        cancelButton.addActionListener(e -> dispose());
        bottom.add(useButton);
        bottom.add(cancelButton);
        add(bottom, BorderLayout.SOUTH);

        // ---- behaviors ----
        debounce = new javax.swing.Timer(400, e -> runSearch());
        debounce.setRepeats(false);
        queryField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { debounce.restart(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { debounce.restart(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { debounce.restart(); }
        });
        queryField.addActionListener(e -> {
            if (resultList.getSelectedValue() != null) pickSelected(onPick);
            else runSearch();
        });
        resultList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) pickSelected(onPick);
            }
        });

        // Escape closes the dialog
        getRootPane().registerKeyboardAction(e -> dispose(),
                javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
                javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);

        pack();
        setLocationRelativeTo(parent);
        queryField.requestFocusInWindow();
    }

    private void runSearch() {
        final String query = queryField.getText().trim();
        final int seq = ++searchSeq;
        if (query.length() < 2) {
            model.clear();
            statusLabel.setText(query.isEmpty() ? "Type a city name…" : "Keep typing…");
            return;
        }
        statusLabel.setText("Searching \"" + query + "\"…");
        geocoding.searchAsync(query,
                results -> SwingUtilities.invokeLater(() -> {
                    if (seq != searchSeq) return; // stale response
                    model.clear();
                    for (GeoPlace place : results) model.addElement(place);
                    statusLabel.setText(results.isEmpty()
                            ? "No city found for \"" + query + "\""
                            : results.size() + " result(s) — double-click to select");
                    if (!results.isEmpty()) resultList.setSelectedIndex(0);
                }),
                error -> SwingUtilities.invokeLater(() -> {
                    if (seq != searchSeq) return;
                    model.clear();
                    statusLabel.setText("Search failed: " + error + " — check your connection");
                }));
    }

    private void pickSelected(Consumer<GeoPlace> onPick) {
        GeoPlace place = resultList.getSelectedValue();
        if (place == null) return;
        onPick.accept(place);
        dispose();
    }
}
