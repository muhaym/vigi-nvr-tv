package com.fantacode.vigitv;

import java.util.*;
import org.json.*;

/** Recorder-specific data, independent of Activities and of any particular installation. */
final class RecorderProfile {
  static final int MAX_CHANNELS = 96;
  String host = "", label = "Recorder", certificate = "";
  RecorderPorts ports = RecorderPorts.defaults();
  int columns = 4, rows = 4;
  boolean combined = false;
  boolean overviewNeedsReview = false;
  final TreeMap<Integer, Camera> cameras = new TreeMap<>();

  static final class Camera {
    final int channel;
    String name;
    int cell;
    boolean lens;

    Camera(int id, String text, int position, boolean optical) {
      channel = id;
      name = text;
      cell = position;
      lens = optical;
    }
  }

  static String validHost(String value) {
    if (value == null) throw new IllegalArgumentException("Enter a recorder address");
    String host = value.trim().toLowerCase(Locale.ROOT);
    if (host.isEmpty()
        || host.length() > 253
        || !host.matches("[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?")
        || host.contains(".."))
      throw new IllegalArgumentException(
          "Enter a recorder IP address or hostname, without a URL or port");
    if (host.matches("[0-9.]+")) {
      String[] parts = host.split("\\.", -1);
      if (parts.length != 4) throw new IllegalArgumentException("Enter a valid IPv4 address");
      for (String part : parts)
        if (part.length() > 3 || Integer.parseInt(part) > 255)
          throw new IllegalArgumentException("Enter a valid IPv4 address");
    } else
      for (String label : host.split("\\.")) {
        if (label.length() > 63 || !label.matches("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?"))
          throw new IllegalArgumentException("Enter a valid recorder hostname");
      }
    return host;
  }

  String name(int id) {
    Camera camera = cameras.get(id);
    return camera == null ? "Camera " + id : camera.name;
  }

  String[] names() {
    String[] result = new String[MAX_CHANNELS];
    for (int i = 0; i < result.length; i++) result[i] = name(i + 1);
    return result;
  }

  int[] cells() {
    int[] result = new int[MAX_CHANNELS + 1];
    Arrays.fill(result, -1);
    for (Camera c : cameras.values()) result[c.channel] = c.cell;
    return result;
  }

  /** A stale overview must never make the recorder account or camera table unreadable. */
  boolean repairOverview() {
    boolean invalid = columns < 1 || columns > 8 || rows < 1 || rows > 8;
    if (invalid) {
      columns = 4;
      rows = 4;
    }
    Set<Integer> used = new HashSet<>();
    for (Camera camera : cameras.values()) {
      if (camera.cell < -1 || camera.cell >= columns * rows) {
        camera.cell = -1;
        invalid = true;
      }
      if (combined && (camera.cell < 0 || !used.add(camera.cell))) invalid = true;
    }
    if (invalid) {
      combined = false;
      overviewNeedsReview = true;
    }
    return invalid;
  }

  /** Discovery owns names and identities; only reuse overview positions after comparing tables. */
  void inheritOverview(RecorderProfile previous) {
    if (!host.equals(previous.host)) return;
    combined = previous.combined;
    overviewNeedsReview = previous.overviewNeedsReview;
    columns = previous.columns;
    rows = previous.rows;
    for (Camera camera : cameras.values()) {
      Camera old = previous.cameras.get(camera.channel);
      if (old != null) {
        camera.cell = old.cell;
        camera.lens = old.lens;
      }
    }
    if (previous.combined && !cameras.keySet().equals(previous.cameras.keySet())) {
      combined = false;
      overviewNeedsReview = true;
    }
    repairOverview();
  }

  JSONObject json() throws JSONException {
    JSONArray list = new JSONArray();
    for (Camera c : cameras.values())
      list.put(
          new JSONObject()
              .put("channel", c.channel)
              .put("name", c.name)
              .put("cell", c.cell)
              .put("lens", c.lens));
    return new JSONObject()
        .put("schema", 1)
        .put("host", host)
        .put("label", label)
        .put("certificate", certificate)
        .put(
            "ports",
            new JSONObject()
                .put("https", ports.https)
                .put("rtsp", ports.rtsp)
                .put("talk", ports.talk))
        .put("combined", combined)
        .put("overviewNeedsReview", overviewNeedsReview)
        .put("columns", columns)
        .put("rows", rows)
        .put("cameras", list);
  }

  static RecorderProfile parse(JSONObject json) throws JSONException {
    if (json.optInt("schema", 1) != 1) throw new JSONException("Unsupported profile schema");
    RecorderProfile profile = new RecorderProfile();
    profile.host = validHost(json.getString("host"));
    JSONObject ports = json.optJSONObject("ports");
    if (ports != null)
      profile.ports =
          new RecorderPorts(
              ports.optInt("https", 443), ports.optInt("rtsp", 554), ports.optInt("talk", 8443));
    profile.label = json.optString("label", "Recorder");
    profile.certificate = json.optString("certificate", "");
    profile.columns = json.optInt("columns", 4);
    profile.rows = json.optInt("rows", 4);
    profile.combined = json.optBoolean("combined", false);
    profile.overviewNeedsReview = json.optBoolean("overviewNeedsReview", false);
    JSONArray cameras = json.optJSONArray("cameras");
    if (cameras != null)
      for (int i = 0; i < cameras.length(); i++) {
        JSONObject item = cameras.getJSONObject(i);
        int id = item.getInt("channel"), cell = item.optInt("cell", -1);
        if (id < 1 || id > MAX_CHANNELS || profile.cameras.containsKey(id))
          throw new JSONException("Invalid camera identity");
        String name = item.optString("name", "Camera " + id).trim();
        profile.cameras.put(
            id,
            new Camera(
                id, name.isEmpty() ? "Camera " + id : name, cell, item.optBoolean("lens", false)));
      }
    profile.repairOverview();
    return profile;
  }
}
