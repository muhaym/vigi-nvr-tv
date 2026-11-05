package com.fantacode.vigitv;

/** Display-only zoom/pan. Coordinates are screen pixels; no recorder settings change. */
final class DigitalViewport {
  static final float[] STEPS = {1f, 1.5f, 2f, 3f, 4f};
  int step;
  float x, y, width, height, imageWidth, imageHeight;

  float scale() {
    return STEPS[step];
  }

  float maxX() {
    return Math.max(0, (imageWidth * scale() - width) / 2);
  }

  float maxY() {
    return Math.max(0, (imageHeight * scale() - height) / 2);
  }

  static float clamp(float value, float limit) {
    return Math.max(-limit, Math.min(limit, value));
  }

  void clamp() {
    x = clamp(x, maxX());
    y = clamp(y, maxY());
  }

  void size(float w, float h, float iw, float ih) {
    width = Math.max(0, w);
    height = Math.max(0, h);
    // Hardware decoding can start without a video-layout callback. The displayed
    // surface is still a usable frame; missing metadata must not clamp all pan to zero.
    imageWidth = iw > 0 ? iw : width;
    imageHeight = ih > 0 ? ih : height;
    clamp();
  }

  void zoom(int direction) {
    float before = scale();
    step = Math.max(0, Math.min(STEPS.length - 1, step + direction));
    float ratio = scale() / before;
    x *= ratio;
    y *= ratio;
    clamp();
  }

  void pan(int horizontal, int vertical) {
    x -= horizontal * width * .06f;
    y -= vertical * height * .06f;
    clamp();
  }

  void reset() {
    step = 0;
    x = 0;
    y = 0;
  }
}
