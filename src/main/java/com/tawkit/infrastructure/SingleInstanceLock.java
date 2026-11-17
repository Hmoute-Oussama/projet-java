package com.tawkit.infrastructure;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Prevents two Tawkit instances from running at the same time — on a kiosk
 * two fullscreen windows fighting over the screen is a real problem.
 * Uses a {@link FileLock} on <code>&lt;user.home&gt;/.tawkit/tawkit.lock</code>;
 * the lock is held until the JVM exits (or {@link #close()} is called).
 */
public final class SingleInstanceLock implements AutoCloseable {

    private final FileChannel channel;
    private final FileLock lock;

    private SingleInstanceLock(FileChannel channel, FileLock lock) {
        this.channel = channel;
        this.lock = lock;
    }

    /**
     * @return a held lock, or {@code null} when another instance already
     *         holds it.
     */
    public static SingleInstanceLock acquire() {
        try {
            Path file = Path.of(System.getProperty("user.home"), ".tawkit", "tawkit.lock");
            Files.createDirectories(file.getParent());
            RandomAccessFile raf = new RandomAccessFile(file.toFile(), "rw");
            FileChannel channel = raf.getChannel();
            try {
                FileLock lock = channel.tryLock();
                if (lock != null) {
                    return new SingleInstanceLock(channel, lock);
                }
                channel.close();
                return null;
            } catch (OverlappingFileLockException e) {
                channel.close();
                return null;
            }
        } catch (IOException e) {
            // If the lock file cannot be created, do not block the app — just log.
            Log.warn("Single-instance lock unavailable: " + e.getMessage());
            return null;
        }
    }

    @Override
    public void close() {
        try {
            lock.release();
        } catch (IOException ignored) {
        }
        try {
            channel.close();
        } catch (IOException ignored) {
        }
    }
}
