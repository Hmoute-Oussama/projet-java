package com.tawkit.view.render;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Map;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Draws country flags dynamically from ISO 3166-1 alpha-2 codes — no
 * hardcoded flag in the widget. Real stripe/emblem patterns for every
 * country of the built-in city database plus common extras; an unknown
 * code falls back to a neutral badge showing the code itself.
 *
 * <p>Flags are simplified (stripes + hoist triangles/bands + emblem discs +
 * crosses) but instantly recognizable and always consistent with the
 * selected location.
 */
public final class FlagPainter {

    /**
     * Flag spec: stripes (horizontal or vertical, with optional weights) +
     * optional hoist triangle / hoist band / canton / center disc / nordic cross.
     */
    private record Spec(String[] colors, double[] weights, boolean vertical,
                        String hoistTriangle, String hoistBand, String canton,
                        String disc, String cross) {
        static Spec h(String... colors) { return new Spec(colors, null, false, null, null, null, null, null); }
        static Spec v(String... colors) { return new Spec(colors, null, true, null, null, null, null, null); }
        static Spec hw(double[] weights, String... colors) { return new Spec(colors, weights, false, null, null, null, null, null); }
        static Spec tri(String hoist, String... colors) { return new Spec(colors, null, false, hoist, null, null, null, null); }
        static Spec band(String band, String... colors) { return new Spec(colors, null, false, null, band, null, null, null); }
        static Spec disc(String discColor, String field) { return new Spec(new String[]{field}, null, false, null, null, null, discColor, null); }
        static Spec cross(String field, String crossColor) { return new Spec(new String[]{field}, null, false, null, null, null, null, crossColor); }
    }

