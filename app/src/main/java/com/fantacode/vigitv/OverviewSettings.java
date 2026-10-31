package com.fantacode.vigitv;

import android.app.*;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Remote-friendly, unsaved cell assignments; the public UI never exposes raw channel-zero IDs. */
final class OverviewSettings extends TrayDialog {
  interface Result {
    boolean saved(RecorderProfile profile);
  }

  final RecorderProfile profile;
  final CompositeDraft draft;
  final boolean required;
  final Result callback;
  final GridLayout grid;
  final TextView size, status, save, toggle, fill;
  boolean enabled;

  static void show(Activity owner, RecorderProfile profile, boolean required, Result callback) {
    new OverviewSettings(owner, profile, required, callback).show();
  }

  OverviewSettings(Activity owner, RecorderProfile profile, boolean required, Result callback) {
    super(owner, "Arrange overview");
    this.profile = profile;
    this.required = required;
    this.callback = callback;
    draft = new CompositeDraft(profile);
    enabled = profile.combined || required;
    TextView hint =
        text(
            "Match the positions in your recorder’s combined preview. Select a tile to assign a"
                + " camera by name.",
            13);
    hint.setMaxLines(2);
    hint.setTextColor(ControlTray.MUTED);
    section(hint, -2);
    LinearLayout options = row();
    section(options, 44);
    size = button("", this::chooseSize);
    options.addView(size, ControlTray.item(owner, 0, 1, 0));
    fill = button("Fill in camera order", this::confirmFill);
    options.addView(fill, ControlTray.item(owner, 0, 1, 8));
    toggle =
        button(
            "",
            () -> {
              enabled = !enabled;
              refresh(-1);
            });
    if (!required) options.addView(toggle, ControlTray.item(owner, 0, 1, 8));
    ScrollView scroll = new ScrollView(owner);
    scroll.setFillViewport(false);
    grid = new GridLayout(owner);
    scroll.addView(grid, new ScrollView.LayoutParams(-1, -2));
    section(scroll, 200);
    status = text("", 13);
    status.setMaxLines(2);
    status.setMinHeight(dp(32));
    status.setTextColor(ControlTray.MUTED);
    section(status, -2);
    LinearLayout footer = row();
    section(footer, 44);
    TextView cancel = button("Cancel", this::dismiss);
    footer.addView(cancel, ControlTray.item(owner, 0, 1, 0));
    save = button("Save overview", this::save);
    footer.addView(save, ControlTray.item(owner, 0, 1, 8));
    refresh(-1);
  }

  @Override
  public void show() {
    super.show();
    getWindow().setLayout(dp(820), -2);
  }

  void refresh(int focus) {
    size.setText("Recorder grid · " + draft.columns + " × " + draft.rows);
    toggle.setText(enabled ? "Preview enabled" : "Preview disabled");
    grid.removeAllViews();
    grid.setColumnCount(draft.columns);
    grid.setRowCount(draft.rows);
    for (int i = 0; i < draft.cells.length; i++) {
      final int cell = i;
      int id = draft.cells[i];
      TextView tile = button(id == 0 ? "Empty" : profile.name(id), () -> chooseCamera(cell));
      tile.setTextSize(draft.columns > 4 ? 11 : 12);
      tile.setMaxLines(2);
      tile.setEllipsize(TextUtils.TruncateAt.END);
      tile.setMinWidth(0);
      tile.setPadding(dp(6), 0, dp(6), 0);
      tile.setContentDescription(
          "Row "
              + (i / draft.columns + 1)
              + ", column "
              + (i % draft.columns + 1)
              + ": "
              + (id == 0 ? "Empty" : profile.name(id)));
      tile.setSelected(id != 0);
      ControlTray.focus(tile, false);
      enabled(tile, enabled);
      GridLayout.LayoutParams position =
          new GridLayout.LayoutParams(
              GridLayout.spec(i / draft.columns), GridLayout.spec(i % draft.columns, 1f));
      position.width = 0;
      position.height = dp(44);
      position.setMargins(dp(3), dp(3), dp(3), dp(3));
      grid.addView(tile, position);
      if (i == focus) tile.requestFocus();
    }
    List<Integer> missing = draft.unassigned();
    String message =
        enabled
            ? (profile.cameras.size() - missing.size())
                + " of "
                + profile.cameras.size()
                + " cameras placed"
            : "Combined preview is off. Individual camera layouts remain available.";
    if (enabled && !missing.isEmpty()) {
      StringBuilder names = new StringBuilder();
      for (int i = 0; i < Math.min(3, missing.size()); i++) {
        if (i > 0) names.append(", ");
        names.append(profile.name(missing.get(i)));
      }
      message += " · Still to place: " + names + (missing.size() > 3 ? "…" : "");
    }
    status.setText(message);
    enabled(save, !enabled || missing.isEmpty());
    enabled(size, enabled);
    enabled(fill, enabled);
  }

