package com.fantacode.vigitv;

import android.content.Context;
import android.media.*;
import android.os.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Foreground, explicitly started capture. Microphone test never opens a network socket. */
final class TalkController {

  interface Listener {
    void state(boolean active, String message);

    void level(int percent);
  }

  interface Authorization {
    boolean allowed();
  }

  final Context context;
  final Handler ui = new Handler(Looper.getMainLooper());
  final Listener listener;
  final Authorization auth;

  volatile Session current;

  final class Session {
    final AtomicBoolean cancelled = new AtomicBoolean();
    boolean duplex;
    volatile NativeNvrSession transport;
    volatile UsbMicSource source;
  }

  TalkController(Context c, Authorization a, Listener l) {
    context = c;
    auth = a;
    listener = l;
  }

  boolean active() {
    return current != null;
  }

  boolean duplexActive() {
    Session s = current;
    return s != null && s.duplex;
  }

  void post(Session s, boolean active, String text) {
    ui.post(
        () -> {
          if (current == s) listener.state(active, text);
        });
  }

  void stop() {
    Session old = current;
    current = null;
    if (old != null) {
      old.cancelled.set(true);
      if (old.transport != null) old.transport.cancel();
      new Thread(() -> closeRecorder(old), "vigi-mic-close").start();
    }
    listener.level(0);
    if (old != null) listener.state(false, "Ready · listening");
  }

  void closeRecorder(Session s) {
    UsbMicSource source = s.source;
    if (source != null) source.close();
  }

  boolean alive(Session s) {
    return current == s && !s.cancelled.get() && auth.allowed();
  }

  void start(String host, String user, String password, int channel, boolean test) {
    start(host, user, password, channel, test, false);
  }

  void start(String host, String user, String password, int channel, boolean test, boolean duplex) {

    if (current != null || !auth.allowed()) return;
    Session s = new Session();
    s.duplex = duplex && !test;
    current = s;
    listener.state(
        true, test ? "Microphone test · no audio is sent" : "Connecting to camera speaker…");

    new Thread(
            () -> {
              String end = "Ready · listening";
              MicSignal signal = new MicSignal();
              try {

                s.source = new UsbMicSource(context);
                if (!alive(s)) return;

                NativeTalkMux mux = new NativeTalkMux();
                if (!test) {
                  NativeNvrSession t = RecorderServices.talk(context, host);
                  s.transport = t;
                  if (!alive(s)) return;
                  t.login(user, password);
                  if (!alive(s)) return;
                  android.util.Log.i(
                      "VigiTalk",
                      "Requesting recorder talk: camera="
                          + channel
                          + ", native index="
                          + NativeNvrSession.nativeChannel(channel));
                  t.allocateTalk(channel, s.duplex ? "aec" : "half_duplex");
                  if (!alive(s)) return;
                }

                s.source.open(16000);
                if (!alive(s)) return;

                post(
                    s,
                    true,
                    test
                        ? "USB microphone test - stay quiet for one second"
                        : s.duplex
                            ? "Microphone on · talk and listen · End call to stop"
                            : "Microphone on · sending audio to camera");
                short[] samples = new short[640];
                long started = SystemClock.elapsedRealtime();

                while (alive(s)
                    && SystemClock.elapsedRealtime() - started
                        < (test ? 10000 : s.duplex ? 300000 : 60000)) {

                  int filled = 0;
                  while (filled < samples.length && alive(s)) {
                    int got = s.source.read(samples, filled, samples.length - filled);
                    if (got <= 0) throw new Exception("Microphone input stopped");
                    filled += got;
                  }
                  if (!alive(s)) break;

                  int relativeLevel = signal.update(samples, filled);
                  if (test && signal.frames == 30)
                    post(s, true, "Speak into the USB microphone now · watch the meter");
                  int level = test ? relativeLevel : signal.absoluteLevel();
                  ui.post(
                      () -> {
                        if (current == s) listener.level(level);
                      });
                  if (!test && alive(s)) s.transport.sendAudio(mux.frame(samples, filled));
                }

                end =
                    test
                        ? signal.result()
                        : s.duplex
                            ? "Call ended after five minutes · listening"
                            : "Microphone stopped after one minute · listening";

              } catch (SecurityException e) {
                end = "Microphone permission is needed. Allow it in app settings.";
              } catch (Exception e) {
                if (!s.cancelled.get()) {
                  String reason = e.getMessage();
                  end =
                      e instanceof java.io.IOException
                          ? (reason != null
                                  && (reason.startsWith("Recorder")
                                      || reason.startsWith("This camera")
                                      || reason.startsWith("USB microphone")
                                      || reason.startsWith("Connect a USB")
                                      || reason.startsWith("The USB"))
                              ? reason
                              : "Talk connection lost. Start talking again to retry.")
                          : "No usable microphone input. Check the USB microphone connection.";
                }
              } finally {
                if (s.transport != null) s.transport.close();
                closeRecorder(s);
                final String message = end;
                ui.post(
                    () -> {
                      if (current == s) {
                        current = null;
                        listener.level(0);
                        listener.state(false, message);
                      }
                    });
              }
            },
            test ? "vigi-mic-test" : "vigi-talk")
        .start();
  }
}
