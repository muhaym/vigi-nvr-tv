package com.fantacode.vigitv;

public final class DigitalViewportSelfTest {
  static void check(boolean value, String label) {
    if (!value) throw new AssertionError(label);
  }

  static void near(float actual, float expected, String label) {
    check(Math.abs(actual - expected) < .01f, label + ": " + actual);
  }

  public static void main(String[] args) {
    DigitalViewport view = new DigitalViewport();
    view.size(1920, 1080, 1920, 1080);
    view.pan(1, 1);
    near(view.x, 0, "no movement at 1x");
    near(view.y, 0, "no movement at 1x");
    view.zoom(1);
    near(view.scale(), 1.5f, "first zoom step");
    view.pan(1, 1);
    check(view.x < 0 && view.y < 0, "right/down reveals corresponding image region");
    float oldX = view.x;
    view.zoom(1);
    near(view.x, oldX * 2 / 1.5f, "zoom preserves focal point");
    for (int i = 0; i < 100; i++) view.pan(1, 1);
    near(view.x, -960, "right image edge clamps");
    near(view.y, -540, "bottom image edge clamps");
    for (int i = 0; i < 200; i++) view.pan(-1, -1);
    near(view.x, 960, "left image edge clamps");
    near(view.y, 540, "top image edge clamps");
    view.zoom(-1);
    near(view.x, 480, "zoom out clamps horizontal edge");
    near(view.y, 270, "zoom out clamps vertical edge");
    for (int i = 0; i < 20; i++) view.zoom(1);
    near(view.scale(), 4, "upper zoom limit");
    for (int i = 0; i < 20; i++) view.zoom(-1);
    near(view.scale(), 1, "lower zoom limit");
    near(view.x, 0, "position returns at 1x");
    view.size(1920, 1080, 1440, 1080);
    view.zoom(1);
    near(view.maxX(), 120, "4:3 letterbox horizontal bounds");
    near(view.maxY(), 270, "4:3 vertical bounds");
    view.size(1080, 1920, 1080, 608);
    near(view.maxY(), 0, "short image cannot pan into blank space");
    view.reset();
    near(view.x, 0, "reset x");
    near(view.y, 0, "reset y");
    near(view.scale(), 1, "reset scale");
    view.size(0, 0, 0, 0);
    view.pan(1, 1);
    check(!Float.isNaN(view.x) && !Float.isNaN(view.y), "unlaid-out view remains finite");
    System.out.println(
        "PASS: zoom limits, focal-point preservation, directional movement, image-edge clamping,"
            + " aspect-ratio letterboxing, resize, and reset.");
  }
}