  void chooseCamera(int cell) {
    ArrayList<Integer> ids = new ArrayList<>(profile.cameras.keySet());
    String[] labels = new String[ids.size() + 1];
    labels[0] = "Empty";
    int selected = 0;
    for (int i = 0; i < ids.size(); i++) {
      int id = ids.get(i);
      int placed = draft.position(id);
      labels[i + 1] = profile.name(id) + (placed >= 0 && placed != cell ? " · move here" : "");
      if (draft.cells[cell] == id) selected = i + 1;
    }
    new AlertDialog.Builder(owner)
        .setTitle("Row " + (cell / draft.columns + 1) + " · Column " + (cell % draft.columns + 1))
        .setSingleChoiceItems(
            labels,
            selected,
            (dialog, which) -> {
              draft.assign(cell, which == 0 ? 0 : ids.get(which - 1));
              dialog.dismiss();
              refresh(cell);
            })
        .setNegativeButton("Cancel", null)
        .show();
  }

  void confirmFill() {
    new AlertDialog.Builder(owner)
        .setTitle("Arrange in camera-list order?")
        .setMessage(
            "This replaces the draft grid. Check that it matches the recorder’s preview before"
                + " saving.")
        .setNegativeButton("Cancel", null)
        .setPositiveButton(
            "Arrange",
            (d, w) -> {
              draft.fill();
              refresh(0);
            })
        .show();
  }

  void chooseSize() {
    String[] labels = {"2 × 2", "3 × 3", "4 × 4", "5 × 5", "6 × 6", "8 × 8", "Custom grid…"};
    int[] sizes = {2, 3, 4, 5, 6, 8};
    new AlertDialog.Builder(owner)
        .setTitle("Match the recorder’s preview grid")
        .setItems(
            labels,
            (d, which) -> {
              if (which == sizes.length) customSize();
              else {
                draft.resize(sizes[which], sizes[which]);
                refresh(-1);
              }
            })
        .setNegativeButton("Cancel", null)
        .show();
  }

  void customSize() {
    LinearLayout form = new LinearLayout(owner);
    form.setOrientation(LinearLayout.VERTICAL);
    EditText columns = dimension(form, "Columns", draft.columns),
        rows = dimension(form, "Rows", draft.rows);
    AlertDialog dialog =
        new AlertDialog.Builder(owner)
            .setTitle("Recorder grid size")
            .setView(form)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Apply", null)
            .create();
    dialog.setOnShowListener(
        d ->
            dialog
                .getButton(-1)
                .setOnClickListener(
                    v -> {
                      try {
                        draft.resize(
                            Integer.parseInt(columns.getText().toString()),
                            Integer.parseInt(rows.getText().toString()));
                        dialog.dismiss();
                        refresh(-1);
                      } catch (Exception invalid) {
                        columns.setError("Use 1–8 rows and columns");
                      }
                    }));
    dialog.show();
  }

  EditText dimension(LinearLayout form, String label, int value) {
    TextView heading = text(label, 13);
    form.addView(heading);
    EditText input = new EditText(owner);
    input.setText(Integer.toString(value));
    input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
    form.addView(input);
    return input;
  }

  void save() {
    try {
      RecorderProfile updated;
      if (enabled) updated = draft.build();
      else {
        updated = RecorderProfile.parse(profile.json());
        updated.combined = false;
      }
      if (callback.saved(updated)) dismiss();
    } catch (Exception error) {
      status.setText("Place every camera once before saving the overview.");
    }
  }
}
