package com.tawkit.infrastructure;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Central logging for the kiosk. Writes to the console and to
 * <code>&lt;user.home&gt;/.tawkit/logs/tawkit*.log</code> (rotated, 3 files of
 * 1 MB) so problems on a mosque screen can be diagnosed afterwards.
 * If the log folder is not writable, console logging still works.
 */
public final class Log {

    private static final Logger LOGGER = Logger.getLogger("com.tawkit");

    static {
        LOGGER.setUseParentHandlers(false);
        LOGGER.setLevel(Level.INFO);

        ConsoleHandler console = new ConsoleHandler();
        console.setFormatter(new SimpleFormatter());
        console.setLevel(Level.INFO);
        LOGGER.addHandler(console);

        try {
            Path dir = Path.of(System.getProperty("user.home"), ".tawkit", "logs");
            Files.createDirectories(dir);
            FileHandler file = new FileHandler(
                    dir.resolve("tawkit%u.log").toString(), 1_000_000, 3, true);
            file.setFormatter(new SimpleFormatter());
            file.setLevel(Level.INFO);
            LOGGER.addHandler(file);
        } catch (IOException e) {
            LOGGER.warning("File logging disabled (" + e.getMessage() + ")");
        }
    }

    private Log() {
    }

    public static void info(String message) {
        LOGGER.info(message);
    }

    public static void warn(String message) {
        LOGGER.warning(message);
    }

    public static void error(String message) {
        LOGGER.severe(message);
    }

    public static void error(String message, Throwable thrown) {
        LOGGER.log(Level.SEVERE, message, thrown);
    }
}
