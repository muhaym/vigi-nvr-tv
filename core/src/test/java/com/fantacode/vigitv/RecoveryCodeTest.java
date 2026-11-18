package com.fantacode.vigitv;

import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import org.junit.Test;

public class RecoveryCodeTest {
  @Test
  public void acceptsGroupedLowercaseWithoutChangingInput() {
    char[] grouped = "abcd efgh ijkl-mnop qrst uv27".toCharArray();
    String original = new String(grouped);
    assertArrayEquals(
        "ABCDEFGHIJKLMNOPQRSTUV27".getBytes(StandardCharsets.US_ASCII),
        RecoveryCode.normalize(grouped));
    assertEquals(original, new String(grouped));
    assertArrayEquals(
        RecoveryCode.normalize(grouped),
        RecoveryCode.normalize("ABCDEFGHIJKLMNOPQRSTUV27".toCharArray()));
  }

  @Test
  public void rejectsWrongLengthConfusableDigitsAndUnicode() {
    assertNull(RecoveryCode.normalize(null));
    for (String bad :
        new String[] {
          "",
          "ABCD",
          "ABCDEFGHIJKLMNOPQRSTUVWXY",
          "ABCDEFGHIJKLMNOPQRSTUV01",
          "ABCDEFGHIJKLMNOPQRSTUV89",
          "ABCDEFGHIJKLMNOPQRSTUV!@",
          "ABCDEFGHIJKLMNOPQRSTUV２７",
          "ABCDEFGHIJKLMNOPQRSTUV\n7",
          "                        ABCDEFGHIJKLMNOPQRSTUV27"
        }) assertNull(RecoveryCode.normalize(bad.toCharArray()));
  }
}
