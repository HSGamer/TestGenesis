package me.hsgamer.testgenesis.client.utils;

import com.google.protobuf.Duration;
import com.google.protobuf.Timestamp;

import java.time.Instant;

/**
 * Utility class for Google Protobuf types.
 */
public final class UapUtils {
    private UapUtils() {
    }

    /**
     * Converts an {@link Instant} to a {@link Timestamp}.
     *
     * @param instant The instant.
     * @return The timestamp.
     */
    public static Timestamp toTimestamp(Instant instant) {
        return Timestamp.newBuilder()
            .setSeconds(instant.getEpochSecond())
            .setNanos(instant.getNano())
            .build();
    }

    /**
     * Creates a {@link Timestamp} for the current time.
     *
     * @return The timestamp.
     */
    public static Timestamp now() {
        return toTimestamp(Instant.now());
    }

    /**
     * Converts milliseconds to a {@link Duration}.
     *
     * @param ms The milliseconds.
     * @return The duration.
     */
    public static Duration msToDuration(long ms) {
        return Duration.newBuilder()
            .setSeconds(ms / 1000)
            .setNanos((int) ((ms % 1000) * 1_000_000))
            .build();
    }
}
