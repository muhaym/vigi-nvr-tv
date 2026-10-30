package com.fantacode.vigitv;

import android.content.Context;
import android.os.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Recorder-only manual alarms; closing the UI requests stop, never another start. */
final class AlarmController implements AutoCloseable {
  interface Listener {
    void state(
        AlarmProtocol.Capabilities capabilities, String message, boolean active, boolean busy);
  }

  final Context context;
  final String host, user, password;
  final int channel;
  final TalkController.Authorization authorization;
  final Listener listener;
  final Handler ui = new Handler(Looper.getMainLooper());
  final ExecutorService worker = Executors.newSingleThreadExecutor();
  volatile boolean closed, busy;
  volatile AtomicBoolean running;
  volatile AlarmProtocol.Capabilities capabilities;
  NvrWebControl api;

  AlarmController(
      Context c,
      String h,
      String u,
      String secret,
      int camera,
      TalkController.Authorization auth,
      Listener result) {
    context = c.getApplicationContext();
    host = h;
    user = u;
    password = secret;
    channel = camera;
    AlarmProtocol.wireChannel(camera);
    authorization = auth;
    listener = result;
  }

  NvrWebControl api() throws Exception {
    if (api == null) api = RecorderServices.web(context, host, user, password);
    api.login();
    return api;
  }

  boolean allowed() {
    return !closed && authorization.allowed();
  }

  boolean hasRun() {
    return running != null;
  }

  void report(String message, boolean active, boolean working) {
    ui.post(
        () -> {
          if (allowed()) listener.state(capabilities, message, active, working);
        });
  }

  void inspect() {
    if (!allowed() || busy) return;
    busy = true;
    report("Checking camera alarm outputs…", false, true);
    worker.execute(
        () -> {
          try {
            if (!allowed()) return;
            capabilities = api().alarmCapabilities(channel);
            report(
                capabilities.supported()
                    ? (AlarmSafety.any(context)
                        ? "Stop needs confirmation for "
                            + AlarmSafety.pendingChannels(context, host)
                            + ". Open that camera and choose Stop alarm."
                        : "Ready · " + capabilities.label())
                    : "No manual siren or light is exposed by this camera.",
                false,
                false);
          } catch (Exception error) {
            report("Could not read this camera's manual alarm capabilities.", false, false);
          } finally {
            busy = false;
          }
        });
  }

  void start() {
    if (!allowed() || busy || capabilities == null || !capabilities.supported()) return;
    if (AlarmSafety.any(context)) {
      report("Confirm the previous alarm is stopped before starting another.", false, false);
      return;
    }
    busy = true;
    AtomicBoolean cancel = new AtomicBoolean();
    running = cancel;
    report("Starting manual alarm…", false, true);
    worker.execute(
        () -> {
          boolean marked = false;
          try {
            api();
            if (!allowed() || cancel.get()) return;
            AlarmSafety.mark(context, host, channel);
            marked = true;
            ManualAlarmRun.Result result =
                ManualAlarmRun.execute(
                    (camera, start) -> api.manualAlarm(camera, start),
                    channel,
                    10000,
                    cancel,
                    () -> allowed(),
                    SystemClock::elapsedRealtime,
                    Thread::sleep,
                    seconds -> report("Alarm active · stop in " + seconds + "s", true, true));
            if (!result.attempted || result.stopped) {
              AlarmSafety.clear(context, host, channel);
              report(
                  !result.attempted
                      ? "Alarm cancelled before starting."
                      : result.started
                          ? "Manual alarm stopped."
                          : "Start was not confirmed. Stop request confirmed.",
                  false,
                  false);
            } else
              report("Stop was not confirmed. Check this camera at the recorder.", false, false);
          } catch (Exception error) {
            if (marked) {
              boolean stopped =
                  ManualAlarmRun.stop((camera, start) -> api.manualAlarm(camera, start), channel);
              if (stopped) AlarmSafety.clear(context, host, channel);
            }
            report(
                "Recorder alarm request failed. Check its status before retrying.", false, false);
          } finally {
            running = null;
            busy = false;
          }
        });
  }

  void cancel() {
    AtomicBoolean active = running;
    if (active != null) {
      active.set(true);
      report("Stopping manual alarm…", false, true);
    }
  }

  void silence() {
    if (running != null) {
      cancel();
      return;
    }
    if (!allowed()
        || busy
        || ((capabilities == null || !capabilities.supported())
            && !AlarmSafety.pending(context, host, channel))) return;
    busy = true;
    report("Sending manual alarm stop…", false, true);
    worker.execute(
        () -> {
          try {
            api();
            if (!allowed()) return;
            boolean stopped =
                ManualAlarmRun.stop((camera, start) -> api.manualAlarm(camera, start), channel);
            if (stopped) {
              AlarmSafety.clear(context, host, channel);
              report("Manual alarm stop confirmed.", false, false);
            } else {
              AlarmSafety.mark(context, host, channel);
              report("Stop was not confirmed. Check the recorder.", false, false);
            }
          } catch (Exception error) {
            report("Could not confirm manual alarm stop. Check the recorder.", false, false);
          } finally {
            busy = false;
          }
        });
  }

  @Override
  public void close() {
    if (closed) return;
    cancel();
    closed = true;
    worker.execute(
        () -> {
          if (api != null) api.close();
        });
    worker.shutdown();
  }
}
