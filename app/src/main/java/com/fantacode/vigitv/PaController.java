package com.fantacode.vigitv;

import android.content.Context;
import android.os.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Explicit, ten-second, two-speaker USB microphone experiment through the saved NVR only. */
final class PaController {
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
    volatile TwoSpeakerBroadcast broadcast;
    volatile UsbMicSource source;
    final AtomicInteger firstFrames = new AtomicInteger(), secondFrames = new AtomicInteger();
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
    return current == s && !s.cancelled.get() && auth.allowed();
  }

  void post(Session s, Runnable action) {
    ui.post(
        () -> {
          if (current == s) action.run();
        });
  }

  void stop() {
    Session s = current;
    if (s == null) return;
    s.cancelled.set(true);
    if (s.broadcast != null) s.broadcast.close();
    new Thread(
            () -> {
              if (s.source != null) s.source.close();
            },
            "pa-mic-stop")
        .start();
    listener.state(true, "Stopping both speakers…");
  }

  void start(
      String host, String username, String password, int first, int second, boolean checkOnly) {
    if (busy() || !auth.allowed()) return;
    Session s = new Session();
    current = s;
    listener.state(
        true,
        checkOnly
            ? "Checking two NVR talk sessions · microphone off"
            : "Preparing both speakers · microphone off");
    listener.speaker(first, "Connecting…");
    listener.speaker(second, "Waiting…");
    new Thread(
            () -> {
              String result = "Stopped · microphone off";
              boolean sent = false;
              try {
                // Verify a USB input exists, but do not capture until both talk sessions are
                // accepted.
                if (!checkOnly) s.source = new UsbMicSource(context);
                TwoSpeakerBroadcast broadcast =
                    new TwoSpeakerBroadcast(
                        first,
                        second,
                        channel -> {
                          NativeNvrSession transport = RecorderServices.talk(context, host);
                          return new TwoSpeakerBroadcast.Sink() {
                            public void open() throws Exception {
                              if (!alive(s)) throw new java.io.IOException("Broadcast stopped");
                              transport.login(username, password);
                              if (!alive(s)) throw new java.io.IOException("Broadcast stopped");
                              transport.allocateTalk(channel, "half_duplex");
                            }

                            public void send(byte[] frame) throws Exception {
                              if (!alive(s)) throw new java.io.IOException("Broadcast stopped");
                              transport.sendAudio(frame);
                              (channel == first ? s.firstFrames : s.secondFrames).incrementAndGet();
                            }

                            public void cancel() {
                              transport.cancel();
                            }

                            public void close() {
                              transport.close();
                            }
                          };
                        },
                        channel -> {
                          android.util.Log.i(
                              "PaPrototype", "NVR talk allocation accepted camera=" + channel);
                          post(s, () -> listener.speaker(channel, "NVR session accepted"));
                        });
                s.broadcast = broadcast;
                if (!alive(s)) return;
                broadcast.connect();
                if (!alive(s)) return;
                if (checkOnly) {
                  result = "Both NVR talk requests accepted. No microphone audio was sent.";
                } else {
                  broadcast.start();
                  if (!alive(s)) return;
                  s.source.open(16000);
                  if (!alive(s)) return;
                  post(
                      s,
                      () ->
                          listener.state(
                              true, "Microphone on · sending to both speakers · 10 seconds"));
                  android.util.Log.i(
                      "PaPrototype", "USB transmission started cameras=" + first + "," + second);
                  NativeTalkMux mux = new NativeTalkMux();
                  MicSignal signal = new MicSignal();
                  short[] samples = new short[640];
                  long until = SystemClock.elapsedRealtime() + 10000;
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
                  broadcast.check();
                  result = "Ten-second test ended · microphone off";
                }
              } catch (Exception error) {
                if (!s.cancelled.get()) {
                  String detail = error.getMessage();
                  result =
                      error instanceof SecurityException
                          ? "Allow microphone access to run the test."
                          : detail != null
                                  && (detail.startsWith("Recorder")
                                      || detail.startsWith("Connect a USB")
                                      || detail.startsWith("The USB")
                                      || detail.startsWith("USB microphone"))
                              ? detail + " · both speakers stopped"
                              : sent
                                  ? "Speaker connection interrupted · both speakers stopped"
                                  : "Could not prepare both speakers · no microphone audio sent";
                }
              } finally {
                if (s.broadcast != null) s.broadcast.close();
                if (s.source != null) s.source.close();
                final String end = s.cancelled.get() ? "Stopped · microphone off" : result;
                android.util.Log.i(
                    "PaPrototype",
                    "Test stopped camera="
                        + first
                        + " frames="
                        + s.firstFrames.get()
                        + " camera="
                        + second
                        + " frames="
                        + s.secondFrames.get());
                post(
                    s,
                    () -> {
                      current = null;
                      listener.level(0);
                      listener.speaker(first, "Closed · " + s.firstFrames.get() + " frames sent");
                      listener.speaker(second, "Closed · " + s.secondFrames.get() + " frames sent");
                      listener.state(false, end);
                    });
              }
            },
            "pa-two-speaker-test")
        .start();
  }
}
