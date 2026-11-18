package com.fantacode.vigitv;

import java.util.Arrays;

/** Normalizes a 120-bit Base32 recovery code, without retaining caller-owned input. */
final class RecoveryCode {
  static byte[] normalize(char[] candidate) {
    if (candidate == null || candidate.length > 40) return null;
    byte[] normalized = new byte[24];
    int length = 0;
    for (char ch : candidate) {
      if (ch == ' ' || ch == '-') continue;
      if (ch >= 'a' && ch <= 'z') ch -= 32;
      if (length == 24 || !((ch >= 'A' && ch <= 'Z') || (ch >= '2' && ch <= '7'))) {
        Arrays.fill(normalized, (byte) 0);
        return null;
      }
      normalized[length++] = (byte) ch;
    }
    if (length == 24) return normalized;
    Arrays.fill(normalized, (byte) 0);
    return null;
  }
}
