package com.fantacode.vigitv;

import java.util.*;
import org.json.*;

/** Ordered user groups contain recorder channel identities, never camera addresses. */
final class CameraGroup {
  String name;
  final ArrayList<Integer> ids = new ArrayList<>();

  CameraGroup(String label, int... channels) {
    name = label;
    for (int channel : channels) ids.add(channel);
  }

  static ArrayList<CameraGroup> load(String json, Set<Integer> available) {
    ArrayList<CameraGroup> groups = new ArrayList<>();
    try {
      JSONArray list = new JSONArray(json);
      if (list.length() > 128) throw new JSONException("Too many groups");
      for (int i = 0; i < list.length(); i++) {
        JSONObject item = list.getJSONObject(i);
        CameraGroup group = new CameraGroup(item.getString("name"));
        JSONArray channels = item.getJSONArray("ids");
        for (int j = 0; j < channels.length(); j++) {
          int id = channels.getInt(j);
          if (available.contains(id) && !group.ids.contains(id)) group.ids.add(id);
        }
        groups.add(group);
      }
    } catch (JSONException invalid) {
      groups.clear();
    }
    if (groups.isEmpty()) {
      CameraGroup all = new CameraGroup("All cameras");
      all.ids.addAll(new TreeSet<>(available));
      groups.add(all);
    }
    return groups;
  }

  static String save(List<CameraGroup> groups) throws JSONException {
    JSONArray result = new JSONArray();
    for (CameraGroup group : groups)
      result.put(new JSONObject().put("name", group.name).put("ids", new JSONArray(group.ids)));
    return result.toString();
  }
}
