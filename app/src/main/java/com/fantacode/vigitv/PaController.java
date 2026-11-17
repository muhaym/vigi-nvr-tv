package com.fantacode.vigitv;

import android.content.Context;
import android.os.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Explicit USB microphone announcements through the saved NVR to selected speakers. */
final class PaController {
  static final int TEST_MS = 10000, ANNOUNCEMENT_MS = 300000;

  interface Listener {
    void state(boolean busy, String text);

    void speaker(int channel, String text);

    void level(int value);
  }

  final Context context;
  final TalkController.Authorization auth;
  final Listener listener;
  final Handler ui = new Handler(Looper.getMainLooper());
  volatile Session current;

  static final class Session {
    final AtomicBoolean cancelled = new AtomicBoolean();
    final int[] targets;
    final Map<Integer, AtomicInteger> frames = new ConcurrentHashMap<>();
    final Map<Integer, String> errors = new ConcurrentHashMap<>();
    final Set<Integer> connected =
        Collections.newSetFromMap(new ConcurrentHashMap<Integer, Boolean>());
    volatile SpeakerBroadcast broadcast;
    volatile UsbMicSource source;
    volatile boolean timedOut;
    Runnable deadline;

    Session(int[] selected) {
      targets = Arrays.copyOf(selected, selected.length);
      for (int id : targets) frames.put(id, new AtomicInteger());
    }
  }

  PaController(Context context, TalkController.Authorization auth, Listener listener) {
    this.context = context;
    this.auth = auth;
    this.listener = listener;
  }

  boolean busy() {
    return current != null;
  }

  boolean alive(Session s) {
    return current == s
        && !s.cancelled.get()
        && auth.allowed()
        && (s.broadcast == null || !s.broadcast.closed);
  }

  void post(Session s, Runnable action) {
    ui.post(
        () -> {
          if (current == s) action.run();
        });
  }

  void closeSource(Session s) {
    new Thread(
            () -> {
              if (s.source != null) s.source.close();
            },
            "pa-mic-stop")
        .start();
  }

  void stop() {
    Session s = current;
    if (s == null) return;
    s.cancelled.set(true);
    if (s.broadcast != null) s.broadcast.close();
    closeSource(s);
    listener.state(true, "Stopping all speakers…");
  }

