package com.tawkit.service.content;

import com.tawkit.model.content.ContentItem;
import com.tawkit.model.content.ContentType;
import com.tawkit.model.content.Slide;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Loads the rotating display content: Qur'an verses, hadith, adhkar and
 * user announcements. Bundled data lives in classpath resources
 * (<code>/data/*.txt</code>, one item per line, fields separated by '|'):
 *
 * <pre>arabic text | translation | reference</pre>
 *
 * User slides are read from <code>&lt;home&gt;/.tawkit/slides/</code>:
 * <code>slides.txt</code> for text/URL entries and image files (.jpg/.png)
 * placed directly in the folder.
 */
public class ContentService {

    private final List<ContentItem> verses = new ArrayList<>();
    private final List<ContentItem> hadiths = new ArrayList<>();
    private final List<ContentItem> adhkar = new ArrayList<>();
    private final List<String> announcements = new ArrayList<>();
    private final List<Slide> slides = new ArrayList<>();

    private final File slidesFolder;

    public ContentService() {
        this(new File(System.getProperty("user.home"), ".tawkit/slides"));
    }

    public ContentService(File slidesFolder) {
        this.slidesFolder = slidesFolder;
        loadBundled();
        loadUserSlides();
    }

    public List<ContentItem> getVerses() {
        return Collections.unmodifiableList(verses);
    }

    /**
     * Deterministic "verse of the day" (change #8): a stable pick from the
     * bundled Qur'an verses for the given date — the same verse shows all
     * day, independent of the main content rotation.
     */
    public ContentItem getVerseOfDay(LocalDate date) {
        if (verses.isEmpty()) return null;
        int seed = date.getYear() * 366 + date.getDayOfYear();
        return verses.get(seed % verses.size());
    }

    public List<ContentItem> getHadiths() {
        return Collections.unmodifiableList(hadiths);
    }

    public List<ContentItem> getAdhkar() {
        return Collections.unmodifiableList(adhkar);
    }

    private static final java.util.regex.Pattern DAY_TAG = java.util.regex.Pattern.compile(
            "\\[\\s*((?:MON|TUE|WED|THU|FRI|SAT|SUN)(?:\\s*,\\s*(?:MON|TUE|WED|THU|FRI|SAT|SUN))*)\\s*\\]",
            java.util.regex.Pattern.CASE_INSENSITIVE);

    /** Announcements for today: a tag like [FRI] or [SAT,SUN] (anywhere in the line) limits the days. */
    public List<String> getAnnouncements() {
        String today = LocalDate.now().getDayOfWeek().name().substring(0, 3);   // e.g. WED
        List<String> out = new ArrayList<>();
        for (String line : announcements) {
            String text = line.trim();
            java.util.regex.Matcher m = DAY_TAG.matcher(text);
            if (m.find()) {
                String days = m.group(1).toUpperCase();
                if (!days.contains(today)) continue;                       // not today: skip
                text = (text.substring(0, m.start()) + text.substring(m.end())).trim();  // remove tag
            }
            if (!text.isEmpty()) out.add(text);
        }
        return Collections.unmodifiableList(out);
    }

    public List<Slide> getSlides() {
        return Collections.unmodifiableList(slides);
    }

    public File getSlidesFolder() {
        return slidesFolder;
    }

    public void setAnnouncements(List<String> items) {
        announcements.clear();
        if (items != null) announcements.addAll(items);
    }
    /** Loads the bundled announcements in the given language (falls back to English). */
    public void loadAnnouncementsFor(String lang) {
        List<String> lines = readLines("/data/announcements_" + lang + ".txt");
        if (lines.isEmpty()) lines = readLines("/data/announcements.txt");
        announcements.clear();
        announcements.addAll(lines);
    }
    public void reloadSlides() {
        slides.clear();
        loadUserSlides();
    }

    // ------------------------------------------------------------- loading

    private void loadBundled() {
        verses.addAll(readItems("/data/quran.txt", ContentType.QURAN_VERSE));
        hadiths.addAll(readItems("/data/hadith.txt", ContentType.HADITH));
        adhkar.addAll(readItems("/data/azkar.txt", ContentType.ADHKAR));
        announcements.addAll(readLines("/data/announcements.txt"));
    }

    private List<ContentItem> readItems(String resource, ContentType type) {
        List<ContentItem> items = new ArrayList<>();
        for (String line : readLines(resource)) {
            String[] parts = line.split("\\|", 3);
            if (parts.length >= 1) {
                String arabic = parts.length > 0 ? parts[0].trim() : "";
                String translation = parts.length > 1 ? parts[1].trim() : "";
                String reference = parts.length > 2 ? parts[2].trim() : "";
                if (arabic.isEmpty() && translation.isEmpty()) continue;
                items.add(new ContentItem(type, arabic, translation, reference));
            }
        }
        return items;
    }

    private List<String> readLines(String resource) {
        List<String> lines = new ArrayList<>();
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            if (in == null) return lines;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#")) lines.add(line);
                }
            }
        } catch (IOException e) {
            com.tawkit.infrastructure.Log.warn("Content resource failed " + resource
                    + " (" + e.getMessage() + ")");
        }
        return lines;
    }

    private void loadUserSlides() {
        if (slidesFolder.isDirectory()) {
            File index = new File(slidesFolder, "slides.txt");
            if (index.isFile()) {
                try {
                    for (String line : Files.readAllLines(index.toPath(), StandardCharsets.UTF_8)) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) continue;
                        Slide slide = Slide.parse(line);
                        if (slide != null) slides.add(slide);
                    }
                } catch (IOException e) {
                    com.tawkit.infrastructure.Log.warn("slides.txt unreadable ("
                            + e.getMessage() + ")");
                }
            }
            // Also pick up image files dropped in the folder directly
            File[] images = slidesFolder.listFiles(
                    (dir, name) -> name.toLowerCase().endsWith(".jpg")
                            || name.toLowerCase().endsWith(".jpeg")
                            || name.toLowerCase().endsWith(".png"));
            if (images != null) {
                for (File img : images) {
                    Slide s = new Slide(Slide.Kind.IMAGE, img.getName());
                    if (!slides.contains(s)) slides.add(s);
                }
            }
        } else {
            try {
                Files.createDirectories(slidesFolder.toPath());
            } catch (IOException ignored) {
            }
        }
        Path marker = slidesFolder.toPath().resolve("README.txt");
        if (!Files.exists(marker)) {
            try {
                Files.writeString(marker, """
                        Put your slides here.
                        - slides.txt : one slide per line (text, or a URL, or a picture filename)
                        - '|' inside a text creates a line break
                        - .jpg / .png files in this folder are picked up automatically
                        """, StandardCharsets.UTF_8);
            } catch (IOException ignored) {
            }
        }
    }
}
