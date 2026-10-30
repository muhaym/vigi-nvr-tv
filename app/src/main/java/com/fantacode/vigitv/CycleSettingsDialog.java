package com.fantacode.vigitv;

import android.app.Activity;
import android.widget.*;

/** Mode and timing fit in one screen without a scrolling radio list. */
final class CycleSettingsDialog extends TrayDialog {
  interface Selection {
    void apply(int seconds, boolean cameras);
  }

  boolean cameras;
  final boolean canCameras, canPages;
  final TextView cameraButton, pageButton;
  final TextView[] intervals = new TextView[4];

  CycleSettingsDialog(
      Activity activity,
      String group,
      int count,
      int pages,
      int current,
      int mode,
      Selection selection) {
    super(activity, "Auto-cycle · " + group);
    cameras = mode != 2;
    canCameras = count > 1;
    canPages = pages > 1;
    if (cameras && !canCameras && canPages) cameras = false;
    if (!cameras && !canPages && canCameras) cameras = true;
    LinearLayout modes = row();
    section(modes, 44);
    cameraButton = button("Each camera", () -> select(true));
    pageButton = button("Next page", () -> select(false));
    modes.addView(cameraButton, ControlTray.item(owner, 0, 1, 0));
    modes.addView(pageButton, ControlTray.item(owner, 0, 1, 8));
    enabled(cameraButton, canCameras);
    enabled(pageButton, canPages);
    TextView hint = text("Choose an interval to start. Opening a camera pauses cycling.", 13);
    hint.setTextColor(ControlTray.MUTED);
    section(hint, -2);
    LinearLayout times = row();
    section(times, 44);
    int[] seconds = {5, 15, 30, 60};
    for (int i = 0; i < seconds.length; i++) {
      final int value = seconds[i];
      TextView choice =
          button(
              value + " seconds",
              () -> {
                selection.apply(value, cameras);
                dismiss();
              });
      choice.setSelected(current == value);
      ControlTray.focus(choice, false);
      intervals[i] = choice;
      times.addView(choice, ControlTray.item(owner, 0, 1, i == 0 ? 0 : 8));
    }
    LinearLayout footer = row();
    section(footer, 44);
    TextView off =
        button(
            "Turn cycling off",
            () -> {
              selection.apply(0, cameras);
              dismiss();
            });
    footer.addView(off, ControlTray.item(owner, 0, 1, 0));
    select(cameras);
  }

  void select(boolean value) {
    cameras = value;
    cameraButton.setSelected(value);
    pageButton.setSelected(!value);
    ControlTray.focus(cameraButton, cameraButton.hasFocus());
    ControlTray.focus(pageButton, pageButton.hasFocus());
    for (TextView interval : intervals) enabled(interval, value ? canCameras : canPages);
  }
}
