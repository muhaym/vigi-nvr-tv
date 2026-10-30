package com.fantacode.vigitv;

import android.app.*;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.view.*;
import android.widget.*;

/** A bottom tray with explicit modes and a microphone that starts only on user action. */
final class TalkDialog extends Dialog implements TalkController.Listener {
  interface Volume {
    void set(int volume);
  }

  final Activity owner;
  final String host, user, password;
  final int channel;
  final boolean priorAudio;
  final Volume volume;
  final TalkController controller;
  TextView status, talk, mic, hint, conversation, push, gesture;
  ProgressBar meter;
  int mode;
  boolean armed, testing;

  TalkDialog(
      Activity activity,
      String camera,
      String h,
      String u,
      String secret,
      int id,
      boolean audio,
      TalkController.Authorization authorization,
      Volume output) {
    super(activity);
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    owner = activity;
    host = h;
    user = u;
    password = secret;
    channel = id;
    priorAudio = audio;
    volume = output;
    mode = owner.getSharedPreferences("native", 0).getInt("talk_mode", 1);
    if (mode < 0 || mode > 2) mode = 1;
    controller = new TalkController(owner, authorization, this);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
    getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
    ControlTray content = new ControlTray(owner);
    LinearLayout heading = row();
    content.addView(heading, new LinearLayout.LayoutParams(-1, dp(ControlTray.BUTTON_DP)));
    TextView title = text("Talk · " + camera, 17);
    title.setSingleLine();
    title.setEllipsize(android.text.TextUtils.TruncateAt.END);
    heading.addView(title, new LinearLayout.LayoutParams(0, -1, 1));
    TextView close = button("Close", () -> dismiss());
    heading.addView(close, ControlTray.item(owner, 72, 0, 12));
    LinearLayout choices = row();
    LinearLayout.LayoutParams choicesSize =
        new LinearLayout.LayoutParams(-1, dp(ControlTray.BUTTON_DP));
    choicesSize.topMargin = dp(12);
    content.addView(choices, choicesSize);
    conversation = button("Conversation", () -> select(2));
    push = button("Push-to-talk", () -> select(mode == 0 ? 0 : 1));
    choices.addView(conversation, ControlTray.item(owner, 0, 1, 0));
    choices.addView(push, ControlTray.item(owner, 0, 1, 8));
    hint = text("", 13);
    hint.setTextColor(ControlTray.MUTED);
    hint.setMaxLines(2);
    hint.setMinHeight(dp(20));
    LinearLayout.LayoutParams hintSize = new LinearLayout.LayoutParams(-1, -2);
    hintSize.topMargin = dp(12);
    content.addView(hint, hintSize);
    status = text("Microphone off · listening", 13);
    status.setMaxLines(2);
    status.setMinHeight(dp(20));
    LinearLayout.LayoutParams statusSize = new LinearLayout.LayoutParams(-1, -2);
    statusSize.topMargin = dp(4);
    content.addView(status, statusSize);
    meter = new ProgressBar(owner, null, android.R.attr.progressBarStyleHorizontal);
    meter.setMax(100);
    meter.setProgressTintList(android.content.res.ColorStateList.valueOf(0xff9eb7d3));
    LinearLayout.LayoutParams meterSize = new LinearLayout.LayoutParams(-1, dp(3));
    meterSize.topMargin = dp(8);
    content.addView(meter, meterSize);
    LinearLayout actions = row();
    LinearLayout.LayoutParams actionsSize =
        new LinearLayout.LayoutParams(-1, dp(ControlTray.BUTTON_DP));
    actionsSize.topMargin = dp(16);
    content.addView(actions, actionsSize);
    talk = button("Start talking", () -> {});
    talk.setActivated(true);
    ControlTray.focus(talk, false);
    actions.addView(talk, ControlTray.item(owner, 0, 1, 0));
    talk.setOnClickListener(
        v -> {
          if (mode == 0)
            talk.announceForAccessibility("Use Press once for click-based push-to-talk");
          else toggle();
        });
    talk.setOnKeyListener(
        (view, key, event) -> {
          if (key != KeyEvent.KEYCODE_DPAD_CENTER && key != KeyEvent.KEYCODE_ENTER) return false;
          if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            armed = true;
            if (mode == 0) begin();
          } else if (event.getAction() == KeyEvent.ACTION_UP) {
            boolean trigger = armed && !event.isCanceled();
            armed = false;
            if (mode == 0 || !trigger) controller.stop();
            else toggle();
          }
          return true;
        });
    talk.setOnTouchListener(
        (view, event) -> {
          int action = event.getActionMasked();
          if (action == MotionEvent.ACTION_DOWN) {
            talk.post(() -> talk.requestFocus());
            armed = true;
            if (mode == 0) begin();
          } else if (action == MotionEvent.ACTION_UP) {
            boolean trigger = armed;
            armed = false;
            if (mode == 0) controller.stop();
            else if (trigger) view.performClick();
          } else if (action == MotionEvent.ACTION_CANCEL) {
            armed = false;
            controller.stop();
          }
          return true;
        });
    talk.setOnFocusChangeListener(
        (view, focused) -> {
          ControlTray.focus(talk, focused);
          if (!focused) {
            armed = false;
            if (mode == 0) controller.stop();
          }
        });
    mic =
        button(
            "Test mic",
            () -> {
              controller.stop();
              testing = true;
              controller.start("", "", "", channel, true);
            });
    gesture = button("Tap mode", () -> select(mode == 0 ? 1 : 0));
    actions.addView(gesture, ControlTray.item(owner, 112, 0, 8));
    actions.addView(mic, ControlTray.item(owner, 96, 0, 8));
    setContentView(content);
    setOnShowListener(
        d -> {
          Window window = getWindow();
          window.setLayout(dp(620), -2);
          window.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
          WindowManager.LayoutParams params = window.getAttributes();
          params.y = dp(24);
          window.setAttributes(params);
          volume.set(100);
          update();
          talk.post(() -> talk.requestFocus());
        });
    setOnDismissListener(
        d -> {
          armed = false;
          controller.stop();
          volume.set(priorAudio ? 100 : 0);
        });
  }

  int dp(int size) {
    return Math.round(size * owner.getResources().getDisplayMetrics().density);
  }

  LinearLayout row() {
    LinearLayout row = new LinearLayout(owner);
    row.setGravity(Gravity.CENTER_VERTICAL);
    return row;
  }

  TextView text(String label, int size) {
    TextView view = new TextView(owner);
    view.setText(label);
    view.setTextSize(size);
    view.setIncludeFontPadding(false);
    view.setTextColor(ControlTray.TEXT);
    view.setGravity(Gravity.CENTER_VERTICAL);
    view.setTypeface(Typeface.create("sans-serif", android.graphics.Typeface.NORMAL));
    return view;
  }

  TextView button(String label, Runnable action) {
    TextView view = text(label, 13);
    view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    view.setGravity(Gravity.CENTER);
    view.setPadding(dp(12), 0, dp(12), 0);
    view.setFocusable(true);
    view.setFocusableInTouchMode(true);
    ControlTray.focus(view, false);
    view.setOnFocusChangeListener((v, focused) -> ControlTray.focus(view, focused));
    view.setOnClickListener(v -> action.run());
    return view;
  }

  void select(int choice) {
    controller.stop();
    armed = false;
    mode = choice;
    owner.getSharedPreferences("native", 0).edit().putInt("talk_mode", mode).apply();
    update();
  }

  void update() {
    boolean call = mode == 2;
    conversation.setSelected(call);
    push.setSelected(!call);
    ControlTray.focus(conversation, conversation.hasFocus());
    ControlTray.focus(push, push.hasFocus());
    gesture.setVisibility(call ? View.GONE : View.VISIBLE);
    gesture.setText(mode == 0 ? "Hold mode" : "Tap mode");
    gesture.setContentDescription(
        mode == 0 ? "Hold mode. Switch to tap to talk" : "Tap mode. Switch to hold to talk");
    talk.setText(
        testing && controller.active()
            ? "Stop mic test"
            : call
                ? (controller.active() ? "End conversation" : "Start conversation")
                : mode == 0
                    ? "Hold OK to talk"
                    : controller.active() ? "Stop talking · listen" : "Start talking");
    hint.setText(
        call
            ? "Talk and listen together. Uses the connected USB microphone."
            : mode == 0
                ? "Hold OK to speak. Release to listen."
                : "Press once to speak. Press again to listen.");
  }

  void begin() {
    controller.stop();
    testing = false;
    controller.start(host, user, password, channel, false, mode == 2);
  }

  void toggle() {
    if (controller.active()) controller.stop();
    else begin();
  }

  @Override
  public void state(boolean active, String message) {
    if (!active) testing = false;
    status.setText(message);
    status.setTextColor(active ? 0xffffcf9a : ControlTray.MUTED);
    volume.set(active && !controller.duplexActive() ? 0 : 100);
    mic.setEnabled(!active);
    update();
  }

  @Override
  public void level(int percent) {
    meter.setProgress(percent);
    meter.setContentDescription("Microphone level " + percent + " percent");
  }

  @Override
  public void onWindowFocusChanged(boolean focused) {
    super.onWindowFocusChanged(focused);
    if (!focused) {
      armed = false;
      controller.stop();
    }
  }

  @Override
  public void onBackPressed() {
    dismiss();
  }
}
