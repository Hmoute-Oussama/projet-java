package com.tawkit.view.render;

import com.tawkit.model.common.HijriMonthNames;
import com.tawkit.service.localization.Messages;
import com.tawkit.view.TawkitTheme;

import java.awt.Font;
import java.awt.Graphics2D;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;

/** Top-center date header, e.g. "Mercredi 7 Joumada ath-thania 1448 . 18/11/2026". */
public final class DateHeaderElement implements DashboardElement {

    private String dateText = "";

    @Override
    public void render(Graphics2D g2, RenderContext ctx) {
        boolean arabic = "ar".equals(Messages.getLocale().getLanguage());
        g2.setFont(arabic
                ? TawkitTheme.arabic((float) (2.2 * ctx.vw()))           // Amiri: Arabic glyphs
                : TawkitTheme.serif(Font.PLAIN, (float) (1.9 * ctx.vw())));
        TextDraw.drawShadowed(g2, dateText, ctx.centerX(), (float) (5.0 * ctx.vh()),
                TextDraw.Anchor.CENTER, TawkitTheme.WARM_WHITE, 0.12f * (float) ctx.vw());
    }

    public void setDate(LocalDate gregorian, int hijriDay, int hijriMonth, int hijriYear) {
        Locale locale = Messages.getLocale();                 // language chosen in Settings
        String weekday = gregorian.getDayOfWeek().getDisplayName(TextStyle.FULL, locale);
        if (!weekday.isEmpty()) {                             // "mercredi" -> "Mercredi"
            weekday = weekday.substring(0, 1).toUpperCase(locale) + weekday.substring(1);
        }
        String month = HijriMonthNames.forLanguage(locale.getLanguage(), hijriMonth);
        this.dateText = weekday + " " + hijriDay + " " + month + " " + hijriYear
                + " . " + String.format("%02d/%02d/%04d",
                gregorian.getDayOfMonth(), gregorian.getMonthValue(), gregorian.getYear());
    }
}