package com.fantacode.vigitv;

/** Persisted OTP/recovery throttling, shared across entry paths and activity restarts. */
final class OtpAttempts {
  final int failures, penalties;
  final long blockedUntil;

  OtpAttempts(int failures, int penalties, long blockedUntil) {
    this.failures = Math.max(0, Math.min(4, failures));
    this.penalties = Math.max(0, Math.min(6, penalties));
    this.blockedUntil = blockedUntil;
  }

  long waitMs(long now) {
    return Math.max(0, Math.min(900_000, blockedUntil - now));
  }

  OtpAttempts result(boolean accepted, long now) {
    if (waitMs(now) > 0) return this;
    if (accepted) return new OtpAttempts(0, 0, 0);
    if (failures < 4) return new OtpAttempts(failures + 1, penalties, 0);
    int next = Math.min(6, penalties + 1);
    return new OtpAttempts(0, next, now + Math.min(900_000, 30_000L << (next - 1)));
  }
}
