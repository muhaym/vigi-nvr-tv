package com.fantacode.vigitv;

/** Fixed NVR-style layouts, independent of camera count and Android views. */
final class ViewLayout {
  static final int[] SIZES = {1, 4, 8, 16};

  static boolean supported(int size) {
    return size == 1 || size == 4 || size == 8 || size == 16;
  }

  static int normalize(int old) {
    return old <= 1 ? 1 : old <= 4 ? 4 : old <= 8 ? 8 : 16;
  }

  static int columns(int size) {
    if (!supported(size)) throw new IllegalArgumentException("Unsupported camera layout");
    return size == 1 ? 1 : size == 4 ? 2 : 4;
  }

  static int rows(int size) {
    return size / columns(size);
  }

  static int pages(int count, int size) {
    columns(size);
    return count <= 0 ? 0 : (count + size - 1) / size;
  }

  static final class Cell {
    final float x, y, width, height;

    Cell(float left, float top, float w, float h) {
      x = left;
      y = top;
      width = w;
      height = h;
    }
  }

  static Cell cell(int size, int index, float width, float height, float gap) {
    int columns = columns(size), rows = rows(size);
    if (index < 0 || index >= size || width < 0 || height < 0 || gap < 0)
      throw new IllegalArgumentException("Invalid grid bounds");
    float w = Math.max(0, (width - gap * (columns - 1)) / columns),
        h = Math.max(0, (height - gap * (rows - 1)) / rows);
    return new Cell(index % columns * (w + gap), index / columns * (h + gap), w, h);
  }
}
