package com.fantacode.vigitv;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.IOException;
import java.util.Map;

/** An unconfirmed stop survives process death; no account or audio is stored here. */
final class AlarmSafety {
  static final String PREFIX = "manual_alarm_pending/";

  static SharedPreferences prefs(Context context) {
    return context.getSharedPreferences("native", 0);
  }

  static String key(String host, int channel) {
    AlarmProtocol.wireChannel(channel);
    return PREFIX + host + "/" + channel;
  }

  static boolean pending(Context context, String host, int channel) {
    return prefs(context).getBoolean(key(host, channel), false);
  }

  static boolean any(Context context) {
    for (Map.Entry<String, ?> entry : prefs(context).getAll().entrySet())
      if (entry.getKey().startsWith(PREFIX) && Boolean.TRUE.equals(entry.getValue())) return true;
    return false;
  }

  static String pendingChannels(Context context, String host) {
    StringBuilder result = new StringBuilder();
    for (int channel = 1; channel <= RecorderProfile.MAX_CHANNELS; channel++)
      if (pending(context, host, channel)) {
        if (result.length() > 0) result.append(", ");
        result.append(channel);
      }
    return result.length() == 0 ? "another recorder" : "camera channel(s) " + result;
  }

  static void mark(Context context, String host, int channel) throws IOException {
    if (!prefs(context).edit().putBoolean(key(host, channel), true).commit())
      throw new IOException("Could not save alarm safety state");
  }

  static void clear(Context context, String host, int channel) {
    prefs(context).edit().remove(key(host, channel)).commit();
  }
}
