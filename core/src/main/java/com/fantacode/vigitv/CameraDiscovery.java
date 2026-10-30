package com.fantacode.vigitv;

import java.net.URLDecoder;
import java.util.*;
import org.json.*;

/** Parses only camera identifiers and labels, never camera credentials or addresses. */
final class CameraDiscovery {
  static TreeMap<Integer, RecorderProfile.Camera> parse(JSONObject response) throws Exception {
    TreeMap<Integer, RecorderProfile.Camera> cameras = new TreeMap<>();
    walk(response, cameras, 0, new int[] {4096});
    if (cameras.isEmpty())
      throw new java.io.IOException(
          "Camera discovery is not supported by this firmware. Enter a camera count to configure"
              + " channels manually.");
    return cameras;
  }

  static void walk(
      Object value, TreeMap<Integer, RecorderProfile.Camera> result, int depth, int[] budget)
      throws Exception {
    if (--budget[0] < 0)
      throw new java.io.IOException("Recorder camera response exceeded parsing limit");
    if (depth > 8) return;
    if (value instanceof JSONArray) {
      JSONArray list = (JSONArray) value;
      for (int i = 0; i < list.length(); i++) walk(list.opt(i), result, depth + 1, budget);
      return;
    }
    if (!(value instanceof JSONObject)) return;
    JSONObject object = (JSONObject) value;
    int id = object.optInt("id", object.optInt("channel", 0));
    if (id >= 1
        && id <= RecorderProfile.MAX_CHANNELS
        && (object.has("name") || object.has("alias"))) {
      String raw = object.optString("name", object.optString("alias", "Camera " + id)), name = raw;
      try {
        name = URLDecoder.decode(raw.replace("+", "%2B"), "UTF-8");
      } catch (IllegalArgumentException ignored) {
      }
      name = name.trim();
      if (name.isEmpty()) name = "Camera " + id;
      if (name.length() > 256) name = name.substring(0, 256);
      if (result.containsKey(id) && !result.get(id).name.equals(name))
        throw new java.io.IOException("Recorder returned conflicting camera identifiers");
      result.put(id, new RecorderProfile.Camera(id, name, -1, false));
      return;
    }
    Iterator<String> keys = object.keys();
    while (keys.hasNext()) walk(object.opt(keys.next()), result, depth + 1, budget);
  }
}
