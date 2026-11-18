package com.fantacode.vigitv;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.util.Collections;

/** Offline QR generation; never depends on a remote QR/image service. */
final class EnrollmentQr {
  static BitMatrix create(byte[] secret, String label) throws Exception {
    return new QRCodeWriter()
        .encode(
            Totp.enrollmentUri(secret, label),
            BarcodeFormat.QR_CODE,
            488,
            488,
            Collections.singletonMap(EncodeHintType.MARGIN, 4));
  }
}