    private static final Map<String, Spec> FLAGS = Map.ofEntries(
            // ---- Maghreb / Middle East ----
            Map.entry("MA", new Spec(new String[]{"#C1272D"}, null, false, null, null, null, "#006233", null)),
            Map.entry("DZ", new Spec(new String[]{"#006233", "#FFFFFF"}, null, true, null, null, null, "#D21034", null)),
            Map.entry("TN", new Spec(new String[]{"#C8102E"}, null, false, null, null, null, "#FFFFFF", null)),
            Map.entry("LY", Spec.hw(new double[]{1, 2, 1}, "#E70013", "#000000", "#239E46")),
            Map.entry("EG", new Spec(new String[]{"#CE1126", "#FFFFFF", "#000000"}, null, false, null, null, null, "#C09300", null)),
            Map.entry("SA", Spec.h("#006C35")),
            Map.entry("JO", new Spec(new String[]{"#000000", "#FFFFFF", "#007A3D"}, null, false, "#CE1126", null, null, "#FFFFFF", null)),
            Map.entry("PS", Spec.tri("#CE1126", "#000000", "#FFFFFF", "#009736")),
            Map.entry("AE", Spec.band("#CE1126", "#00732F", "#FFFFFF", "#000000")),
            Map.entry("KW", Spec.band("#000000", "#007A3D", "#FFFFFF", "#CE1126")),
            Map.entry("QA", new Spec(new String[]{"#FFFFFF", "#8A1538"}, new double[]{1, 3}, true, null, null, null, null, null)),
            Map.entry("BH", new Spec(new String[]{"#FFFFFF", "#CE1126"}, new double[]{1, 3}, true, null, null, null, null, null)),
            Map.entry("OM", Spec.band("#CE1126", "#FFFFFF", "#C8102E", "#008000")),
            Map.entry("YE", Spec.h("#CE1126", "#FFFFFF", "#000000")),
            Map.entry("IQ", new Spec(new String[]{"#CE1126", "#FFFFFF", "#000000"}, null, false, null, null, null, "#C09300", null)),
            Map.entry("SY", Spec.h("#CE1126", "#FFFFFF", "#000000")),
            Map.entry("LB", new Spec(new String[]{"#EE161F", "#FFFFFF", "#EE161F"}, new double[]{1, 2, 1}, false, null, null, null, "#14A44D", null)),
            Map.entry("IL", Spec.cross("#FFFFFF", "#0038B8")),
            // ---- Africa ----
            Map.entry("SD", Spec.tri("#007229", "#D21034", "#FFFFFF", "#000000")),
            Map.entry("ET", new Spec(new String[]{"#078930", "#FCDD09", "#DA121A"}, null, false, null, null, null, "#0F47AF", null)),
            Map.entry("SO", new Spec(new String[]{"#4189DD"}, null, false, null, null, null, "#FFFFFF", null)),
            Map.entry("DJ", Spec.tri("#FFFFFF", "#6AB2E7", "#0F7A3D")),
            Map.entry("TD", Spec.h("#002664", "#FECB00", "#C60C30")),
            Map.entry("NE", new Spec(new String[]{"#E05206", "#FFFFFF", "#0DB02B"}, null, false, null, null, null, "#E05206", null)),
            Map.entry("ML", Spec.h("#14B53A", "#FCD116", "#CE1126")),
            Map.entry("MR", new Spec(new String[]{"#006233"}, null, false, null, null, null, "#FCD116", null)),
            Map.entry("SN", new Spec(new String[]{"#00853F", "#FDEF42", "#E31B23"}, null, true, null, null, null, "#00853F", null)),
            Map.entry("CM", new Spec(new String[]{"#007A5E", "#CE1126", "#FCD116"}, null, true, null, null, null, "#FCD116", null)),
            Map.entry("GN", Spec.h("#CE1126", "#FCD116", "#009460")),
            Map.entry("GH", new Spec(new String[]{"#CE1126", "#FCD116", "#006B3F"}, null, false, null, null, null, "#000000", null)),
            Map.entry("CI", Spec.v("#F77F00", "#FFFFFF", "#009E60")),
            Map.entry("NG", Spec.v("#008751", "#FFFFFF", "#008751")),
            Map.entry("KE", Spec.h("#000000", "#CE1126", "#006600")),
            Map.entry("TZ", Spec.h("#1EB53A", "#000000", "#00A3DD")),
            Map.entry("UG", Spec.h("#000000", "#F9DD16", "#D90000")),
            Map.entry("ZA", Spec.h("#007A4D", "#FFFFFF", "#DE3831")),
            // ---- Europe ----
            Map.entry("FR", Spec.v("#0055A4", "#FFFFFF", "#EF4135")),
            Map.entry("IT", Spec.v("#009246", "#FFFFFF", "#CE2B37")),
            Map.entry("DE", Spec.h("#000000", "#DD0000", "#FFCE00")),
            Map.entry("BE", Spec.v("#000000", "#FDDA24", "#EF3340")),
            Map.entry("NL", Spec.h("#AE1C28", "#FFFFFF", "#21468B")),
            Map.entry("RU", Spec.h("#FFFFFF", "#0039A6", "#D52B1E")),
            Map.entry("PL", Spec.h("#FFFFFF", "#DC143C")),
            Map.entry("AT", Spec.h("#ED2939", "#FFFFFF", "#ED2939")),
            Map.entry("ES", Spec.hw(new double[]{1, 2, 1}, "#AA151B", "#F1BF00", "#AA151B")),
            Map.entry("PT", new Spec(new String[]{"#046A38", "#DA291C"}, new double[]{2, 3}, true, null, null, null, "#FABD38", null)),
            Map.entry("GR", Spec.cross("#0D5EAF", "#FFFFFF")),
            Map.entry("SE", Spec.cross("#006AA7", "#FECC00")),
            Map.entry("NO", Spec.cross("#BA0C2F", "#FFFFFF")),
            Map.entry("DK", Spec.cross("#C8102E", "#FFFFFF")),
            Map.entry("FI", Spec.cross("#FFFFFF", "#002F6C")),
            Map.entry("CH", Spec.cross("#DA291C", "#FFFFFF")),
            Map.entry("IE", Spec.v("#169B62", "#FFFFFF", "#FF883E")),
            Map.entry("GB", Spec.h("#012169")), // special-cased in paintSpec()
            // ---- Americas ----
            Map.entry("US", Spec.h("#B22234")), // special-cased in paintSpec()
            Map.entry("CA", new Spec(new String[]{"#D80621", "#FFFFFF", "#D80621"}, new double[]{1, 2, 1}, true, null, null, null, "#D80621", null)),
            Map.entry("MX", new Spec(new String[]{"#006847", "#FFFFFF", "#CE1126"}, null, true, null, null, null, "#8C6F3E", null)),
            Map.entry("BR", new Spec(new String[]{"#009B3A"}, null, false, null, null, null, "#FFDF00", null)),
            Map.entry("AR", Spec.h("#74ACDF", "#FFFFFF", "#74ACDF")),
            Map.entry("CL", Spec.h("#FFFFFF", "#D52B1E")),
            Map.entry("CO", Spec.hw(new double[]{2, 1}, "#FCD116", "#003893", "#CE1126")),
            // ---- Asia / Oceania ----
            Map.entry("TR", new Spec(new String[]{"#C8102E"}, null, false, null, null, null, "#FFFFFF", null)),
            Map.entry("IR", Spec.h("#239F40", "#FFFFFF", "#DA0000")),
            Map.entry("PK", Spec.band("#FFFFFF", "#01411C")),
            Map.entry("IN", new Spec(new String[]{"#FF9933", "#FFFFFF", "#138808"}, null, false, null, null, null, "#06038D", null)),
            Map.entry("BD", new Spec(new String[]{"#006A4E"}, null, false, null, null, null, "#F42A41", null)),
            Map.entry("ID", Spec.h("#CE1126", "#FFFFFF")),
            Map.entry("MY", Spec.h("#CC0001")), // special-cased in paintSpec()
            Map.entry("CN", new Spec(new String[]{"#DE2910"}, null, false, null, null, null, "#FFDE00", null)),
            Map.entry("AF", Spec.h("#000000", "#C8102E", "#007A36")),
            Map.entry("KZ", Spec.h("#00AFCA")),
            Map.entry("KG", new Spec(new String[]{"#C8102E"}, null, false, null, null, null, "#F9D616", null)),
            Map.entry("UZ", Spec.h("#0099B5", "#FFFFFF", "#1EB53A")),
            Map.entry("TM", Spec.h("#00843D")),
            Map.entry("AZ", Spec.h("#00B5E2", "#EF3340", "#009543")),
            Map.entry("MV", new Spec(new String[]{"#D21034"}, null, false, null, null, null, "#007E3A", null)),
            Map.entry("SG", Spec.h("#ED2939", "#FFFFFF")),
            Map.entry("BN", Spec.hw(new double[]{4, 1, 1}, "#F7E017", "#FFFFFF", "#000000")),
            Map.entry("TH", Spec.hw(new double[]{1, 1, 2, 1, 1}, "#A51931", "#F4F5F8", "#2D2A4A", "#F4F5F8", "#A51931")),
            Map.entry("LK", new Spec(new String[]{"#8D2029"}, null, false, null, null, null, "#F7B618", null)),
            Map.entry("JP", new Spec(new String[]{"#FFFFFF"}, null, false, null, null, null, "#BC002D", null)),
            Map.entry("KR", Spec.cross("#FFFFFF", "#C60C30")),
            Map.entry("MN", Spec.band("#C4272F", "#015197")),
            Map.entry("NP", Spec.h("#DC143C", "#003893")),
            Map.entry("PH", Spec.tri("#0038A8", "#FFFFFF", "#CE1126")),
            Map.entry("AU", Spec.h("#00247D")), // special-cased in paintSpec()
            Map.entry("NZ", Spec.cross("#012169", "#FFFFFF"))
    );

