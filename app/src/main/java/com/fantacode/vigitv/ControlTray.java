package com.fantacode.vigitv;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared visual language for live, recorded, zoom and microphone controls. */
class ControlTray extends LinearLayout {
  static final int BACKGROUND = 0xff111720,
      TEXT = 0xffedf2ff,
      MUTED = 0xffaab4c5,
      FOCUS = 0xffedf2ff;
  static final int BUTTON_DP = 44, GAP_DP = 8;

  ControlTray(Context context) {
    super(context);
    setOrientation(VERTICAL);
    int pad = dp(20);
    setPadding(pad, dp(16), pad, dp(16));
    GradientDrawable background = new GradientDrawable();
    background.setColor(BACKGROUND);
    background.setCornerRadius(dp(12));
    background.setStroke(dp(1), 0xff303949);
    setBackground(background);
    setElevation(dp(4));
  }

  int dp(int value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }

  static LinearLayout.LayoutParams item(Context context, int width, float weight, int gap) {
    float density = context.getResources().getDisplayMetrics().density;
    LinearLayout.LayoutParams params =
        new LinearLayout.LayoutParams(
            width < 0 ? width : Math.round(width * density),
            Math.round(BUTTON_DP * density),
            weight);
    params.leftMargin = Math.round(gap * density);
    return params;
  }

  static void focus(TextView control, boolean focused) {
    GradientDrawable background = new GradientDrawable();
    background.setCornerRadius(8 * control.getResources().getDisplayMetrics().density);
    background.setColor(
        focused ? FOCUS : control.isSelected() || control.isActivated() ? 0xff26364a : 0xff1c2431);
    background.setStroke(
        Math.max(1, Math.round(control.getResources().getDisplayMetrics().density)),
        focused ? FOCUS : control.isSelected() || control.isActivated() ? 0xff617996 : 0xff303a48);
    control.setBackground(background);
    control.setTextColor(focused ? 0xff090b10 : TEXT);
  }
}
