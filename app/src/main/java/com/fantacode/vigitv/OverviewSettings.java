package com.fantacode.vigitv;

import android.app.*;
import android.widget.*;
import java.util.*;

/** Combined preview mappings are explicit, because recorder firmware can reorder cells. */
final class OverviewSettings {
  interface Result {
    void saved(RecorderProfile profile);
  }

  static void show(Activity owner, RecorderProfile profile, Result callback) {
    LinearLayout content = new LinearLayout(owner);
    content.setOrientation(LinearLayout.VERTICAL);
    content.setPadding(40, 16, 40, 16);
    TextView description = new TextView(owner);
    description.setText(
        "Individual streams show up to four cameras per page. Combined preview uses one decoder,"
            + " with less detail per camera. Configure its exact grid and camera order before"
            + " enabling it. This changes only the TV app.");
    content.addView(description);
    CheckBox combined = new CheckBox(owner);
    combined.setText("Use recorder combined preview (Channel Zero)");
    combined.setChecked(profile.combined);
    content.addView(combined);
    EditText
        columns = field(owner, content, "Grid columns (1–8)", Integer.toString(profile.columns)),
        rows = field(owner, content, "Grid rows (1–8)", Integer.toString(profile.rows));
    String[] cells = new String[profile.rows * profile.columns];
    Arrays.fill(cells, "0");
    for (RecorderProfile.Camera camera : profile.cameras.values())
      if (camera.cell >= 0 && camera.cell < cells.length)
        cells[camera.cell] = Integer.toString(camera.channel);
    EditText order =
        field(
            owner,
            content,
            "Channel IDs in cell order; 0 for empty",
            android.text.TextUtils.join(",", cells));
    TextView error = new TextView(owner);
    content.addView(error);
    AlertDialog dialog =
        new AlertDialog.Builder(owner)
            .setTitle("Overview quality")
            .setView(content)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create();
    dialog.setOnShowListener(
        d ->
            dialog
                .getButton(-1)
                .setOnClickListener(
                    v -> {
                      try {
                        RecorderProfile updated = RecorderProfile.parse(profile.json());
                        updated.combined = combined.isChecked();
                        if (updated.combined) {
                          int width = Integer.parseInt(columns.getText().toString()),
                              height = Integer.parseInt(rows.getText().toString());
                          if (width < 1 || width > 8 || height < 1 || height > 8)
                            throw new IllegalArgumentException("Grid size must be 1–8");
                          String[] channels = order.getText().toString().split(",", -1);
                          if (channels.length != width * height)
                            throw new IllegalArgumentException(
                                "Enter one channel ID for every grid cell");
                          Set<Integer> used = new HashSet<>();
                          for (RecorderProfile.Camera camera : updated.cameras.values())
                            camera.cell = -1;
                          for (int cell = 0; cell < channels.length; cell++) {
                            int id = Integer.parseInt(channels[cell].trim());
                            if (id == 0) continue;
                            if (!updated.cameras.containsKey(id) || !used.add(id))
                              throw new IllegalArgumentException(
                                  "Channel IDs must exist and appear only once");
                            updated.cameras.get(id).cell = cell;
                          }
                          if (!used.containsAll(updated.cameras.keySet()))
                            throw new IllegalArgumentException(
                                "Map every camera before enabling combined preview");
                          updated.columns = width;
                          updated.rows = height;
                        }
                        callback.saved(updated);
                        dialog.dismiss();
                      } catch (Exception invalid) {
                        error.setText(invalid.getMessage());
                      }
                    }));
    dialog.show();
  }

  static EditText field(Activity owner, LinearLayout content, String hint, String text) {
    EditText field = new EditText(owner);
    field.setHint(hint);
    field.setText(text);
    field.setSingleLine();
    content.addView(field);
    return field;
  }
}