  void start(
      String host,
      String username,
      String password,
      int[] selected,
      boolean checkOnly,
      int durationMs) {
    if (busy() || !auth.allowed()) return;
    if (durationMs != TEST_MS && durationMs != ANNOUNCEMENT_MS)
      throw new IllegalArgumentException("Unsupported announcement duration");
    if (selected == null || selected.length == 0) return;
    Session s = new Session(selected);
    current = s;
    listener.state(
        true,
        (checkOnly ? "Checking " : "Preparing ") + s.targets.length + " speakers · microphone off");
    for (int id : s.targets) listener.speaker(id, "Waiting…");
    new Thread(
            () -> {
              String result = "Stopped · microphone off";
              boolean sent = false, checkPassed = false;
              try {
                if (!checkOnly) s.source = new UsbMicSource(context);
                SpeakerBroadcast broadcast =
                    new SpeakerBroadcast(
                        s.targets,
                        channel -> {
                          NativeNvrSession transport = RecorderServices.talk(context, host);
                          return new SpeakerBroadcast.Sink() {
                            public void open() throws Exception {
                              if (!alive(s)) throw new java.io.IOException("Broadcast stopped");
                              post(s, () -> listener.speaker(channel, "Connecting…"));
                              transport.login(username, password);
                              if (!alive(s)) throw new java.io.IOException("Broadcast stopped");
                              transport.allocateTalk(channel, "half_duplex");
                            }

                            public void send(byte[] frame) throws Exception {
                              if (!alive(s)) throw new java.io.IOException("Broadcast stopped");
                              transport.sendAudio(frame);
                              s.frames.get(channel).incrementAndGet();
                            }

                            public void cancel() {
                              transport.cancel();
                            }

                            public void close() {
                              transport.close();
                            }
                          };
                        },
                        new SpeakerBroadcast.Observer() {
                          public void connected(int channel) {
                            s.connected.add(channel);
                            android.util.Log.i(
                                "PaBroadcast", "NVR talk allocation accepted camera=" + channel);
                            post(s, () -> listener.speaker(channel, "Connection accepted"));
                          }

                          public void failed(Exception error) {
                            if (error instanceof SpeakerBroadcast.SpeakerFailure) {
                              int id = ((SpeakerBroadcast.SpeakerFailure) error).channel;
                              String text = "Connection unavailable";
                              Throwable cause = error.getCause();
                              if (cause != null
                                  && cause.getMessage() != null
                                  && cause.getMessage().startsWith("Recorder"))
                                text = cause.getMessage();
                              s.errors.put(id, text);
                            }
                            // Unblock microphone reads as soon as any network sender fails.
                            if (s.source != null) closeSource(s);
                          }
                        });
                s.broadcast = broadcast;
                if (!alive(s)) return;
                broadcast.connect();
                if (!alive(s)) return;
                if (checkOnly) {
                  checkPassed = true;
                  result =
                      "All "
                          + s.targets.length
                          + " connections accepted · no microphone audio sent";
                } else {
                  broadcast.start();
                  if (!alive(s)) return;
                  s.source.open(16000);
                  if (!alive(s)) return;
                  s.deadline =
                      () -> {
                        if (current == s && !s.cancelled.get()) {
                          s.timedOut = true;
                          stop();
                        }
                      };
                  ui.postDelayed(s.deadline, durationMs);
                  post(
                      s,
                      () ->
                          listener.state(
                              true,
                              "Microphone on · "
                                  + s.targets.length
                                  + " speakers · "
                                  + (durationMs == TEST_MS
                                      ? "10-second test"
                                      : "Stop all to finish; five-minute limit")));
                  android.util.Log.i(
                      "PaBroadcast", "USB transmission started speakerCount=" + s.targets.length);
                  NativeTalkMux mux = new NativeTalkMux();
                  MicSignal signal = new MicSignal();
                  short[] samples = new short[640];
                  long until = SystemClock.elapsedRealtime() + durationMs;
                  while (alive(s) && SystemClock.elapsedRealtime() < until) {
                    broadcast.check();
                    int filled = 0;
                    while (filled < samples.length && alive(s))
                      filled += s.source.read(samples, filled, samples.length - filled);
                    if (!alive(s)) break;
                    signal.update(samples, filled);
                    int level = signal.absoluteLevel();
                    post(s, () -> listener.level(level));
                    broadcast.send(mux.frame(samples, filled));
                    sent = true;
                  }
                  if (!s.cancelled.get()) broadcast.check();
                  result =
                      durationMs == TEST_MS
                          ? "Test ended · microphone off"
                          : "Five-minute limit reached · microphone off";
                }
              } catch (Exception error) {
                if (!s.cancelled.get()) {
                  String detail = error.getMessage();
                  result =
                      error instanceof SecurityException
                          ? "Allow microphone access to announce."
                          : s.broadcast != null && s.broadcast.failure != null
                              ? "A speaker connection failed · all speakers stopped. Review the"
                                    + " list below."
                              : detail != null
                                      && (detail.startsWith("Connect a USB")
                                          || detail.startsWith("The USB")
                                          || detail.startsWith("USB microphone"))
                                  ? detail + " · all speakers stopped"
                                  : sent
                                      ? "Announcement interrupted · all speakers stopped"
                                      : "Could not prepare all speakers · no microphone audio sent";
                }
              } finally {
                if (s.deadline != null) ui.removeCallbacks(s.deadline);
                if (s.broadcast != null) s.broadcast.close();
                if (s.source != null) s.source.close();
                final String end =
                    s.timedOut
                        ? (durationMs == TEST_MS
                            ? "Test ended · microphone off"
                            : "Five-minute limit reached · microphone off")
                        : s.cancelled.get() ? "Stopped · microphone off" : result;
                final boolean accepted = checkPassed;
                for (int id : s.targets)
                  android.util.Log.i(
                      "PaBroadcast", "Stopped camera=" + id + " frames=" + s.frames.get(id).get());
                post(
                    s,
                    () -> {
                      current = null;
                      listener.level(0);
                      for (int id : s.targets) {
                        int frames = s.frames.get(id).get();
                        listener.speaker(
                            id,
                            s.errors.containsKey(id)
                                ? s.errors.get(id)
                                : frames > 0
                                    ? "Stopped · " + frames + " frames sent"
                                    : accepted
                                        ? "Connection accepted"
                                        : s.connected.contains(id)
                                            ? "Closed · no audio sent"
                                            : "Not checked");
                      }
                      listener.state(false, end);
                    });
              }
            },
            "pa-announcement")
        .start();
  }
}
