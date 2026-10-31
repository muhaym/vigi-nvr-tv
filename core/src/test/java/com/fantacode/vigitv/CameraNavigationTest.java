package com.fantacode.vigitv;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

public class CameraNavigationTest {
  @Test
  public void followsSelectedGroupOrderAndWraps() {
    List<Integer> group = Arrays.asList(7, 2, 96);
    assertEquals(2, CameraNavigation.adjacent(group, 7, 1));
    assertEquals(96, CameraNavigation.adjacent(group, 7, -1));
    assertEquals(7, CameraNavigation.adjacent(group, 96, 1));
    assertEquals(7, CameraNavigation.adjacent(group, 2, -1));
  }

  @Test
  public void emptySingleAndMissingSelections() {
    assertEquals(0, CameraNavigation.adjacent(Collections.emptyList(), 1, 1));
    assertEquals(4, CameraNavigation.adjacent(Arrays.asList(4), 4, -1));
    assertEquals(7, CameraNavigation.adjacent(Arrays.asList(7, 9), 3, 1));
    assertEquals(9, CameraNavigation.adjacent(Arrays.asList(7, 9), 3, -1));
  }

  @Test
  public void invalidDirectionIsRejected() {
    try {
      CameraNavigation.adjacent(Arrays.asList(1), 1, 0);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }
}
