package com.fantacode.vigitv;

import android.content.Context;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;

/** Compact remote-friendly display zoom controls. The video remains behind this overlay. */
final class ZoomOverlay extends ControlTray {
  interface Listener {
    void zoom(int direction);

    void move();

    void reset();

    void done();

    void controlsFocused();

    void lens(boolean zoomIn);

    void stopLens();
  }

  final Listener listener;
  final TextView scale, hint, title;
  final TextView minus, plus, move, lensStatus;
  final LinearLayout lensRow, controls;
  final TextView lensMinus, lensPlus;
  final boolean showLens;
  boolean lensVisible = true;

  ZoomOverlay(
      Context context, String camera, boolean playback, boolean hasOpticalLens, Listener actions) {
    super(context);
    listener = actions;
    setOrientation(VERTICAL);
    setMinimumWidth(dp(520));
    showLens = hasOpticalLens && !playback;
    title = label(camera + (playback ? "  /  Playback" : "  /  Live"), 13, 0xffb4bdcb);
    LayoutParams titleSize = new LayoutParams(-1, dp(24));
    titleSize.bottomMargin = dp(12);
    addView(title, titleSize);
    lensRow = new LinearLayout(context);
    lensRow.setGravity(Gravity.CENTER_VERTICAL);
    LayoutParams lensSize = new LayoutParams(-1, dp(ControlTray.BUTTON_DP));
    lensSize.bottomMargin = dp(8);
    addView(lensRow, lensSize);
    lensStatus = label("Checking optical lens…", 13, 0xffb4bdcb);
    lensRow.addView(lensStatus, new LayoutParams(0, -1, 1));
    lensMinus = button(lensRow, "Lens −", "Optical zoom out", () -> listener.lens(false), dp(92));
    lensPlus = button(lensRow, "Lens +", "Optical zoom in", () -> listener.lens(true), dp(92));
    for (TextView control : new TextView[] {lensMinus, lensPlus})
      control.setOnKeyListener(
          (v, key, event) -> {
            if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER) {
              if (event.getAction() == KeyEvent.ACTION_DOWN) listener.lens(v == lensPlus);
              else if (event.getAction() == KeyEvent.ACTION_UP) listener.stopLens();
              return true;
            }
            return false;
          });
    lensMinus.setVisibility(GONE);
    lensPlus.setVisibility(GONE);
    lensRow.setVisibility(hasOpticalLens && !playback ? VISIBLE : GONE);
    controls = new LinearLayout(context);
    controls.setGravity(Gravity.CENTER_VERTICAL);
    addView(controls, new LayoutParams(-1, dp(ControlTray.BUTTON_DP)));
    minus = button(controls, "−", "Zoom out", () -> listener.zoom(-1), dp(44));
    scale = label("View 1×", 14, 0xffedf2ff);
    scale.setGravity(Gravity.CENTER);
    scale.setTypeface(Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
    LayoutParams scaleSize = new LayoutParams(dp(64), -1);
    scaleSize.leftMargin = dp(8);
    controls.addView(scale, scaleSize);
    plus = button(controls, "+", "Zoom in", () -> listener.zoom(1), dp(44));
    View gap = new View(context);
    LayoutParams spacerSize = new LayoutParams(0, 1, 1);
    spacerSize.leftMargin = dp(16);
    controls.addView(gap, spacerSize);
    move =
        button(controls, "Move", "Move around the zoomed picture", () -> listener.move(), dp(76));
    button(controls, "Reset", "Reset zoom and position", () -> listener.reset(), dp(76));
    button(controls, "Done", "Close zoom controls", () -> listener.done(), dp(72));
    hint = label("−/+ Zoom   ·   Move to pan   ·   Back done", 11, 0xff98a2b3);
    addView(hint, new LayoutParams(-1, dp(32)));
  }

  int dp(float size) {
    return Math.round(size * getResources().getDisplayMetrics().density);
  }

  TextView label(String value, int size, int color) {
    TextView text = new TextView(getContext());
    text.setText(value);
    text.setTextSize(size);
    text.setIncludeFontPadding(false);
    text.setTextColor(color);
    text.setSingleLine(true);
    text.setGravity(Gravity.CENTER_VERTICAL);
    return text;
  }

