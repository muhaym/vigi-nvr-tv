package com.fantacode.vigitv;

import android.app.*;
import android.widget.*;

/** Opening this panel only reads capabilities; actuation requires an explicit start. */
final class AlarmDialog extends TrayDialog implements AlarmController.Listener {
  final AlarmController controller;
  final TextView status, start, stop;
  final String camera;
  AlertDialog confirmation;

  AlarmDialog(
      Activity owner,
      String name,
      String host,
      String user,
      String password,
      int channel,
      TalkController.Authorization authorization) {
    super(owner, "Alarm · " + name);
    camera = name;
    controller = new AlarmController(owner, host, user, password, channel, authorization, this);
    status = text("Checking camera alarm outputs…", 14);
    status.setMinHeight(dp(40));
    section(status, -2);
    LinearLayout actions = row();
    section(actions, 44);
    start = button("Start · 10 seconds", this::confirm);
    stop = button("Stop alarm", () -> controller.silence());
    actions.addView(start, ControlTray.item(owner, 0, 1, 0));
    actions.addView(stop, ControlTray.item(owner, 0, 1, 8));
    enabled(start, false);
    enabled(stop, false);
    TextView hint =
        text("Manual camera alarm. Automatic detection settings stay as configured.", 12);
    hint.setTextColor(ControlTray.MUTED);
    section(hint, -2);
    setOnDismissListener(
        d -> {
          if (confirmation != null) confirmation.dismiss();
          controller.close();
        });
  }

  void confirm() {
    if (!start.isEnabled()) return;
    confirmation =
        new AlertDialog.Builder(owner)
            .setTitle("Start alarm · " + camera + "?")
            .setMessage(
                "Activates this camera's supported siren/light. The app will request Stop after 10"
                    + " seconds, or when you leave this panel.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Start alarm", (d, w) -> controller.start())
            .create();
    confirmation.show();
    confirmation.getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus();
  }

  @Override
  public void show() {
    super.show();
    controller.inspect();
  }

  @Override
  public void state(
      AlarmProtocol.Capabilities capabilities, String message, boolean active, boolean busy) {
    status.setText(message);
    status.setTextColor(active ? 0xffffcf9a : ControlTray.MUTED);
    boolean supported = capabilities != null && capabilities.supported();
    start.setText(
        supported
            ? "Start " + capabilities.label().toLowerCase(java.util.Locale.ROOT) + " · 10s"
            : "Start · 10 seconds");
    enabled(start, supported && !busy && !AlarmSafety.any(owner));
    enabled(
        stop,
        (supported || AlarmSafety.pending(owner, controller.host, controller.channel))
            && (!busy || controller.hasRun()));
  }

  @Override
  public void onWindowFocusChanged(boolean focused) {
    super.onWindowFocusChanged(focused);
    if (!focused) controller.cancel();
  }
}
