package com.tawkit.view.render;

import com.tawkit.model.weather.WeatherConditions;
import com.tawkit.model.weather.WeatherData;
import com.tawkit.view.TawkitTheme;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Top-left cluster: hamburger menu, temperature, a real weather icon for the
 * actual condition, geographic coordinates and the flag of the resolved
 * country — the compact stack of the reference, but fully dynamic:
 *
 * <ul>
 *   <li>icon follows the real WMO condition (sun only for clear skies; moon
 *       at night; rain/snow/thunder/fog/cloud icons otherwise)</li>
 *   <li>temperature in °C or °F (unit switchable at runtime)</li>
 *   <li>loading / error / empty states instead of misleading defaults</li>
 *   <li>flag drawn from the location's ISO code — never hardcoded</li>
 * </ul>
 */
public final class HeaderLeftElement implements DashboardElement {

    /** Display state of the weather part of the widget. */
    public enum WeatherState { IDLE, LOADING, OK, ERROR }

    private WeatherState weatherState = WeatherState.IDLE;
    private double tempCelsius = Double.NaN;
    private int wmoCode = 0;
    private boolean isDay = true;
    private char unit = 'C';                 // 'C' | 'F'
    private String coordsText = "";
    private String countryCode = "";         // ISO 3166-1 alpha-2 for the flag

    @Override
    public void render(Graphics2D g2, RenderContext ctx) {
        float x = (float) (2.0 * ctx.vw());
        double vw = ctx.vw();
        double vh = ctx.vh();

        // Hamburger menu (three lines), top of the cluster
        g2.setColor(TawkitTheme.MENU_GRAY);
        g2.setStroke(new BasicStroke(Math.max(1.5f, (float) (0.18 * vw))));
        for (int i = 0; i < 3; i++) {
            float ly = (float) (2.2 * vh) + i * (float) (0.7 * vh);
            g2.drawLine((int) Math.round(x), (int) Math.round(ly),
                    (int) Math.round(x + 1.6 * vw), (int) Math.round(ly));
        }

        // Temperature + condition icon
        float tempY = (float) (7.4 * vh);
        g2.setFont(TawkitTheme.serif(java.awt.Font.BOLD, 1.9f * (float) vw));
        Color tempColor = weatherState == WeatherState.ERROR
                ? TextDraw.withAlpha(TawkitTheme.GRAY, 0.9f) : TawkitTheme.WARM_WHITE;
        TextDraw.draw(g2, displayTemp(), x, tempY, TextDraw.Anchor.LEFT, tempColor);

        if (weatherState == WeatherState.OK) {
            paintConditionIcon(g2, WeatherConditions.from(wmoCode, isDay),
                    x + 4.6f * (float) vw, tempY - 0.6f * (float) vh, 0.95f * (float) vw);
        } else if (weatherState == WeatherState.LOADING) {
            paintLoading(g2, x + 4.6f * (float) vw, tempY - 0.6f * (float) vh, 0.8f * (float) vw);
        }

        // Coordinates
        g2.setFont(TawkitTheme.serif(java.awt.Font.PLAIN, 1.05f * (float) vw));
        TextDraw.draw(g2, coordsText, x, (float) (11.0 * vh), TextDraw.Anchor.LEFT, TawkitTheme.GRAY);

        // Flag of the resolved country (dynamic, never hardcoded)
        paintFlag(g2, x, (float) (12.8 * vh), (float) (2.2 * vw), (float) (1.4 * vw));
    }

    /** True when the click hits the hamburger zone (opens the menu). */
    public boolean isMenuClick(int x, int y, RenderContext ctx) {
        return x < 4.5 * ctx.vw() && y < 6.5 * ctx.vh();
    }

    // ------------------------------------------------------------- data API

    /** New weather data arrived → show real values. */
    public void setWeather(WeatherData data) {
        if (data == null) {
            weatherState = WeatherState.IDLE;
            tempCelsius = Double.NaN;
        } else {
            weatherState = WeatherState.OK;
            tempCelsius = data.getCurrentTemp();
            wmoCode = data.getCurrentCode();
            isDay = data.isDay();
        }
    }

