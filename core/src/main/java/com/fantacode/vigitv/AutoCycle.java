package com.fantacode.vigitv;

/** Monotonic, foreground-only page rotation. Pausing always rearms a full interval. */
final class AutoCycle {
  int seconds;
  long due;

  static boolean supported(int seconds) {
    return seconds == 0 || seconds == 5 || seconds == 15 || seconds == 30 || seconds == 60;
  }

  void configure(int value, long now) {
    if (!supported(value)) throw new IllegalArgumentException("Unsupported cycle interval");
    seconds = value;
    defer(now);
  }

  void defer(long now) {
    due = now + seconds * 1000L;
  }

  boolean ready(long now, boolean eligible, int pages) {
    if (seconds == 0 || !eligible || pages < 2) {
      defer(now);
      return false;
    }
    if (now < due) return false;
    defer(now);
    return true;
  }
}
