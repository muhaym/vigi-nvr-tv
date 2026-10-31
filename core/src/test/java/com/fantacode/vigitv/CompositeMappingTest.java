package com.fantacode.vigitv;

import static org.junit.Assert.*;

import org.junit.Test;

public class CompositeMappingTest {
  RecorderProfile profile() {
    RecorderProfile p = new RecorderProfile();
    p.host = "nvr.local";
    p.cameras.put(2, new RecorderProfile.Camera(2, "Entry", -1, false));
    p.cameras.put(7, new RecorderProfile.Camera(7, "Hall", -1, false));
    return p;
  }

  @Test
  public void explicitSparseMappingKeepsCameraIdentity() throws Exception {
    RecorderProfile original = profile();
    RecorderProfile mapped = CompositeMapping.apply(original, 2, 2, "7,0,2,0");
    assertTrue(mapped.combined);
    assertEquals(0, mapped.cameras.get(7).cell);
    assertEquals(2, mapped.cameras.get(2).cell);
    assertFalse(original.combined);
    assertEquals(-1, original.cameras.get(2).cell);
    RecorderProfile.parse(mapped.json());
  }

  @Test
  public void invalidMappingsCannotEnableLargeLayouts() throws Exception {
    for (String order : new String[] {"2,7", "2,2,7,0", "2,9,7,0", "2,0,0,0", "2,7,0,x"}) {
      RecorderProfile original = profile();
      try {
        CompositeMapping.apply(original, 2, 2, order);
        fail(order);
      } catch (Exception expected) {
      }
      assertFalse(original.combined);
      assertEquals(-1, original.cameras.get(7).cell);
    }
  }

  @Test
  public void rejectsInvalidGridBounds() throws Exception {
    for (int size : new int[] {0, 9})
      try {
        CompositeMapping.apply(profile(), size, 2, "2,7");
        fail();
      } catch (IllegalArgumentException expected) {
      }
  }
}
