package com.fantacode.vigitv;

import android.app.*;
import android.media.AudioManager;
import android.widget.*;
import java.util.*;
import org.json.JSONArray;

/** Selectable camera-speaker announcements. Selection alone never connects or captures audio. */
final class PaDialog extends TrayDialog implements PaController.Listener {
  final RecorderProfile profile;
  final String username, password;
  final List<CameraGroup> groups;
  final LinkedHashSet<Integer> selected = new LinkedHashSet<>();
  final Map<Integer, TextView> rows = new LinkedHashMap<>();
  final PaController controller;
  final TalkController.Authorization auth;
  final TextView all, choose, group, summary, state, check, test, start, stop;
  final LinearLayout speakerList;
  final ProgressBar meter;
  final AudioManager audio;
  boolean muted;

  PaDialog(
      Activity owner,
      RecorderProfile profile,
      List<CameraGroup> groups,
      String username,
      String password,
      TalkController.Authorization auth) {
    super(owner, "PA announcements");
    this.profile = profile;
    this.groups = new ArrayList<>(groups);
    this.username = username;
    this.password = password;
    this.auth = auth;
    audio = (AudioManager) owner.getSystemService(Activity.AUDIO_SERVICE);
    controller = new PaController(owner, auth, this);
    TextView hint =
        text(
            "Select cameras with speakers. Check connections before announcing from your USB"
                + " microphone.",
            13);
    hint.setMaxLines(2);
    section(hint, -2);
    LinearLayout choices = row();
    section(choices, 44);
    all = button("All speakers", () -> select(profile.cameras.keySet()));
    choose = button("Choose speakers", this::chooseSpeakers);
    group = button("Use group", this::chooseGroup);
    choices.addView(all, ControlTray.item(owner, 0, 1, 0));
    choices.addView(choose, ControlTray.item(owner, 0, 1, 8));
    choices.addView(group, ControlTray.item(owner, 0, 1, 8));
    summary = text("", 13);
    section(summary, 20);
    ScrollView scroll = new ScrollView(owner);
    scroll.setFocusable(true);
    scroll.setContentDescription("Selected speaker status");
    speakerList = new LinearLayout(owner);
    speakerList.setOrientation(LinearLayout.VERTICAL);
    scroll.addView(speakerList, new ScrollView.LayoutParams(-1, -2));
    section(scroll, 96);
    state = text("Microphone off", 13);
    state.setMaxLines(2);
    section(state, -2);
    meter = new ProgressBar(owner, null, android.R.attr.progressBarStyleHorizontal);
    meter.setMax(100);
    section(meter, 4);
    LinearLayout actions = row();
    section(actions, 44);
    check = button("Check", () -> begin(true, PaController.TEST_MS));
    test = button("Test · 10s", () -> begin(false, PaController.TEST_MS));
    start = button("Start PA", () -> begin(false, PaController.ANNOUNCEMENT_MS));
    stop = button("Stop all", controller::stop);
    actions.addView(check, ControlTray.item(owner, 0, 1, 0));
    actions.addView(test, ControlTray.item(owner, 0, 1, 8));
    actions.addView(start, ControlTray.item(owner, 0, 1, 8));
    actions.addView(stop, ControlTray.item(owner, 0, 1, 8));
    setOnDismissListener(
        d -> {
          controller.stop();
          restoreVolume();
        });
    try {
      JSONArray saved =
          new JSONArray(owner.getSharedPreferences("native", 0).getString(selectionKey(), "[]"));
      for (int i = 0; i < saved.length(); i++) {
        int id = saved.optInt(i, 0);
        if (profile.cameras.containsKey(id)) selected.add(id);
      }
    } catch (Exception malformed) {
      selected.clear();
    }
    refreshSelection();
  }

  String selectionKey() {
    return "pa_speakers_" + profile.host;
  }

  void select(Collection<Integer> ids) {
    if (controller.busy()) return;
    selected.clear();
    for (int id : ids) if (profile.cameras.containsKey(id)) selected.add(id);
    owner
        .getSharedPreferences("native", 0)
        .edit()
        .putString(selectionKey(), new JSONArray(selected).toString())
        .apply();
    refreshSelection();
  }

