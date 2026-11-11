package com.example.simplemusicplayer.util;

import java.util.Locale;

/**
 * Utility class for time and duration formatting.
 */
public final class TimeUtils {

    private TimeUtils() {
        // Utility class, do not instantiate
    }

    /**
     * Formats duration in milliseconds to readable mm:ss (or hh:mm:ss if >= 1 hour).
     *
     * @param millis duration in milliseconds
     * @return formatted duration string (e.g. "03:45" or "01:05:20")
     */
    public static String formatDuration(long millis) {
        if (millis <= 0) {
            return "00:00";
        }
        long totalSeconds = millis / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
        }
    }
}
