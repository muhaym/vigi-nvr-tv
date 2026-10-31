package com.fantacode.vigitv;

import java.util.*;

/** Unsaved, position-based overview editing. Zero stays an internal empty-cell representation. */
final class CompositeDraft {
  final RecorderProfile original;
  int columns, rows;
  int[] cells;

  CompositeDraft(RecorderProfile profile) {
    original = profile;
    columns = profile.columns;
    rows = profile.rows;
    cells = new int[columns * rows];
    for (RecorderProfile.Camera camera : profile.cameras.values())
      if (camera.cell >= 0 && camera.cell < cells.length) cells[camera.cell] = camera.channel;
  }

  void assign(int cell, int channel) {
    if (cell < 0 || cell >= cells.length || channel != 0 && !original.cameras.containsKey(channel))
      throw new IllegalArgumentException("Choose a camera or Empty");
    if (channel != 0) for (int i = 0; i < cells.length; i++) if (cells[i] == channel) cells[i] = 0;
    cells[cell] = channel;
  }

  int position(int channel) {
    for (int i = 0; i < cells.length; i++) if (cells[i] == channel) return i;
    return -1;
  }

  void resize(int width, int height) {
    if (width < 1 || width > 8 || height < 1 || height > 8)
      throw new IllegalArgumentException("Choose 1–8 rows and columns");
    int[] next = new int[width * height];
    for (int row = 0; row < Math.min(rows, height); row++)
      for (int col = 0; col < Math.min(columns, width); col++)
        next[row * width + col] = cells[row * columns + col];
    columns = width;
    rows = height;
    cells = next;
  }

  void fill() {
    Arrays.fill(cells, 0);
    int i = 0;
    for (int channel : original.cameras.keySet()) {
      if (i == cells.length) break;
      cells[i++] = channel;
    }
  }

  List<Integer> unassigned() {
    List<Integer> result = new ArrayList<>();
    for (int channel : original.cameras.keySet()) if (position(channel) < 0) result.add(channel);
    return result;
  }

  RecorderProfile build() throws Exception {
    StringBuilder order = new StringBuilder();
    for (int id : cells) {
      if (order.length() > 0) order.append(',');
      order.append(id);
    }
    return CompositeMapping.apply(original, columns, rows, order.toString());
  }
}
