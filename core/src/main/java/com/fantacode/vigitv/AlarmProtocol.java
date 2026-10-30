package com.fantacode.vigitv;

import java.io.IOException;
import org.json.*;

/** Manual camera alarm commands, distinct from recording / automatic alarm configuration. */
final class AlarmProtocol {
  static int wireChannel(int channel) {
    if (channel < 1 || channel > RecorderProfile.MAX_CHANNELS)
      throw new IllegalArgumentException("Invalid recorder camera number");
    return channel - 1;
  }

  static JSONObject capabilities(int channel) throws JSONException {
    return new JSONObject()
        .put("method", "do")
        .put(
            "chm",
            new JSONObject()
                .put(
                    "chm_get_msg_alarm_list",
                    new JSONObject().put("chn_id", Integer.toString(wireChannel(channel)))));
  }

  static JSONObject command(int channel, boolean start) throws JSONException {
    return new JSONObject()
        .put("method", "do")
        .put(
            "chm",
            new JSONObject()
                .put(
                    "manual_msg_alarm",
                    new JSONObject()
                        .put("action", start ? "start" : "stop")
                        .put("chn_id", wireChannel(channel))));
  }

  static final class Capabilities {
    final boolean sound, light;

    Capabilities(boolean siren, boolean lamp) {
      sound = siren;
      light = lamp;
    }

    boolean supported() {
      return sound || light;
    }

    String label() {
      return sound && light
          ? "Siren and light"
          : sound ? "Siren" : light ? "Light" : "No manual alarm output";
    }
  }

  static Capabilities parse(JSONObject response) throws IOException {
    JSONObject result = null;
    for (int depth = 0; response != null && depth < 4; depth++) {
      result = response.optJSONObject("msg_alarm_list");
      if (result == null && response.optJSONObject("chm") != null)
        result = response.optJSONObject("chm").optJSONObject("msg_alarm_list");
      if (result != null) break;
      response = response.optJSONObject("data");
    }
    if (result == null || (!result.has("sound") && !result.has("light")))
      throw new IOException("Recorder manual alarm capabilities were not recognized");
    return new Capabilities(flag(result, "sound"), flag(result, "light"));
  }

  static boolean flag(JSONObject object, String name) throws IOException {
    Object value = object.opt(name);
    if (value == null || value == JSONObject.NULL) return false;
    if (Boolean.TRUE.equals(value) || "1".equals(value.toString())) return true;
    if (Boolean.FALSE.equals(value) || "0".equals(value.toString())) return false;
    throw new IOException("Recorder manual alarm capability was not recognized");
  }
}
