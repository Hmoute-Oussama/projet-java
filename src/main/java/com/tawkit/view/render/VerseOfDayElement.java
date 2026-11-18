package com.tawkit.view.render;

import com.tawkit.model.content.ContentItem;
import com.tawkit.view.TawkitTheme;

import java.awt.Font;
import java.awt.Graphics2D;
import java.util.List;

/**
 * "آية اليوم" — the verse of the day, displayed in the <b>center</b> of the
 * dashboard (change #8). It changes once per day and replaces the rotating
 * central calligraphy while it is present, so the middle of the screen always
 * shows one clean, readable ayah.
 */
public final class VerseOfDayElement implements DashboardElement {

    private static final int MAX_LINES = 3;
    /** Candidate sizes (vw), largest that fits {@link #MAX_LINES} wins. */
    private static final float[] SIZE_CANDIDATES = {3.0f, 2.7f, 2.4f, 2.1f, 1.9f};

    private String text = "";
    private String reference = "";
    private List<String> wrapped = List.of();
    private float fontSizeVw = 2.4f;
    private double cachedWidth = -1;

    @Override
    public void render(Graphics2D g2, RenderContext ctx) {
        if (text.isBlank()) return;
        if (cachedWidth != ctx.width()) {
            layout(g2, ctx);
            cachedWidth = ctx.width();
        }

        float vw = (float) ctx.vw();
        float size = fontSizeVw * vw;
        float lineHeight = size * 1.55f;
        float blockHeight = Math.max(0, wrapped.size() - 1) * lineHeight;

        // Center the whole verse block on the middle of the screen.
        float firstBaseline = (float) (50.0 * ctx.vh()) - blockHeight / 2f + size * 0.35f;

        g2.setFont(TawkitTheme.arabic(size));
        float y = firstBaseline;
        for (String line : wrapped) {
            TextDraw.drawShadowed(g2, line, ctx.centerX(), y,
                    TextDraw.Anchor.CENTER, TawkitTheme.WARM_WHITE, 0.07f * vw);
            y += lineHeight;
        }

        if (!reference.isEmpty()) {
            float refY = y - lineHeight + size * 0.9f;
            boolean arabicRef = reference.codePoints()
                    .anyMatch(cp -> cp >= 0x0600 && cp <= 0x06FF);
            if (arabicRef) {
                // Arabic surah name: needs an Arabic font, a Latin italic font
                // would render it as empty boxes.
                g2.setFont(TawkitTheme.arabic(1.35f * vw));
            } else {
                g2.setFont(TawkitTheme.serif(Font.ITALIC, 1.2f * vw));
            }
            TextDraw.drawShadowed(g2, reference, ctx.centerX(), refY,
                    TextDraw.Anchor.CENTER,
                    TextDraw.withAlpha(TawkitTheme.GOLDEN_BEIGE, 0.85f), 0);
        }
    }

    /** Chooses the largest font size that keeps the verse within 3 lines. */
    private void layout(Graphics2D g2, RenderContext ctx) {
        float vw = (float) ctx.vw();
        float maxWidth = 84f * vw;
        for (float candidate : SIZE_CANDIDATES) {
            g2.setFont(TawkitTheme.arabic(candidate * vw));
            List<String> lines = TextDraw.wrap(g2, text, maxWidth);
            if (lines.size() <= MAX_LINES) {
                fontSizeVw = candidate;
                wrapped = lines;
                return;
            }
        }
        // Extremely long verse: smallest size, truncate instead of overflowing
        fontSizeVw = SIZE_CANDIDATES[SIZE_CANDIDATES.length - 1];
        g2.setFont(TawkitTheme.arabic(fontSizeVw * vw));
        List<String> lines = TextDraw.wrap(g2, text, maxWidth);
        wrapped = lines.size() <= MAX_LINES ? lines : lines.subList(0, MAX_LINES);
    }

    public void setVerse(ContentItem verse) {
        if (verse == null) {
            text = "";
            reference = "";
            wrapped = List.of();
            cachedWidth = -1;
            return;
        }
        text = verse.getArabicText() == null ? "" : verse.getArabicText();
        String ref = verse.getReference() == null ? "" : verse.getReference();
        if (ref.isBlank()) {
            reference = "";
        } else if (ref.codePoints().anyMatch(cp -> cp >= 0x0600 && cp <= 0x06FF)) {
            // Arabic surah name ("الطلاق") → "سورة الطلاق"
            reference = ref.startsWith("سورة") ? ref : "سورة " + ref;
        } else if (ref.startsWith("Quran")) {
            reference = "— " + ref.substring(5).trim();
        } else {
            reference = "— " + ref;
        }
        cachedWidth = -1; // re-wrap for the next paint
    }
}