  TextView button(LinearLayout row, String value, String description, Runnable action, int width) {
    TextView control = label(value, value.equals("+") || value.equals("−") ? 22 : 13, 0xffedf2ff);
    control.setGravity(Gravity.CENTER);
    control.setContentDescription(description);
    control.setFocusable(true);
    control.setFocusableInTouchMode(true);
    control.setOnClickListener(v -> action.run());
    control.setOnFocusChangeListener(
        (v, focused) -> {
          ControlTray.focus(control, focused);
          if (focused) listener.controlsFocused();
          else if (control == lensMinus || control == lensPlus) listener.stopLens();
        });
    control.setPadding(dp(12), 0, dp(12), 0);
    ControlTray.focus(control, false);
    LayoutParams params = new LayoutParams(width, dp(ControlTray.BUTTON_DP));
    params.leftMargin = row.getChildCount() > 0 ? dp(8) : 0;
    row.addView(control, params);
    return control;
  }

  void update(float factor, boolean panning) {
    String zoom =
        (factor == (int) factor ? Integer.toString((int) factor) : Float.toString(factor)) + "×";
    scale.setText("View " + zoom);
    title.setVisibility(panning ? GONE : VISIBLE);
    controls.setVisibility(panning ? GONE : VISIBLE);
    lensRow.setVisibility(!panning && showLens && lensVisible ? VISIBLE : GONE);
    hint.setVisibility(panning ? VISIBLE : GONE);
    hint.setText(
        panning
            ? zoom + "  ·  Arrows move   ·   OK controls   ·   Back done"
            : "↑/↓ Rows   ·   ←/→ Select   ·   Move to pan   ·   Back done");
    minus.setAlpha(factor <= 1 ? .45f : 1f);
    plus.setAlpha(factor >= 4 ? .45f : 1f);
  }

  void optical(boolean supported, String message) {
    lensVisible = supported;
    lensRow.setVisibility(supported && controls.getVisibility() == VISIBLE ? VISIBLE : GONE);
    lensStatus.setText(message);
    lensMinus.setVisibility(supported ? VISIBLE : GONE);
    lensPlus.setVisibility(supported ? VISIBLE : GONE);
  }

  // Consume all directional keys while controls are visible: focus must never escape
  // into the video or hidden toolbar. Only the explicit Move action enters panning.
  boolean navigate(KeyEvent event) {
    int key = event.getKeyCode();
    if (key != KeyEvent.KEYCODE_DPAD_LEFT
        && key != KeyEvent.KEYCODE_DPAD_RIGHT
        && key != KeyEvent.KEYCODE_DPAD_UP
        && key != KeyEvent.KEYCODE_DPAD_DOWN) return false;
    if (event.getAction() != KeyEvent.ACTION_DOWN) return true;
    View focused = findFocus();
    LinearLayout row = focused != null && focused.getParent() == lensRow ? lensRow : controls;
    java.util.ArrayList<View> buttons = buttons(row);
    if (buttons.isEmpty()) return true;
    int current = buttons.indexOf(focused);
    if (current < 0) {
      focusControls();
      return true;
    }
    if (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT) {
      int next =
          Math.max(
              0,
              Math.min(buttons.size() - 1, current + (key == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1)));
      buttons.get(next).requestFocus();
      return true;
    }
    LinearLayout target =
        key == KeyEvent.KEYCODE_DPAD_UP && row == controls
            ? lensRow
            : key == KeyEvent.KEYCODE_DPAD_DOWN && row == lensRow ? controls : null;
    if (target == null || target.getVisibility() != VISIBLE) return true;
    java.util.ArrayList<View> candidates = buttons(target);
    View nearest = null;
    float distance = Float.MAX_VALUE;
    float center = focused.getLeft() + focused.getWidth() / 2f;
    for (View candidate : candidates) {
      float delta = Math.abs(candidate.getLeft() + candidate.getWidth() / 2f - center);
      if (delta < distance) {
        nearest = candidate;
        distance = delta;
      }
    }
    if (nearest != null) nearest.requestFocus();
    return true;
  }

  java.util.ArrayList<View> buttons(LinearLayout row) {
    java.util.ArrayList<View> result = new java.util.ArrayList<>();
    for (int i = 0; i < row.getChildCount(); i++) {
      View child = row.getChildAt(i);
      if (child.getVisibility() == VISIBLE && child.isFocusable() && child.isEnabled())
        result.add(child);
    }
    return result;
  }

  void focusControls() {
    plus.requestFocus();
  }
}
