package utils;

import java.time.Duration;
import java.time.Instant;

public class Stopwatch {

    private Instant start;

    public void start() {
        start = Instant.now();
    }

    public Duration stop() {
        Duration duration = Duration.between(start, Instant.now());
        start = null;
        return duration;
    }
}