    private FlagPainter() {
    }
    private static final Map<String, BufferedImage> IMAGES = new ConcurrentHashMap<>();
    private static final BufferedImage MISSING = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);

    /** Real flag picture for an ISO code, or MISSING if the file does not exist. */
    private static BufferedImage image(String iso) {
        return IMAGES.computeIfAbsent(iso.toLowerCase(), code -> {
            try (InputStream in = FlagPainter.class.getResourceAsStream("/assets/flags/" + code + ".png")) {
                if (in == null) return MISSING;
                BufferedImage img = ImageIO.read(in);
                return img == null ? MISSING : img;
            } catch (Exception e) {
                return MISSING;
            }
        });
    }

    /** Paints the flag of {@code isoCode} into the rectangle; null/unknown → neutral badge. */
    public static void paint(Graphics2D g, String isoCode, float x, float y, float w, float h) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        // Clip to a rounded flag shape
        g2.clip(new RoundRectangle2D.Float(x, y, w, h, w * 0.12f, w * 0.12f));

        String iso = isoCode == null ? "" : isoCode.toUpperCase();
        BufferedImage img = iso.length() == 2 ? image(iso) : MISSING;
        Spec spec = FLAGS.get(iso);
        if (img != MISSING) {
            // Real flag picture
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.drawImage(img, Math.round(x), Math.round(y), Math.round(w), Math.round(h), null);
        } else if (spec != null) {
            paintSpec(g2, iso, spec, x, y, w, h);
        } else if (iso.length() == 2) {
            paintBadge(g2, iso, x, y, w, h);
        } else {
            paintNoCountry(g2, x, y, w, h);
        }
        g2.dispose();
        // Thin dark border for contrast on any background
        g.setColor(new Color(0, 0, 0, 120));
        g.setStroke(new BasicStroke(1f));
        g.draw(new RoundRectangle2D.Float(x, y, w, h, w * 0.12f, w * 0.12f));
    }

    private static void paintSpec(Graphics2D g2, String iso, Spec s, float x, float y, float w, float h) {
        // Special cases first
        switch (iso) {
            case "GB" -> { paintUnitedKingdom(g2, x, y, w, h); return; }
            case "US" -> { paintUnitedStates(g2, x, y, w, h); return; }
            case "MY" -> { paintMalaysia(g2, x, y, w, h); return; }
            case "AU" -> { paintAustralia(g2, x, y, w, h); return; }
            default -> { }
        }
        String[] colors = s.colors();
        // Egypt/Iraq style discs drawn over the default field when colors is placeholder
        if (colors.length == 1 && colors[0] == null) colors = new String[]{"#333333"};

        int n = colors.length;
        double[] weights = s.weights();
        double total = 0;
        if (weights != null) for (double wt : weights) total += wt;
        else total = n;

        float offset = 0;
        for (int i = 0; i < n; i++) {
            double frac = weights != null ? weights[i] / total : 1.0 / n;
            g2.setColor(parse(colors[i]));
            if (s.vertical()) {
                g2.fillRect(Math.round(x + offset), Math.round(y),
                        Math.max(1, (int) Math.ceil(w * frac) + 1), Math.round(h));
            } else {
                g2.fillRect(Math.round(x), Math.round(y + offset),
                        Math.round(w), Math.max(1, (int) Math.ceil(h * frac) + 1));
            }
            offset += (float) (frac * (s.vertical() ? w : h));
        }

        if (s.hoistBand() != null) {
            g2.setColor(parse(s.hoistBand()));
            g2.fillRect(Math.round(x), Math.round(y), Math.round(w * 0.33f), Math.round(h));
        }
        if (s.hoistTriangle() != null) {
            g2.setColor(parse(s.hoistTriangle()));
            Path2D tri = new Path2D.Float();
            tri.moveTo(x, y);
            tri.lineTo(x + w * 0.42f, y + h / 2f);
            tri.lineTo(x, y + h);
            tri.closePath();
            g2.fill(tri);
        }
        if (s.canton() != null) {
            g2.setColor(parse(s.canton()));
            g2.fillRect(Math.round(x), Math.round(y), Math.round(w * 0.45f), Math.round(h * 0.5f));
        }
        if (s.disc() != null) {
            // "disc on null field" specs encode both colors: field placeholder handled above
            String discColor = s.disc();
            // Two-color discs (e.g. MA green on red) come via disc(field) with colors[0]
            g2.setColor(parse(discColor));
            float dr = h * 0.38f;
            g2.fill(new Ellipse2D.Float(x + w / 2f - dr, y + h / 2f - dr, dr * 2, dr * 2));
        }
        if (s.cross() != null) {
            g2.setColor(parse(s.cross()));
            float band = h * 0.22f;
            // Nordic-style off-center cross; centered for CH/GR via iso check
            float cx = (iso.equals("CH") || iso.equals("GR")) ? x + w / 2f - band / 2
                    : x + w * 0.36f - band / 2;
            g2.fillRect(Math.round(cx), Math.round(y), Math.round(band), Math.round(h));
            g2.fillRect(Math.round(x), Math.round(y + h / 2f - band / 2), Math.round(w), Math.round(band));
            if (iso.equals("NO")) { // white fimbriation + inner blue cross
                g2.setColor(parse("#FFFFFF"));
                float wb = h * 0.32f;
                float wcx = x + w * 0.36f - wb / 2;
                g2.fillRect(Math.round(wcx), Math.round(y), Math.round(wb), Math.round(h));
                g2.fillRect(Math.round(x), Math.round(y + h / 2f - wb / 2), Math.round(w), Math.round(wb));
                g2.setColor(parse("#00205B"));
                g2.fillRect(Math.round(cx), Math.round(y), Math.round(band), Math.round(h));
                g2.fillRect(Math.round(x), Math.round(y + h / 2f - band / 2), Math.round(w), Math.round(band));
            }
        }
        // Second disc layer for two-tone emblems (EG gold on stripes etc. handled by caller data)
    }

    private static void paintUnitedKingdom(Graphics2D g2, float x, float y, float w, float h) {
        g2.setColor(parse("#012169"));
        g2.fillRect(Math.round(x), Math.round(y), Math.round(w), Math.round(h));
        g2.setColor(parse("#FFFFFF"));
        g2.fillRect(Math.round(x), Math.round(y + h * 0.40f), Math.round(w), Math.round(h * 0.20f));
        g2.fillRect(Math.round(x + w * 0.42f), Math.round(y), Math.round(w * 0.16f), Math.round(h));
        g2.setColor(parse("#C8102E"));
        g2.fillRect(Math.round(x), Math.round(y + h * 0.44f), Math.round(w), Math.round(h * 0.12f));
        g2.fillRect(Math.round(x + w * 0.45f), Math.round(y), Math.round(w * 0.10f), Math.round(h));
    }

    private static void paintUnitedStates(Graphics2D g2, float x, float y, float w, float h) {
        for (int i = 0; i < 7; i++) {
            g2.setColor(i % 2 == 0 ? parse("#B22234") : parse("#FFFFFF"));
            g2.fillRect(Math.round(x), Math.round(y + i * h / 7f), Math.round(w), Math.round(h / 7f) + 1);
        }
        g2.setColor(parse("#3C3B6E"));
        g2.fillRect(Math.round(x), Math.round(y), Math.round(w * 0.45f), Math.round(h * 4 / 7f));
    }

    private static void paintMalaysia(Graphics2D g2, float x, float y, float w, float h) {
        for (int i = 0; i < 7; i++) {
            g2.setColor(i % 2 == 0 ? parse("#CC0001") : parse("#FFFFFF"));
            g2.fillRect(Math.round(x), Math.round(y + i * h / 7f), Math.round(w), Math.round(h / 7f) + 1);
        }
        g2.setColor(parse("#010066"));
        g2.fillRect(Math.round(x), Math.round(y), Math.round(w * 0.5f), Math.round(h * 0.5f));
        g2.setColor(parse("#FFCC00"));
        float dr = h * 0.13f;
        g2.fill(new Ellipse2D.Float(x + w * 0.20f - dr, y + h * 0.25f - dr, dr * 2, dr * 2));
    }

    private static void paintAustralia(Graphics2D g2, float x, float y, float w, float h) {
        g2.setColor(parse("#00247D"));
        g2.fillRect(Math.round(x), Math.round(y), Math.round(w), Math.round(h));
        g2.setColor(parse("#FFFFFF"));
        float dr = h * 0.07f;
        g2.fill(new Ellipse2D.Float(x + w * 0.72f - dr, y + h * 0.30f - dr, dr * 2, dr * 2));
        g2.fill(new Ellipse2D.Float(x + w * 0.62f - dr, y + h * 0.62f - dr, dr * 2, dr * 2));
        g2.fill(new Ellipse2D.Float(x + w * 0.85f - dr, y + h * 0.55f - dr, dr * 2, dr * 2));
        g2.fill(new Ellipse2D.Float(x + w * 0.75f - dr, y + h * 0.82f - dr, dr * 2, dr * 2));
    }

    /** Unknown country: dark badge with the ISO code letters. */
    private static void paintBadge(Graphics2D g2, String iso, float x, float y, float w, float h) {
        g2.setColor(new Color(0x18, 0x18, 0x18));
        g2.fillRect(Math.round(x), Math.round(y), Math.round(w), Math.round(h));
        g2.setColor(new Color(0xC5, 0xA6, 0x6A));
        g2.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, Math.max(8, (int) (h * 0.55f))));
        var fm = g2.getFontMetrics();
        g2.drawString(iso, x + (w - fm.stringWidth(iso)) / 2f, y + h * 0.72f);
    }

    /** No country information at all: subtle neutral globe-ish dot. */
    private static void paintNoCountry(Graphics2D g2, float x, float y, float w, float h) {
        g2.setColor(new Color(0x18, 0x18, 0x18));
        g2.fillRect(Math.round(x), Math.round(y), Math.round(w), Math.round(h));
        g2.setColor(new Color(0x99, 0x99, 0x99, 180));
        float dr = h * 0.30f;
        g2.fill(new Ellipse2D.Float(x + w / 2 - dr, y + h / 2 - dr, dr * 2, dr * 2));
    }

    private static Color parse(String hex) {
        if (hex == null || hex.length() < 7) return Color.GRAY;
        return Color.decode(hex);
    }
}

