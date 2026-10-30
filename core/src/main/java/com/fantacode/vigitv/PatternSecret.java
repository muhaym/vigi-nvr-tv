package com.fantacode.vigitv;

import java.security.MessageDigest;
import java.util.HashSet;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Pattern validation and slow derivation; no raw pattern is persisted. */
final class PatternSecret {
  static boolean valid(char[] pattern) {
    if (pattern == null || pattern.length < 4 || pattern.length > 9) return false;
    HashSet<Character> used = new HashSet<>();
    for (char point : pattern) if (point < '0' || point > '8' || !used.add(point)) return false;
    return true;
  }

  static byte[] derive(char[] pattern, byte[] salt) throws Exception {
    PBEKeySpec spec = new PBEKeySpec(pattern, salt, 120000, 256);
    try {
      return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
    } finally {
      spec.clearPassword();
    }
  }

  static boolean equal(byte[] a, byte[] b) {
    return MessageDigest.isEqual(a, b);
  }
}