    public void setWeatherState(WeatherState state) {
        this.weatherState = state;
    }

    /** 'C' or 'F' — conversion happens at render from the stored Celsius value. */
    public void setTemperatureUnit(char unit) {
        this.unit = unit == 'F' ? 'F' : 'C';
    }

    public void setCityInfo(double latitude, double longitude) {
        this.coordsText = formatDms(latitude, "N", "S") + "   " + formatDms(longitude, "E", "W");
    }

    public void setCountryCode(String iso) {
        this.countryCode = iso == null ? "" : iso.trim().toUpperCase();
    }

    // ------------------------------------------------------------ rendering

    private String displayTemp() {
        return switch (weatherState) {
            case LOADING -> "…°";
            case OK -> {
                if (Double.isNaN(tempCelsius)) yield "--°";
                yield Math.round(unit == 'F' ? tempCelsius * 9.0 / 5.0 + 32.0 : tempCelsius) + "°";
            }
            default -> "--°"; // IDLE / ERROR
        };
    }

    /** DMS format like "N 48° 12'" with correct hemisphere letters. */
    private String formatDms(double degrees, String positiveHemi, String negativeHemi) {
        int deg = (int) Math.floor(Math.abs(degrees));
        int min = (int) Math.round((Math.abs(degrees) - deg) * 60);
        if (min >= 60) {
            deg += 1;
            min -= 60;
        }
        return (degrees >= 0 ? positiveHemi : negativeHemi) + " " + deg + "° "
                + String.format("%02d'", min);
    }

    private void paintLoading(Graphics2D g2, float cx, float cy, float r) {
        g2.setColor(TextDraw.withAlpha(TawkitTheme.MENU_GRAY, 0.8f));
        g2.setStroke(new BasicStroke(Math.max(1.2f, r * 0.16f)));
        // Spinning arc — the screen repaints every second, so it visibly rotates
        double angle = (System.currentTimeMillis() / 250 % 360);
        int sweep = 250;
        int px = Math.round(cx - r), py = Math.round(cy - r);
        int d = Math.round(r * 2);
        g2.drawArc(px, py, d, d, (int) angle, sweep);
    }

    /** Draws the real condition icon (never a sun when it rains/snows). */
    private void paintConditionIcon(Graphics2D g2, WeatherConditions kind, float cx, float cy, float r) {
        switch (kind) {
            case SUN -> paintSun(g2, cx, cy, r);
            case MOON -> paintMoon(g2, cx, cy, r);
            case PARTLY -> {
                paintSun(g2, cx - r * 0.45f, cy - r * 0.45f, r * 0.62f);
                paintCloud(g2, cx + r * 0.18f, cy + r * 0.28f, r * 0.75f, TawkitTheme.WARM_WHITE);
            }
            case CLOUD -> paintCloud(g2, cx, cy, r, TawkitTheme.WARM_WHITE);
            case FOG -> paintFog(g2, cx, cy, r);
            case RAIN -> {
                paintCloud(g2, cx, cy - r * 0.22f, r * 0.85f, TextDraw.withAlpha(TawkitTheme.GRAY, 0.95f));
                paintDrops(g2, cx, cy + r * 0.55f, r * 0.8f, false);
            }
            case SNOW -> {
                paintCloud(g2, cx, cy - r * 0.22f, r * 0.85f, TawkitTheme.WARM_WHITE);
                paintDrops(g2, cx, cy + r * 0.55f, r * 0.8f, true);
            }
            case THUNDER -> {
                paintCloud(g2, cx, cy - r * 0.22f, r * 0.85f, TextDraw.withAlpha(TawkitTheme.GRAY, 0.95f));
                paintBolt(g2, cx, cy + r * 0.35f, r * 0.75f);
            }
        }
    }

