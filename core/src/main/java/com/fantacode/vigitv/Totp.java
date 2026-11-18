package com.fantacode.vigitv;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** RFC 6238 / RFC 4226: SHA-1, six digits, 30 seconds. No network or clock adjustment. */
final class Totp {
  static final long PERIOD_MS = 30_000;
  static final int WINDOW_STEPS = 2;

  enum Verdict {
    ACCEPTED,
    USED,
    INVALID
  }

  static final class Result {
    final Verdict verdict;
    final long step;

    Result(Verdict verdict, long step) {
      this.verdict = verdict;
      this.step = step;
    }
  }

  private static final char[] BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();

  static String code(byte[] secret, long step, int digits) throws Exception {
    if (secret == null || secret.length < 20 || step < 0 || (digits != 6 && digits != 8))
      throw new IllegalArgumentException("Invalid OTP parameters");
    Mac mac = Mac.getInstance("HmacSHA1");
    mac.init(new SecretKeySpec(secret, "HmacSHA1"));
    byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
    try {
      int offset = hash[hash.length - 1] & 15;
      int value =
          ((hash[offset] & 127) << 24)
              | ((hash[offset + 1] & 255) << 16)
              | ((hash[offset + 2] & 255) << 8)
              | (hash[offset + 3] & 255);
      return String.format(
          Locale.ROOT,
          digits == 6 ? "%06d" : "%08d",
          value % (digits == 6 ? 1_000_000 : 100_000_000));
    } finally {
      Arrays.fill(hash, (byte) 0);
    }
  }

  /** Returns the matched counter, or -1. A used counter can never authorize again. */
  static long match(byte[] secret, char[] candidate, long nowMs, long lastAccepted)
      throws Exception {
    Result result = check(secret, candidate, nowMs, lastAccepted);
    return result.verdict == Verdict.ACCEPTED ? result.step : -1;
  }

  static Result check(byte[] secret, char[] candidate, long nowMs, long lastAccepted)
      throws Exception {
    long matched = matchingStep(secret, candidate, nowMs);
    return new Result(
        matched < 0 ? Verdict.INVALID : matched <= lastAccepted ? Verdict.USED : Verdict.ACCEPTED,
        matched);
  }

  private static long matchingStep(byte[] secret, char[] candidate, long nowMs) throws Exception {
    if (candidate == null || candidate.length != 6 || nowMs < 0) return -1;
    for (char c : candidate) if (c < '0' || c > '9') return -1;
    byte[] supplied = new byte[6];
    for (int i = 0; i < 6; i++) supplied[i] = (byte) candidate[i];
    long matched = -1, current = nowMs / PERIOD_MS;
    try {
      // Compare the whole sliding window; never change the TV clock or learn unbounded drift.
      for (long step = Math.max(0, current - WINDOW_STEPS);
          step <= current + WINDOW_STEPS;
          step++) {
        byte[] expected = code(secret, step, 6).getBytes(StandardCharsets.US_ASCII);
        boolean equal = MessageDigest.isEqual(expected, supplied);
        Arrays.fill(expected, (byte) 0);
        if (equal) matched = step;
      }
      return matched;
    } finally {
      Arrays.fill(supplied, (byte) 0);
    }
  }

  static String base32(byte[] bytes) {
    StringBuilder out = new StringBuilder();
    int buffer = 0, bits = 0;
    for (byte b : bytes) {
      buffer = (buffer << 8) | (b & 255);
      bits += 8;
      while (bits >= 5) {
        bits -= 5;
        out.append(BASE32[(buffer >>> bits) & 31]);
      }
    }
    if (bits > 0) out.append(BASE32[(buffer << (5 - bits)) & 31]);
    return out.toString();
  }

  static String enrollmentUri(byte[] secret, String label) throws Exception {
    if (secret == null || secret.length != 20) throw new IllegalArgumentException("Invalid key");
    String issuer = "VIGI NVR TV";
    return "otpauth://totp/"
        + encode(issuer)
        + ":"
        + encode(label)
        + "?secret="
        + base32(secret)
        + "&issuer="
        + encode(issuer)
        + "&algorithm=SHA1&digits=6&period=30";
  }

  private static String encode(String text) throws Exception {
    return URLEncoder.encode(text, "UTF-8").replace("+", "%20");
  }
}
