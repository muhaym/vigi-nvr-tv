package com.fantacode.vigitv;

import android.app.*;
import android.graphics.drawable.ColorDrawable;
import android.view.*;
import android.widget.*;

/** Compact remote-friendly controls shared by cycle and alarm panels. */
class TrayDialog extends Dialog {
  final Activity owner;
  final ControlTray content;
  final TextView close;

  TrayDialog(Activity activity, String title) {
    super(activity);
    owner = activity;
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
    getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
    content = new ControlTray(owner);
    LinearLayout heading = row();
    content.addView(heading, new LinearLayout.LayoutParams(-1, dp(44)));
    TextView label = text(title, 17);
    label.setSingleLine();
    label.setEllipsize(android.text.TextUtils.TruncateAt.END);
    heading.addView(label, new LinearLayout.LayoutParams(0, -1, 1));
    close = button("Close", this::dismiss);
    heading.addView(close, ControlTray.item(owner, 72, 0, 12));
    setContentView(content);
  }

  int dp(int value) {
    return Math.round(value * owner.getResources().getDisplayMetrics().density);
  }

  LinearLayout row() {
    LinearLayout row = new LinearLayout(owner);
    row.setGravity(Gravity.CENTER_VERTICAL);
    return row;
  }

  void section(android.view.View view, int height) {
    LinearLayout.LayoutParams params =
        new LinearLayout.LayoutParams(-1, height < 0 ? height : dp(height));
    params.topMargin = dp(12);
    content.addView(view, params);
  }

  TextView text(String label, int size) {
    TextView view = new TextView(owner);
    view.setText(label);
    view.setTextSize(size);
    view.setIncludeFontPadding(false);
    view.setTextColor(ControlTray.TEXT);
    view.setGravity(Gravity.CENTER_VERTICAL);
    return view;
  }

  TextView button(String label, Runnable action) {
    TextView view = text(label, 13);
    view.setGravity(Gravity.CENTER);
    view.setPadding(dp(12), 0, dp(12), 0);
    view.setFocusable(true);
    view.setFocusableInTouchMode(true);
    ControlTray.focus(view, false);
    view.setOnFocusChangeListener((v, focused) -> ControlTray.focus(view, focused));
    view.setOnClickListener(v -> action.run());
    return view;
  }

  void enabled(TextView button, boolean enabled) {
    button.setEnabled(enabled);
    button.setFocusable(enabled);
    button.setAlpha(enabled ? 1f : .4f);
  }

  @Override
  public void show() {
    super.show();
    getWindow().setLayout(dp(600), -2);
    getWindow().setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
    WindowManager.LayoutParams params = getWindow().getAttributes();
    params.y = dp(24);
    getWindow().setAttributes(params);
    close.requestFocus();
  }
}
