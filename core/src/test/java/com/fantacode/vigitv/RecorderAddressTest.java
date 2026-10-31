package com.fantacode.vigitv;

import static org.junit.Assert.*;

import org.junit.Test;

public class RecorderAddressTest {
  @Test
  public void plainHostPreservesConfiguredPort() {
    RecorderAddress result = RecorderAddress.parse("  NVR.local  ", 9443);
    assertEquals("nvr.local", result.host);
    assertEquals(9443, result.https);
  }

  @Test
  public void browserAddressAndFragmentAreAccepted() {
    RecorderAddress result = RecorderAddress.parse("https://nvr.local/#/", 443);
    assertEquals("nvr.local", result.host);
    assertEquals(443, result.https);
  }

  @Test
  public void explicitPortOverridesDefault() {
    assertEquals(9443, RecorderAddress.parse("nvr.local:9443", 443).https);
    assertEquals(9443, RecorderAddress.parse("https://nvr.local:9443/", 443).https);
  }

  @Test
  public void unsafeOrAmbiguousAddressesAreRejectedWithoutEchoingInput() {
    for (String value :
        new String[] {
          null,
          "",
          "http://nvr.local",
          "https://name:secret@nvr.local",
          "nvr.local/path",
          "nvr.local?token=secret",
          "nvr.local/#/settings",
          "nvr.local:0",
          "nvr.local:65536",
          "https:///",
          "https://nvr.local:bad"
        }) {
      try {
        RecorderAddress.parse(value, 443);
        fail("Expected rejection");
      } catch (IllegalArgumentException expected) {
        assertFalse(expected.getMessage().contains("secret"));
      }
    }
  }
}
