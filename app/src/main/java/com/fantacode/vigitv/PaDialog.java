package com.fantacode.vigitv;

import android.app.*;
import android.media.AudioManager;
import android.widget.*;
import java.util.*;

/** Experimental two-camera PA. Opening this panel never allocates talk or captures audio. */
final class PaDialog extends TrayDialog implements PaController.Listener {
  final RecorderProfile profile;
  final String username, password;
  final PaController controller;
  final TalkController.Authorization auth;
  final TextView firstButton, secondButton, firstStatus, secondStatus, state, check, start, stop;
  final ProgressBar meter;
  int first, second;
  final AudioManager audio;
  final boolean wasMuted;
  boolean muted;

  PaDialog(
      Activity owner,
      RecorderProfile profile,
      String username,
      String password,
      TalkController.Authorization auth) {
    super(owner, "PA prototype · two speakers");
    this.profile = profile;
    this.username = username;
    this.password = password;
    this.auth = auth;
    audio = (AudioManager) owner.getSystemService(Activity.AUDIO_SERVICE);
    wasMuted = audio.isStreamMute(AudioManager.STREAM_MUSIC);
    controller = new PaController(owner, auth, this);
    TextView hint =
        text(
            "Choose two free rooms. Check reserves both talk sessions without audio. Start sends"
                + " the USB microphone for ten seconds.",
            13);
    hint.setMaxLines(3);
    section(hint, -2);
    LinearLayout choices = row();
    section(choices, 44);
    firstButton = button("Choose speaker 1", () -> choose(true));
    secondButton = button("Choose speaker 2", () -> choose(false));
    choices.addView(firstButton, ControlTray.item(owner, 0, 1, 0));
    choices.addView(secondButton, ControlTray.item(owner, 0, 1, 8));
    firstStatus = text("Speaker 1 · not selected", 12);
    secondStatus = text("Speaker 2 · not selected", 12);
    section(firstStatus, 20);
    section(secondStatus, 20);
    state = text("Microphone off", 13);
    state.setMaxLines(3);
    section(state, -2);
    meter = new ProgressBar(owner, null, android.R.attr.progressBarStyleHorizontal);
    meter.setMax(100);
    section(meter, 4);
    LinearLayout actions = row();
    section(actions, 44);
    check = button("Check connection", () -> begin(true));
    start = button("Start · 10 seconds", () -> begin(false));
    stop = button("Stop both", controller::stop);
    actions.addView(check, ControlTray.item(owner, 0, 1, 0));
    actions.addView(start, ControlTray.item(owner, 0, 1, 8));
    actions.addView(stop, ControlTray.item(owner, 0, 1, 8));
    setOnDismissListener(
        d -> {
          controller.stop();
          restoreVolume();
        });
    state(false, "Microphone off");
  }

  void choose(boolean firstSlot) {
    ArrayList<Integer> ids = new ArrayList<>(profile.cameras.keySet());
    String[] labels = new String[ids.size()];
    for (int i = 0; i < ids.size(); i++) labels[i] = profile.name(ids.get(i));
    new AlertDialog.Builder(owner)
        .setTitle(firstSlot ? "Speaker 1" : "Speaker 2")
        .setItems(
            labels,
            (d, which) -> {
              int channel = ids.get(which);
              if (channel == (firstSlot ? second : first)) {
                state.setText("Choose two different cameras");
                return;
              }
              if (firstSlot) {
                first = channel;
                firstButton.setText(profile.name(channel));
                firstStatus.setText("Speaker 1 · ready");
              } else {
                second = channel;
                secondButton.setText(profile.name(channel));
                secondStatus.setText("Speaker 2 · ready");
              }
              state(false, "Microphone off · ready to check");
            })
        .setNegativeButton("Cancel", null)
        .show();
  }

  void begin(boolean checkOnly) {
    if (controller.busy() || first == 0 || second == 0 || first == second) return;
    if (!checkOnly) {
      new AlertDialog.Builder(owner)
          .setTitle("Start two-speaker test?")
          .setMessage(
              "Send the USB microphone to "
                  + profile.name(first)
                  + " and "
                  + profile.name(second)
                  + " for ten seconds. TV playback will be muted.")
          .setNegativeButton("Cancel", null)
          .setPositiveButton(
              "Start test",
              (d, w) -> {
                if (!isShowing() || !auth.allowed()) return;
                if (!wasMuted) {
                  audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0);
                  muted = true;
                }
                controller.start(profile.host, username, password, first, second, false);
              })
          .show();
    } else controller.start(profile.host, username, password, first, second, true);
  }

  void restoreVolume() {
    if (muted) {
      audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0);
      muted = false;
    }
  }

  public void state(boolean busy, String message) {
    state.setText(message);
    enabled(firstButton, !busy);
    enabled(secondButton, !busy);
    boolean selected = first != 0 && second != 0 && first != second;
    enabled(check, selected && !busy);
    enabled(start, selected && !busy);
    enabled(stop, busy);
    if (busy) stop.requestFocus();
    else restoreVolume();
  }

  public void speaker(int channel, String message) {
    (channel == first ? firstStatus : secondStatus)
        .setText(profile.name(channel) + " · " + message);
  }

  public void level(int value) {
    meter.setProgress(value);
  }
}
