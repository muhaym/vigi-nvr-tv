package com.fantacode.vigitv;

import static org.junit.Assert.*;

import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.Decoder;
import com.google.zxing.qrcode.detector.Detector;
import org.junit.Test;

public class EnrollmentQrTest {
  @Test
  public void actualSetupQrDecodesToStandardAuthenticatorUri() throws Exception {
    byte[] synthetic = new byte[20];
    for (int i = 0; i < synthetic.length; i++) synthetic[i] = (byte) (i * 13);
    BitMatrix qr = EnrollmentQr.create(synthetic, "Test TV & lobby");
    assertEquals(488, qr.getWidth());
    assertEquals(488, qr.getHeight());
    String decoded = new Decoder().decode(new Detector(qr).detect().getBits()).getText();
    assertEquals(Totp.enrollmentUri(synthetic, "Test TV & lobby"), decoded);
    assertTrue(decoded.contains("digits=6&period=30"));
    assertFalse(qr.get(0, 0));
  }
}
