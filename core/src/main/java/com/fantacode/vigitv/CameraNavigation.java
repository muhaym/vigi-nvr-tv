package com.fantacode.vigitv;

import java.util.List;

/** Manual camera changes follow the selected group's order, including sparse channel identities. */
final class CameraNavigation {
  static int adjacent(List<Integer> cameras, int current, int direction) {
    if (direction != 1 && direction != -1)
      throw new IllegalArgumentException("Camera direction must be left or right");
    if (cameras.isEmpty()) return 0;
    int index = cameras.indexOf(current);
    if (index < 0) return cameras.get(direction > 0 ? 0 : cameras.size() - 1);
    return cameras.get(Math.floorMod(index + direction, cameras.size()));
  }
}
