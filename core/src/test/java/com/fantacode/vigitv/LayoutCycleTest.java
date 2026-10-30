package com.fantacode.vigitv;

import static org.junit.Assert.*;

import org.junit.Test;

public class LayoutCycleTest {
  @Test
  public void fixedLayoutsDoNotDependOnLastPageOccupancy() {
    for (int size : ViewLayout.SIZES) {
      assertEquals(size, ViewLayout.columns(size) * ViewLayout.rows(size));
      for (int index = 0; index < size; index++) {
        ViewLayout.Cell cell = ViewLayout.cell(size, index, 1920, 1080, 12);
        assertTrue(cell.x >= 0 && cell.y >= 0 && cell.width > 0 && cell.height > 0);
        assertTrue(cell.x + cell.width <= 1920.01f);
        assertTrue(cell.y + cell.height <= 1080.01f);
        if (index % ViewLayout.columns(size) > 0) {
          ViewLayout.Cell previous = ViewLayout.cell(size, index - 1, 1920, 1080, 12);
          assertEquals(12, cell.x - (previous.x + previous.width), .01f);
        }
      }
    }
    assertEquals(2, ViewLayout.pages(11, 8));
    assertEquals(1, ViewLayout.pages(11, 16));
    assertEquals(11, ViewLayout.pages(11, 1));
    assertEquals(0, ViewLayout.pages(0, 4));
  }

  @Test
  public void legacyLayoutsMigrateToSupportedSizes() {
    assertEquals(4, ViewLayout.normalize(2));
    assertEquals(8, ViewLayout.normalize(6));
    assertEquals(16, ViewLayout.normalize(12));
    assertEquals(1, ViewLayout.normalize(-1));
  }

  @Test
  public void cyclePausesAndRequiresAWholeIntervalAfterUserActivity() {
    AutoCycle cycle = new AutoCycle();
    cycle.configure(15, 1000);
    assertFalse(cycle.ready(15999, true, 3));
    assertTrue(cycle.ready(16000, true, 3));
    assertFalse(cycle.ready(17000, true, 3));
    cycle.defer(20000);
    assertFalse(cycle.ready(34999, true, 3));
    assertTrue(cycle.ready(35000, true, 3));
    assertFalse(cycle.ready(100000, false, 3));
    assertFalse(cycle.ready(114999, true, 3));
    assertTrue(cycle.ready(115000, true, 3));
  }

  @Test
  public void fiveSecondCycleHonorsPauseAndUserInteraction() {
    AutoCycle cycle = new AutoCycle();
    cycle.configure(5, 0);
    assertFalse(cycle.ready(4999, true, 11));
    assertTrue(cycle.ready(5000, true, 11));
    cycle.defer(6000);
    assertFalse(cycle.ready(10999, true, 11));
    assertTrue(cycle.ready(11000, true, 11));
    assertFalse(cycle.ready(20000, false, 11));
    assertFalse(cycle.ready(24999, true, 11));
    assertTrue(cycle.ready(25000, true, 11));
  }

  @Test
  public void offAndSinglePageNeverAdvance() {
    AutoCycle cycle = new AutoCycle();
    cycle.configure(0, 0);
    assertFalse(cycle.ready(999999, true, 16));
    cycle.configure(15, 0);
    assertFalse(cycle.ready(999999, true, 1));
    assertFalse(cycle.ready(1000000, true, 0));
  }

  @Test
  public void invalidSettingsAreRejected() {
    try {
      new AutoCycle().configure(-1, 0);
      fail();
    } catch (IllegalArgumentException expected) {
    }
    try {
      ViewLayout.cell(6, 0, 1920, 1080, 8);
      fail();
    } catch (IllegalArgumentException expected) {
    }
    try {
      ViewLayout.cell(4, 4, 1920, 1080, 8);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }
}
