package com.fantacode.vigitv;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.*;
import org.junit.Test;

public class PlayerCleanupTest {
  @Test
  public void cleanupAlwaysCompletesInOrder() {
    for (int failure = 0; failure < 4; failure++) {
      final int mode = failure;
      List<String> order = new ArrayList<>();
      PlayerCleanup.run(
          () -> {
            order.add("stop");
            if ((mode & 1) != 0) throw new IOException();
          },
          () -> {
            order.add("release");
            if ((mode & 2) != 0) throw new IllegalStateException();
          },
          () -> order.add("complete"));
      assertEquals(Arrays.asList("stop", "release", "complete"), order);
    }
  }

  @Test
  public void fatalStopStillReleasesAndCompletes() {
    List<String> order = new ArrayList<>();
    try {
      PlayerCleanup.run(
          () -> {
            throw new AssertionError("fatal");
          },
          () -> order.add("release"),
          () -> order.add("complete"));
      fail();
    } catch (AssertionError expected) {
      assertEquals(Arrays.asList("release", "complete"), order);
    }
  }
}