    private void paintSun(Graphics2D g2, float cx, float cy, float r) {
        g2.setColor(TawkitTheme.SUN_YELLOW);
        g2.fill(new Ellipse2D.Float(cx - r * 0.55f, cy - r * 0.55f, r * 1.1f, r * 1.1f));
        g2.setStroke(new BasicStroke(Math.max(1f, r * 0.16f)));
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI / 4 * i;
            g2.drawLine(Math.round(cx + (float) Math.cos(angle) * r * 0.8f),
                    Math.round(cy + (float) Math.sin(angle) * r * 0.8f),
                    Math.round(cx + (float) Math.cos(angle) * r * 1.35f),
                    Math.round(cy + (float) Math.sin(angle) * r * 1.35f));
        }
    }

    private void paintMoon(Graphics2D g2, float cx, float cy, float r) {
        g2.setColor(TawkitTheme.SUN_YELLOW);
        g2.fill(new Ellipse2D.Float(cx - r * 0.55f, cy - r * 0.55f, r * 1.1f, r * 1.1f));
        g2.setColor(new Color(0x05, 0x05, 0x05)); // background color cuts the crescent
        g2.fill(new Ellipse2D.Float(cx - r * 0.15f, cy - r * 0.75f, r * 1.0f, r * 1.0f));
    }

    private void paintCloud(Graphics2D g2, float cx, float cy, float r, Color color) {
        g2.setColor(color);
        float body = r * 0.55f;
        g2.fill(new Ellipse2D.Float(cx - r * 0.75f, cy - body * 0.4f, body * 1.5f, body));
        g2.fill(new Ellipse2D.Float(cx - r * 0.35f, cy - body * 1.0f, body * 1.1f, body * 1.15f));
        g2.fill(new Ellipse2D.Float(cx + r * 0.05f, cy - body * 0.55f, body * 1.25f, body * 1.05f));
        g2.fill(new RoundRectangle2D.Float(cx - r * 0.72f, cy, r * 1.45f, body * 0.55f, body * 0.5f, body * 0.5f));
    }

    private void paintDrops(Graphics2D g2, float cx, float cy, float r, boolean snow) {
        Color drop = snow ? Color.WHITE : new Color(0x6E, 0xC1, 0xE4);
        g2.setColor(drop);
        for (int i = 0; i < 3; i++) {
            float dx = cx + (i - 1) * r * 0.38f;
            if (snow) {
                g2.fill(new Ellipse2D.Float(dx - r * 0.07f, cy - r * 0.07f, r * 0.14f, r * 0.14f));
            } else {
                g2.fill(new RoundRectangle2D.Float(dx - r * 0.055f, cy, r * 0.11f, r * 0.42f,
                        r * 0.11f, r * 0.11f));
            }
        }
    }

    private void paintBolt(Graphics2D g2, float cx, float cy, float r) {
        g2.setColor(TawkitTheme.SUN_YELLOW);
        Path2D bolt = new Path2D.Float();
        bolt.moveTo(cx - r * 0.15f, cy - r * 0.3f);
        bolt.lineTo(cx + r * 0.2f, cy - r * 0.3f);
        bolt.lineTo(cx - r * 0.02f, cy + r * 0.05f);
        bolt.lineTo(cx + r * 0.28f, cy + r * 0.05f);
        bolt.lineTo(cx - r * 0.18f, cy + r * 0.75f);
        bolt.lineTo(cx - r * 0.02f, cy + r * 0.12f);
        bolt.lineTo(cx - r * 0.30f, cy + r * 0.12f);
        bolt.closePath();
        g2.fill(bolt);
    }

    private void paintFog(Graphics2D g2, float cx, float cy, float r) {
        g2.setColor(TextDraw.withAlpha(TawkitTheme.MENU_GRAY, 0.9f));
        g2.setStroke(new BasicStroke(Math.max(1.5f, r * 0.16f),
                BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 3; i++) {
            float ly = cy - r * 0.35f + i * r * 0.38f;
            float inset = (i == 1) ? r * 0.12f : 0;
            g2.drawLine(Math.round(cx - r + inset), Math.round(ly),
                    Math.round(cx + r - inset), Math.round(ly));
        }
    }

    private void paintFlag(Graphics2D g2, float x, float y, float fw, float fh) {
        FlagPainter.paint(g2, countryCode, x, y, fw, fh);
    }
}
