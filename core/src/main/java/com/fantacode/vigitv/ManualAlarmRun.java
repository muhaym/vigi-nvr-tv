package com.fantacode.vigitv;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One bounded start attempt, followed by stop even after a timeout, cancellation or lost UI auth.
 */
final class ManualAlarmRun {
  interface Gateway {
    void alarm(int channel, boolean start) throws Exception;
  }

  interface Clock {
    long now();
  }

  interface Waiter {
    void pause(long milliseconds) throws InterruptedException;
  }

  interface Guard {
    boolean allowed();
  }

  interface Progress {
    void remaining(int seconds);
  }

  static final class Result {
    boolean attempted, started, stopped;
    Exception failure;
  }

  static Result execute(
      Gateway gateway,
      int channel,
      long duration,
      AtomicBoolean cancel,
      Guard guard,
      Clock clock,
      Waiter waiter,
      Progress progress) {
    AlarmProtocol.wireChannel(channel);
    if (duration <= 0 || duration > 10000)
      throw new IllegalArgumentException("Manual alarm duration must be at most ten seconds");
    Result result = new Result();
    boolean interrupted = false;
    try {
      if (cancel.get() || !guard.allowed()) return result;
      long end = clock.now() + duration;
      result.attempted = true;
      gateway.alarm(channel, true);
      result.started = true;
      int previous = -1;
      while (!cancel.get() && guard.allowed() && clock.now() < end) {
        int seconds = (int) ((end - clock.now() + 999) / 1000);
        if (seconds != previous) {
          progress.remaining(seconds);
          previous = seconds;
        }
        waiter.pause(Math.min(100, Math.max(1, end - clock.now())));
      }
    } catch (InterruptedException error) {
      interrupted = true;
      result.failure = error;
    } catch (Exception error) {
      result.failure = error;
    } finally {
      if (result.attempted) result.stopped = stop(gateway, channel);
      if (interrupted) Thread.currentThread().interrupt();
    }
    return result;
  }

  static boolean stop(Gateway gateway, int channel) {
    AlarmProtocol.wireChannel(channel);
    for (int attempt = 0; attempt < 3; attempt++)
      try {
        gateway.alarm(channel, false);
        return true;
      } catch (Exception ignored) {
      }
    return false;
  }
}
