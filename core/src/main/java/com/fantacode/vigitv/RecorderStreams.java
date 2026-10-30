package com.fantacode.vigitv;

import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.*;

/** VIGI NVR stream paths. Never log returned URLs: they contain credentials. */
final class RecorderStreams {
  static void channel(int value) {
    if (value < 1 || value > RecorderProfile.MAX_CHANNELS)
      throw new IllegalArgumentException("Invalid recorder camera number");
  }

  static String component(String value) {
    try {
      return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
    } catch (java.io.UnsupportedEncodingException impossible) {
      throw new AssertionError(impossible);
    }
  }

  static String url(String host, int port, String user, String password, String path) {
    return "rtsp://"
        + component(user)
        + ":"
        + component(password)
        + "@"
        + RecorderProfile.validHost(host)
        + ":"
        + RecorderPorts.valid(port)
        + path;
  }

  static String live(
      String host, int port, String user, String password, int camera, boolean main) {
    channel(camera);
    return url(host, port, user, password, "/live/" + camera + "/" + (main ? 1 : 2) + "/avm");
  }

  static String combined(String host, int port, String user, String password) {
    return url(host, port, user, password, "/live/0/1/avm");
  }

  static String replay(
      String host, int port, String user, String password, int camera, long start, long end) {
    channel(camera);
    if (start <= 0 || end <= start)
      throw new IllegalArgumentException("Choose a recorded time range");
    return url(
        host,
        port,
        user,
        password,
        "/replay/" + camera + "/1/avm?starttime=" + utc(start) + "&endtime=" + utc(end));
  }

  static String utc(long ms) {
    SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd't'HHmmss'z'", Locale.ROOT);
    format.setTimeZone(TimeZone.getTimeZone("UTC"));
    return format.format(new Date(ms));
  }
}
