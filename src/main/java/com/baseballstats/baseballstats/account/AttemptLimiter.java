package com.baseballstats.baseballstats.account;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Slows down password guessing: after too many attempts for the same key (a username or
 * an IP address) inside a time window, further attempts are refused until the window passes.
 * Kept in memory, which is fine for a single server.
 */
public class AttemptLimiter {

    private final int maxAttempts;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Deque<Long>> attempts = new ConcurrentHashMap<>();

    public AttemptLimiter(int maxAttempts, Duration window, Clock clock) {
        this.maxAttempts = maxAttempts;
        this.window = window;
        this.clock = clock;
    }

    /** True if this key has used up its attempts for now. */
    public boolean isBlocked(String key) {
        Deque<Long> times = attempts.get(key);
        if (times == null) return false;
        synchronized (times) {
            prune(times);
            return times.size() >= maxAttempts;
        }
    }

    public void record(String key) {
        if (attempts.size() > 50_000) attempts.clear(); // never grow without limit
        Deque<Long> times = attempts.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (times) {
            prune(times);
            times.addLast(clock.millis());
        }
    }

    public void reset(String key) {
        attempts.remove(key);
    }

    private void prune(Deque<Long> times) {
        long cutoff = clock.millis() - window.toMillis();
        while (!times.isEmpty() && times.peekFirst() < cutoff) times.removeFirst();
    }
}
