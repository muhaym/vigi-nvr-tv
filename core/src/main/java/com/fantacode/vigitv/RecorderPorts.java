package com.fantacode.vigitv;

/** Recorder service ports are deployment configuration, never assumptions in a player or panel. */
final class RecorderPorts {
  final int https, rtsp, talk;

  RecorderPorts(int web, int video, int nativeTalk) {
    https = valid(web);
    rtsp = valid(video);
    talk = valid(nativeTalk);
  }

  static int valid(int port) {
    if (port < 1 || port > 65535)
      throw new IllegalArgumentException("Ports must be between 1 and 65535");
    return port;
  }

  static RecorderPorts defaults() {
    return new RecorderPorts(443, 554, 8443);
  }
}
