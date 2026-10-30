package com.fantacode.vigitv;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.*;
import org.junit.Test;

public class AlarmTest {
  @Test
  public void exactChannelAndCommandShapes() throws Exception {
    for (int channel : new int[] {1, 3, 32}) {
      JSONObject caps = AlarmProtocol.capabilities(channel);
      assertEquals("do", caps.getString("method"));
      assertEquals(2, caps.length());
      Object id = caps.getJSONObject("chm").getJSONObject("chm_get_msg_alarm_list").get("chn_id");
      assertTrue(id instanceof String);
      assertEquals("" + (channel - 1), id);
      for (boolean start : new boolean[] {true, false}) {
        JSONObject request = AlarmProtocol.command(channel, start);
        assertEquals(2, request.length());
        assertFalse(request.has("ptz"));
        assertFalse(request.has("forward"));
        JSONObject alarm = request.getJSONObject("chm").getJSONObject("manual_msg_alarm");
        assertEquals(2, alarm.length());
        assertTrue(alarm.get("chn_id") instanceof Integer);
        assertEquals(channel - 1, alarm.getInt("chn_id"));
        assertEquals(start ? "start" : "stop", alarm.getString("action"));
      }
    }
    for (int invalid : new int[] {0, -1, 97})
      try {
        AlarmProtocol.command(invalid, true);
        fail();
      } catch (IllegalArgumentException expected) {
      }
  }

  @Test
  public void strictCapabilities() throws Exception {
    assertEquals(
        "Siren and light",
        AlarmProtocol.parse(new JSONObject("{msg_alarm_list:{sound:1,light:1}}")).label());
    assertTrue(
        AlarmProtocol.parse(new JSONObject("{chm:{msg_alarm_list:{sound:true,light:0}}}")).sound);
    assertFalse(
        AlarmProtocol.parse(new JSONObject("{data:{msg_alarm_list:{sound:0,light:false}}}"))
            .supported());
    for (String invalid :
        new String[] {
          "{}",
          "{msg_alarm_list:{}}",
          "{msg_alarm_list:{sound:2}}",
          "{msg_alarm_list:{sound:'yes'}}"
        })
      try {
        AlarmProtocol.parse(new JSONObject(invalid));
        fail();
      } catch (IOException expected) {
      }
  }

  static class Run {
    List<String> calls = new ArrayList<>();
    long now;
    AtomicBoolean cancel = new AtomicBoolean();
    boolean authorized = true, failStart, failStop, cancelOnPause, revokeOnPause, interrupt;
    int failedStops;

    ManualAlarmRun.Result execute() {
      return ManualAlarmRun.execute(
          (channel, start) -> {
            calls.add(channel + ":" + start);
            if (start && failStart || !start && failStop) throw new IOException("simulated");
            if (!start && failedStops-- > 0) throw new IOException("retry");
          },
          3,
          10000,
          cancel,
          () -> authorized,
          () -> now,
          ms -> {
            now += ms;
            if (cancelOnPause) cancel.set(true);
            if (revokeOnPause) authorized = false;
            if (interrupt) throw new InterruptedException();
          },
          seconds -> assertTrue(seconds >= 1 && seconds <= 10));
    }
  }

  @Test
  public void boundedStartStopsCapturedChannel() {
    Run r = new Run();
    ManualAlarmRun.Result result = r.execute();
    assertTrue(result.started && result.stopped);
    assertEquals(10000, r.now);
    assertEquals(Arrays.asList("3:true", "3:false"), r.calls);
  }

  @Test
  public void cancelledOrLockedBeforeStartDoesNothing() {
    Run r = new Run();
    r.cancel.set(true);
    assertFalse(r.execute().attempted);
    assertTrue(r.calls.isEmpty());
    r = new Run();
    r.authorized = false;
    assertFalse(r.execute().attempted);
    assertTrue(r.calls.isEmpty());
  }

  @Test
  public void cancellationOrLostAuthorizationStillStops() {
    for (boolean revoke : new boolean[] {false, true}) {
      Run r = new Run();
      r.revokeOnPause = revoke;
      r.cancelOnPause = !revoke;
      assertTrue(r.execute().stopped);
      assertEquals(Arrays.asList("3:true", "3:false"), r.calls);
      assertEquals(100, r.now);
    }
  }

  @Test
  public void uncertainStartNeverRetriesButStillStops() {
    Run r = new Run();
    r.failStart = true;
    ManualAlarmRun.Result result = r.execute();
    assertTrue(result.attempted && !result.started && result.stopped);
    assertEquals(Arrays.asList("3:true", "3:false"), r.calls);
  }

  @Test
  public void stopRetriesAreBoundedAndReported() {
    Run r = new Run();
    r.failedStops = 2;
    assertTrue(r.execute().stopped);
    assertEquals(4, r.calls.size());
    r = new Run();
    r.failStop = true;
    assertFalse(r.execute().stopped);
    assertEquals(4, r.calls.size());
  }

  @Test
  public void interruptionCleansUpThenRestoresFlag() {
    Run r = new Run();
    r.interrupt = true;
    try {
      assertTrue(r.execute().stopped);
      assertTrue(Thread.currentThread().isInterrupted());
      assertEquals(Arrays.asList("3:true", "3:false"), r.calls);
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  public void slowAcknowledgmentDoesNotExtendAlarmWindow() {
    final long[] now = {0};
    final long[] stoppedAt = {-1};
    ManualAlarmRun.Result result =
        ManualAlarmRun.execute(
            (channel, start) -> {
              if (start) now[0] = 9000;
              else stoppedAt[0] = now[0];
            },
            1,
            10000,
            new AtomicBoolean(),
            () -> true,
            () -> now[0],
            ms -> now[0] += ms,
            seconds -> {});
    assertTrue(result.stopped);
    assertEquals(10000, stoppedAt[0]);
  }

  @Test
  public void rejectsUnboundedDuration() {
    for (long duration : new long[] {0, -1, 10001})
      try {
        ManualAlarmRun.execute(
            (c, s) -> fail(),
            1,
            duration,
            new AtomicBoolean(),
            () -> true,
            () -> 0,
            ms -> {},
            seconds -> {});
        fail();
      } catch (IllegalArgumentException expected) {
      }
  }
}