  void refreshSelection() {
    summary.setText(
        selected.isEmpty()
            ? "No speakers selected"
            : selected.size() == profile.cameras.size()
                ? "All " + selected.size() + " speakers selected"
                : selected.size() + " speakers selected");
    rows.clear();
    speakerList.removeAllViews();
    for (int id : selected) {
      TextView line = text(profile.name(id) + " · selected", 12);
      line.setMaxLines(2);
      line.setMinHeight(dp(24));
      speakerList.addView(line, new LinearLayout.LayoutParams(-1, -2));
      rows.put(id, line);
    }
    if (selected.isEmpty())
      speakerList.addView(text("Choose individual speakers, a group, or All speakers.", 12));
    state(false, "Microphone off");
  }

  void chooseSpeakers() {
    ArrayList<Integer> ids = new ArrayList<>(profile.cameras.keySet());
    String[] labels = new String[ids.size()];
    boolean[] draft = new boolean[ids.size()];
    for (int i = 0; i < ids.size(); i++) {
      labels[i] = profile.name(ids.get(i));
      draft[i] = selected.contains(ids.get(i));
    }
    new AlertDialog.Builder(owner)
        .setTitle("Choose speakers")
        .setMultiChoiceItems(labels, draft, (d, which, checked) -> draft[which] = checked)
        .setNegativeButton("Cancel", null)
        .setNeutralButton("Clear all", (d, w) -> select(Collections.emptyList()))
        .setPositiveButton(
            "Use speakers",
            (d, w) -> {
              ArrayList<Integer> chosen = new ArrayList<>();
              for (int i = 0; i < ids.size(); i++) if (draft[i]) chosen.add(ids.get(i));
              select(chosen);
            })
        .show();
  }

  void chooseGroup() {
    String[] labels = new String[groups.size()];
    for (int i = 0; i < groups.size(); i++) labels[i] = groups.get(i).name;
    new AlertDialog.Builder(owner)
        .setTitle("Speakers in a group")
        .setItems(labels, (d, which) -> select(groups.get(which).ids))
        .setNegativeButton("Cancel", null)
        .show();
  }

  void begin(boolean checkOnly, int durationMs) {
    if (controller.busy() || selected.isEmpty()) return;
    int[] targets = new int[selected.size()];
    int index = 0;
    StringBuilder names = new StringBuilder();
    for (int id : selected) {
      targets[index++] = id;
      if (names.length() > 0) names.append("\n");
      names.append(profile.name(id));
    }
    if (checkOnly) {
      controller.start(profile.host, username, password, targets, true, durationMs);
      return;
    }
    AlertDialog confirmation =
        new AlertDialog.Builder(owner)
            .setTitle(
                durationMs == PaController.TEST_MS
                    ? "Test " + targets.length + " speakers?"
                    : "Start PA to " + targets.length + " speakers?")
            .setMessage(
                names
                    + "\n\n"
                    + (durationMs == PaController.TEST_MS
                        ? "Ten-second USB microphone test."
                        : "Speak through the USB microphone. Stop all ends the announcement;"
                              + " automatic stop after five minutes.")
                    + " TV playback will be muted.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton(
                durationMs == PaController.TEST_MS ? "Start test" : "Start PA",
                (d, w) -> {
                  if (!isShowing() || !auth.allowed()) return;
                  if (!audio.isStreamMute(AudioManager.STREAM_MUSIC)) {
                    audio.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0);
                    muted = true;
                  }
                  controller.start(profile.host, username, password, targets, false, durationMs);
                })
            .create();
    confirmation.show();
    confirmation.getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus();
  }

  void restoreVolume() {
    if (muted) {
      audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0);
      muted = false;
    }
  }

  public void state(boolean busy, String message) {
    state.setText(message);
    enabled(all, !busy);
    enabled(choose, !busy);
    enabled(group, !busy && !groups.isEmpty());
    boolean available = !selected.isEmpty() && !busy;
    enabled(check, available);
    enabled(test, available);
    enabled(start, available);
    enabled(stop, busy);
    if (busy) stop.requestFocus();
    else restoreVolume();
  }

  public void speaker(int channel, String message) {
    TextView line = rows.get(channel);
    if (line != null) line.setText(profile.name(channel) + " · " + message);
  }

  public void level(int value) {
    meter.setProgress(value);
  }
}
