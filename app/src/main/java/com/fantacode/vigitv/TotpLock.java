package com.fantacode.vigitv;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Device-local authenticator storage; never sends or logs enrollment material. */
final class TotpLock {
  private static final String ALIAS = "vigi-tv-authenticator";

  static boolean configured(Context c) {
    // Fail closed if encrypted material is damaged; never fall back to the pattern.
    return "totp".equals(AppLock.prefs(c).getString("method", ""));
  }

  static byte[] random(int size) {
    byte[] bytes = new byte[size];
    new SecureRandom().nextBytes(bytes);
    return bytes;
  }

  static String newRecovery() {
    byte[] random = random(15);
    try {
      return Totp.base32(random);
    } finally {
      Arrays.fill(random, (byte) 0);
    }
  }

  static synchronized void activate(Context c, byte[] secret, String recovery, long step)
      throws Exception {
    if (secret.length != 20 || recovery.length() != 24 || step < 0)
      throw new IllegalArgumentException("Unverified enrollment");
    KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
    ks.load(null);
    if (!ks.containsAlias(ALIAS)) {
      KeyGenerator generator = KeyGenerator.getInstance("AES", "AndroidKeyStore");
      generator.init(
          new KeyGenParameterSpec.Builder(
                  ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
              .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
              .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
              .build());
      generator.generateKey();
    }
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(Cipher.ENCRYPT_MODE, (SecretKey) ks.getKey(ALIAS, null));
    cipher.updateAAD(ALIAS.getBytes(StandardCharsets.US_ASCII));
    byte[] encrypted = cipher.doFinal(secret);
    char[] recoveryChars = recovery.toCharArray();
    byte[] hash;
    try {
      hash = recoveryHash(recoveryChars);
    } finally {
      Arrays.fill(recoveryChars, '\0');
    }
    try {
      if (!AppLock.prefs(c)
          .edit()
          .putString("method", "totp")
          .putString("otp_secret", Base64.encodeToString(encrypted, Base64.NO_WRAP))
          .putString("otp_iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
          .putString("otp_recovery", Base64.encodeToString(hash, Base64.NO_WRAP))
          .putLong("otp_last", step)
          .putInt("otp_failures", 0)
          .putInt("otp_penalties", 0)
          .putLong("otp_blocked", 0)
          .remove("verifier")
          .remove("salt")
          .remove("attempts")
          .remove("blockedUntil")
          .commit()) throw new IllegalStateException("Could not save authenticator");
    } finally {
      Arrays.fill(encrypted, (byte) 0);
      Arrays.fill(hash, (byte) 0);
    }
  }

  private static byte[] secret(Context c) throws Exception {
    SharedPreferences p = AppLock.prefs(c);
    KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
    ks.load(null);
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(
        Cipher.DECRYPT_MODE,
        (SecretKey) ks.getKey(ALIAS, null),
        new GCMParameterSpec(128, Base64.decode(p.getString("otp_iv", ""), Base64.NO_WRAP)));
    cipher.updateAAD(ALIAS.getBytes(StandardCharsets.US_ASCII));
    return cipher.doFinal(Base64.decode(p.getString("otp_secret", ""), Base64.NO_WRAP));
  }

  static synchronized boolean verify(Context c, char[] candidate) throws Exception {
    return check(c, candidate) == Totp.Verdict.ACCEPTED;
  }

  static synchronized Totp.Verdict check(Context c, char[] candidate) throws Exception {
    if (!configured(c) || waitMs(c) > 0) return Totp.Verdict.INVALID;
    byte[] secret = secret(c);
    Totp.Result checked;
    try {
      checked =
          Totp.check(
              secret,
              candidate,
              System.currentTimeMillis(),
              AppLock.prefs(c).getLong("otp_last", -1));
    } finally {
      Arrays.fill(secret, (byte) 0);
    }
    // A consumed code remains rejected, without penalizing a quick relock.
    if (checked.verdict == Totp.Verdict.USED) return checked.verdict;
    boolean accepted = checked.verdict == Totp.Verdict.ACCEPTED;
    SharedPreferences.Editor edit = result(c, accepted);
    if (accepted) edit.putLong("otp_last", checked.step);
    if (!edit.commit()) throw new IllegalStateException("Could not record attempt");
    return checked.verdict;
  }

  static synchronized boolean recover(Context c, char[] candidate) throws Exception {
    if (!configured(c) || waitMs(c) > 0) return false;
    String saved = AppLock.prefs(c).getString("otp_recovery", "");
    byte[] supplied = recoveryHash(candidate);
    boolean accepted;
    try {
      accepted =
          !saved.isEmpty() && MessageDigest.isEqual(supplied, Base64.decode(saved, Base64.NO_WRAP));
    } finally {
      Arrays.fill(supplied, (byte) 0);
    }
    SharedPreferences.Editor edit = result(c, accepted);
    // Retire this code atomically with the replacement in activate(). An interrupted
    // setup must not strand an owner whose original authenticator was lost.
    if (!edit.commit()) throw new IllegalStateException("Could not record recovery");
    return accepted;
  }

  private static byte[] recoveryHash(char[] candidate) throws Exception {
    byte[] normalized = RecoveryCode.normalize(candidate);
    if (normalized == null) return new byte[0];
    try {
      return AppLock.seal(normalized);
    } finally {
      Arrays.fill(normalized, (byte) 0);
    }
  }

  static OtpAttempts attempts(Context c) {
    SharedPreferences p = AppLock.prefs(c);
    return new OtpAttempts(
        p.getInt("otp_failures", 0), p.getInt("otp_penalties", 0), p.getLong("otp_blocked", 0));
  }

  static long waitMs(Context c) {
    return attempts(c).waitMs(System.currentTimeMillis());
  }

  static SharedPreferences.Editor result(Context c, boolean accepted) {
    OtpAttempts next = attempts(c).result(accepted, System.currentTimeMillis());
    return AppLock.prefs(c)
        .edit()
        .putInt("otp_failures", next.failures)
        .putInt("otp_penalties", next.penalties)
        .putLong("otp_blocked", next.blockedUntil);
  }
}
