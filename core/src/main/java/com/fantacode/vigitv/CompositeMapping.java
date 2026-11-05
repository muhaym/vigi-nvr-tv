package com.fantacode.vigitv;

import java.util.*;

/** Validates explicit composite cells before any profile is saved or a larger layout is enabled. */
final class CompositeMapping {
  static RecorderProfile apply(RecorderProfile original, int columns, int rows, String order)
      throws Exception {
    if (columns < 1 || columns > 8 || rows < 1 || rows > 8)
      throw new IllegalArgumentException("Grid size must be 1–8");
    String[] ids = order.split(",", -1);
    if (ids.length != columns * rows)
      throw new IllegalArgumentException("Enter one channel ID for every grid cell");
    RecorderProfile result = RecorderProfile.parse(original.json());
    Set<Integer> used = new HashSet<>();
    for (RecorderProfile.Camera camera : result.cameras.values()) camera.cell = -1;
    for (int cell = 0; cell < ids.length; cell++) {
      int channel = Integer.parseInt(ids[cell].trim());
      if (channel == 0) continue;
      if (!result.cameras.containsKey(channel) || !used.add(channel))
        throw new IllegalArgumentException("Channel IDs must exist and appear only once");
      result.cameras.get(channel).cell = cell;
    }
    if (!used.containsAll(result.cameras.keySet()))
      throw new IllegalArgumentException("Map every camera before enabling combined preview");
    result.columns = columns;
    result.rows = rows;
    result.combined = true;
    result.overviewNeedsReview = false;
    return result;
  }
}
