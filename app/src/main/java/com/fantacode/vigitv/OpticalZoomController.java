package com.fantacode.vigitv;

import android.content.Context;
import android.os.*;
import java.io.*;
import java.security.cert.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.JSONObject;

/** Recorder-mediated lens pulses, bounded in duration and followed by stop even on departure. */
final class OpticalZoomController implements AutoCloseable {
  interface Listener {
    void result(int channel, boolean supported, String message);
  }

  final Context context;
  final String host, user, password;
  final TalkController.Authorization authorization;
  final Listener listener;
  final Handler ui = new Handler(Looper.getMainLooper());
  final ExecutorService worker = Executors.newSingleThreadExecutor();
  final Map<Integer, Boolean> capabilities = new HashMap<>();
  volatile boolean closed, busy;
  volatile int generation;
  NvrWebControl api;

  OpticalZoomController(
      Context c, String h, String u, String p, TalkController.Authorization auth, Listener result) {
    context = c;
    host = h;
    user = u;
    password = p;
    authorization = auth;
    listener = result;
  }

  NvrWebControl api() throws Exception {
    if (api == null) api = RecorderServices.web(context, host, user, password);
    return api;
  }

  boolean alive(int epoch) {
    return !closed && epoch == generation && authorization.allowed();
  }

  void report(int channel, boolean supported, String message, int epoch) {
    ui.post(
        () -> {
          if (alive(epoch)) listener.result(channel, supported, message);
        });
  }

  void inspect(int channel) {
    if (closed) return;
    final int epoch = ++generation;
    worker.execute(
        () -> {
          if (!alive(epoch)) return;
          try {
            Boolean supported = capabilities.get(channel);
            if (supported == null) {
              JSONObject result = api().capability(channel);
              supported = "1".equals(result.optString("zoom_supported"));
              capabilities.put(channel, supported);
            }
            report(channel, supported, supported ? "Optical lens" : "Digital zoom", epoch);
          } catch (Exception error) {
            report(channel, false, "Lens controls unavailable", epoch);
          }
        });
  }

  void pulse(int channel, boolean zoomIn) {
    if (closed || busy || !authorization.allowed()) return;
    busy = true;
    final int epoch = ++generation;
    worker.execute(
        () -> {
          boolean attempted = false, stopped = true;
          String message = "Lens adjusted";
          try {
            if (!alive(epoch) || !Boolean.TRUE.equals(capabilities.get(channel))) return;
            report(channel, true, "Adjusting optical lens…", epoch);
            attempted = true;
            api().move(channel, zoomIn ? 10 : 11);
            long until = SystemClock.elapsedRealtime() + 250;
            while (alive(epoch) && SystemClock.elapsedRealtime() < until) Thread.sleep(20);
          } catch (Exception error) {
            message = "Recorder did not confirm lens adjustment";
          } finally {
            if (attempted) {
              stopped = false;
              for (int attempt = 0; attempt < 3 && !stopped; attempt++)
                try {
                  api().move(channel, 0);
                  stopped = true;
                } catch (Exception ignored) {
                }
            }
            busy = false;
            if (attempted || alive(epoch))
              report(
                  channel,
                  Boolean.TRUE.equals(capabilities.get(channel)),
                  stopped ? message : "Lens stop was not confirmed. Check the camera view.",
                  generation);
          }
        });
  }

  void stop() {
    generation++;
  }

  public void close() {
    if (closed) return;
    closed = true;
    stop();
    worker.execute(
        () -> {
          if (api != null) api.close();
        });
    worker.shutdown();
  }
}
