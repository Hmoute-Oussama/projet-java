package com.tawkit.view.render;

import com.tawkit.model.content.ContentItem;
import com.tawkit.view.TawkitTheme;

import java.awt.Font;
import java.awt.Graphics2D;
import java.util.List;

/**
 * Permanent "verse of the day" widget (change #8) — a small, fixed corner
 * display whose verse changes once per day, independent of the central
 * calligraphy rotation. Sits bottom-left, below the prayer schedule.
 */
public final class VerseOfDayElement implements DashboardElement {

    private static final int MAX_LINES = 3;

    private List<String> wrapped = List.of();
    private String reference = "";

    @Override
    public void render(Graphics2D g2, RenderContext ctx) {
        if (wrapped.isEmpty()) return;
        float vw = (float) ctx.vw();
        float lineHeight = 2.0f * vw;

        // Ornamental marker
        g2.setFont(TawkitTheme.arabicBold(1.6f * vw));
        TextDraw.drawShadowed(g2, "﴾ آية اليوم ﴿", 2.0 * ctx.vw(),
                (float) (80.0 * ctx.vh()), TextDraw.Anchor.LEFT,
                TawkitTheme.GOLDEN_BEIGE, 0.08f * vw);

        g2.setFont(TawkitTheme.arabic(1.7f * vw));
        float y = (float) (84.0 * ctx.vh());
        int shown = 0;
        for (String line : wrapped) {
            if (shown++ >= MAX_LINES) break;
            TextDraw.drawShadowed(g2, line, 2.0 * ctx.vw(), y,
                    TextDraw.Anchor.LEFT, TawkitTheme.WARM_WHITE, 0.06f * vw);
            y += lineHeight;
        }

        if (!reference.isEmpty()) {
            g2.setFont(TawkitTheme.serif(Font.ITALIC, 1.0f * vw));
            TextDraw.drawShadowed(g2, reference, 2.0 * ctx.vw(), y + 0.3f * vw,
                    TextDraw.Anchor.LEFT, TextDraw.withAlpha(TawkitTheme.GRAY, 0.75f), 0);
        }
    }

    public void setVerse(ContentItem verse) {
        if (verse == null) {
            wrapped = List.of();
            reference = "";
            return;
        }
        // Measure with the render font so wrapping matches what is drawn
        java.awt.image.BufferedImage probe = new java.awt.image.BufferedImage(
                1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = probe.createGraphics();
        g2.setFont(TawkitTheme.arabic(1.7f));
        // ~20vw max width, scaled for the probe using font-relative estimate
        String text = verse.getArabicText() == null ? "" : verse.getArabicText();
        // Approximate wrap: split on commas if long, else keep one line
        if (g2.getFontMetrics().stringWidth(text) > 320) {
            String[] parts = text.split("، ");
            wrapped = parts.length > 1
                    ? List.of(parts[0] + "،", String.join("، ", java.util.Arrays.copyOfRange(parts, 1, parts.length)))
                    : List.of(text);
        } else {
            wrapped = List.of(text);
        }
        g2.dispose();
        String ref = verse.getReference() == null ? "" : verse.getReference();
        reference = ref.startsWith("Quran") ? "— " + ref.substring(5).trim() : "— " + ref;
    }
}
