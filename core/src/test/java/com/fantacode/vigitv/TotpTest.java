package com.fantacode.vigitv;

import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import org.junit.Test;

public class TotpTest {
  private final byte[] key = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

  @Test
  public void rfc6238Sha1VectorsIncludingBeyond2038() throws Exception {
    long[] times = {59, 1111111109, 1111111111, 1234567890, 2000000000, 20000000000L};
    String[] expected = {"94287082", "07081804", "14050471", "89005924", "69279037", "65353130"};
    for (int i = 0; i < times.length; i++)
      assertEquals(expected[i], Totp.code(key, times[i] / 30, 8));
  }

  @Test
  public void rfc4226SixDigitCountersIncludingLeadingZero() throws Exception {
    String[] expected = {
      "755224", "287082", "359152", "969429", "338314", "254676", "287922", "162583", "399871",
      "520489"
    };
    for (int i = 0; i < expected.length; i++) assertEquals(expected[i], Totp.code(key, i, 6));
  }

  @Test
  public void acceptsTwoAdjacentWindowsAndNeverReusesConsumedStep() throws Exception {
    long now = 1234567890000L, step = now / 30000;
    for (long offset = -3; offset <= 3; offset++) {
      char[] code = Totp.code(key, step + offset, 6).toCharArray();
      assertEquals(Math.abs(offset) <= 2 ? step + offset : -1, Totp.match(key, code, now, -1));
      assertEquals(-1, Totp.match(key, code, now, step + 2));
    }
    assertEquals(-1, Totp.match(key, Totp.code(key, step, 6).toCharArray(), now - 30000, step));
  }

  @Test
  public void distinguishesConsumedCodesFromIncorrectCodes() throws Exception {
    long now = 1234567890000L, step = now / 30000;
    for (int offset = -2; offset <= 2; offset++) {
      char[] candidate = Totp.code(key, step + offset, 6).toCharArray();
      assertEquals(Totp.Verdict.ACCEPTED, Totp.check(key, candidate, now, -1).verdict);
      Totp.Result used = Totp.check(key, candidate, now, step + 2);
      assertEquals(Totp.Verdict.USED, used.verdict);
      assertEquals(step + offset, used.step);
    }
    assertEquals(Totp.Verdict.INVALID, Totp.check(key, "abcdef".toCharArray(), now, -1).verdict);
    assertEquals(
        Totp.Verdict.INVALID,
        Totp.check(key, Totp.code(key, step - 3, 6).toCharArray(), now, -1).verdict);
    assertEquals(0, Totp.match(key, Totp.code(key, 0, 6).toCharArray(), 0, -1));
  }

  @Test
  public void rejectsMalformedCodesAndInvalidParameters() throws Exception {
    for (String bad : new String[] {"", "12345", "1234567", "１２３４５６", "123 45", "ABCDEF"})
      assertEquals(-1, Totp.match(key, bad.toCharArray(), 59000, -1));
    assertEquals(-1, Totp.match(key, null, 59000, -1));
    assertEquals(-1, Totp.match(key, "287082".toCharArray(), -1, -1));
    for (int digits : new int[] {0, 5, 7, 9}) {
      try {
        Totp.code(key, 1, digits);
        fail();
      } catch (IllegalArgumentException expected) {
      }
    }
    try {
      Totp.code(new byte[10], 1, 6);
      fail();
    } catch (IllegalArgumentException expected) {
    }
    try {
      Totp.code(null, 1, 6);
      fail();
    } catch (IllegalArgumentException expected) {
    }
    try {
      Totp.code(key, -1, 6);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void standardBase32AndEscapedProvisioningUri() throws Exception {
    assertEquals("", Totp.base32(new byte[0]));
    assertEquals("MY", Totp.base32(new byte[] {'f'}));
    assertEquals("MZXW6YTBOI", Totp.base32("foobar".getBytes(StandardCharsets.US_ASCII)));
    String uri = Totp.enrollmentUri(key, "Office TV & 2");
    assertEquals(
        "otpauth://totp/VIGI%20NVR%20TV:Office%20TV%20%26%202?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&issuer=VIGI%20NVR%20TV&algorithm=SHA1&digits=6&period=30",
        uri);
    try {
      Totp.enrollmentUri(new byte[19], "TV");
      fail();
    } catch (IllegalArgumentException expected) {
    }
    try {
      Totp.enrollmentUri(null, "TV");
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void throttlesAcrossAttemptsAndCapsEscalatingWait() {
    OtpAttempts state = new OtpAttempts(0, 0, 0);
    long now = 100000;
    for (int cycle = 0; cycle < 9; cycle++) {
      for (int attempt = 0; attempt < 5; attempt++) state = state.result(false, now);
      assertEquals(Math.min(900000, 30000L << Math.min(5, cycle)), state.waitMs(now));
      assertSame(state, state.result(true, now));
      assertSame(state, state.result(false, now));
      now = state.blockedUntil;
      assertEquals(0, state.waitMs(now));
    }
    state = state.result(true, now);
    assertEquals(0, state.failures);
    assertEquals(0, state.penalties);
    assertEquals(0, state.waitMs(now));
    assertEquals(900000, new OtpAttempts(99, 99, now + 999999).waitMs(now));
    assertEquals(0, new OtpAttempts(-1, -1, 0).failures);
  }
}
