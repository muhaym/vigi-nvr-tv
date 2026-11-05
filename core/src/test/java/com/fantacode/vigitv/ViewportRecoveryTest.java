package com.fantacode.vigitv;

import static org.junit.Assert.*;

import org.junit.Test;

public class ViewportRecoveryTest {
  @Test
  public void missingDecoderLayoutStillAllowsAllFourPanDirections() {
    DigitalViewport v = new DigitalViewport();
    v.size(1920, 1080, 0, 0);
    v.zoom(1);
    v.pan(1, 1);
    assertTrue(v.x < 0);
    assertTrue(v.y < 0);
    v.pan(-1, -1);
    assertEquals(0f, v.x, .01f);
    assertEquals(0f, v.y, .01f);
    v.pan(-1, -1);
    assertTrue(v.x > 0);
    assertTrue(v.y > 0);
    for (int i = 0; i < 100; i++) v.pan(-1, -1);
    assertEquals(480f, v.x, .01f);
    assertEquals(270f, v.y, .01f);
  }

  @Test
  public void lateMetadataAndResizeClampTheExistingPosition() {
    DigitalViewport v = new DigitalViewport();
    v.zoom(1);
    v.size(1920, 1080, 0, 0);
    for (int i = 0; i < 100; i++) v.pan(1, 1);
    v.size(1920, 1080, 1440, 1080);
    assertEquals(-120f, v.x, .01f);
    assertEquals(-270f, v.y, .01f);
    v.zoom(-1);
    assertEquals(0f, v.x, .01f);
    assertEquals(0f, v.y, .01f);
    v.size(0, 0, 0, 0);
    v.pan(1, 1);
    assertFalse(Float.isNaN(v.x));
    assertFalse(Float.isNaN(v.y));
  }
}
